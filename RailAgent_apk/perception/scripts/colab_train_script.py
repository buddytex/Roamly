"""
colab_train_script.py
─────────────────────
High-Accuracy Spatial Rail Heatmap Network (RailHeatmapNet-Large)
Resolution: 512x288 (2x resolution for sub-pixel physical track locking)
Backbone: MobileNetV3-Large + FPN Spatial Decoder
Loss: Soft Dice Loss + Focal BCE Loss
"""
import os
import json
import math
import time
import random
import numpy as np
import cv2
from PIL import Image

import torch
import torch.nn as nn
import torch.nn.functional as F
import torch.optim as optim
from torch.utils.data import Dataset, DataLoader
from torchvision import models

INPUT_W = 512
INPUT_H = 288
BATCH_SIZE = 32
NUM_EPOCHS = 80
LR = 7e-4
WEIGHT_DECAY = 1e-4

MEAN = np.array([0.485, 0.456, 0.406], dtype=np.float32)
STD  = np.array([0.229, 0.224, 0.225], dtype=np.float32)

device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
print(f"Running on device: {device} (GPU Accelerated: {torch.cuda.is_available()})")

def render_heatmap_channel(polylines, w=INPUT_W, h=INPUT_H, line_thickness=3, sigma=2.0):
    mask = np.zeros((h, w), dtype=np.float32)
    for poly in polylines:
        pts = np.array([[int(p[0] * w), int(p[1] * h)] for p in poly], dtype=np.int32)
        if len(pts) >= 2:
            cv2.polylines(mask, [pts], isClosed=False, color=1.0, thickness=line_thickness, lineType=cv2.LINE_AA)
    if sigma > 0:
        ksize = int(math.ceil(sigma * 3)) * 2 + 1
        mask = cv2.GaussianBlur(mask, (ksize, ksize), sigma)
        max_v = mask.max()
        if max_v > 0:
            mask = mask / max_v
    return mask

class RailHeatmapDatasetColab(Dataset):
    def __init__(self, data_root="dataset", split="train", augment=False):
        self.data_root = data_root
        self.augment = augment
        meta_path = os.path.join(data_root, f"{split}_metadata.json")
        with open(meta_path, "r", encoding="utf-8") as f:
            self.metadata = json.load(f)

        self.samples = []
        for m in self.metadata:
            base_name = os.path.splitext(m["file_name"])[0] + ".jpg"
            path = os.path.join(data_root, "images", base_name)
            if os.path.exists(path):
                self.samples.append((path, m))
        print(f"[{split}] Loaded {len(self.samples)} valid samples.")

    def __len__(self):
        return len(self.samples)

    def __getitem__(self, idx):
        img_path, item = self.samples[idx]
        bgr = cv2.imread(img_path)
        if bgr is None:
            bgr = np.zeros((INPUT_H, INPUT_W, 3), dtype=np.uint8)
        else:
            bgr = cv2.resize(bgr, (INPUT_W, INPUT_H), interpolation=cv2.INTER_LINEAR)

        l_polys = [p.copy() for p in item["left_polylines"]]
        r_polys = [p.copy() for p in item["right_polylines"]]

        if self.augment:
            # 1. Random Horizontal Flip (swap left/right)
            if random.random() < 0.5:
                bgr = cv2.flip(bgr, 1)
                new_l = [[[1.0 - pt[0], pt[1]] for pt in poly] for poly in r_polys]
                new_r = [[[1.0 - pt[0], pt[1]] for pt in poly] for poly in l_polys]
                l_polys, r_polys = new_l, new_r

            # 2. Random Brightness / Contrast
            if random.random() < 0.5:
                alpha = random.uniform(0.75, 1.25)
                beta = random.uniform(-20, 20)
                bgr = np.clip(bgr.astype(np.float32) * alpha + beta, 0, 255).astype(np.uint8)

            # 3. Random Gaussian Blur
            if random.random() < 0.2:
                bgr = cv2.GaussianBlur(bgr, (5, 5), 0)

        hm_l = render_heatmap_channel(l_polys, INPUT_W, INPUT_H)
        hm_r = render_heatmap_channel(r_polys, INPUT_W, INPUT_H)
        heatmap = np.stack([hm_l, hm_r], axis=0) # (2, H, W)

        rgb = cv2.cvtColor(bgr, cv2.COLOR_BGR2RGB).astype(np.float32) / 255.0
        rgb = (rgb - MEAN) / STD
        chw = rgb.transpose(2, 0, 1) # (3, H, W)

        return torch.from_numpy(chw), torch.from_numpy(heatmap)

# ── High-Accuracy Model: RailHeatmapNet-Large ─────────────────────────────
class ConvBlock(nn.Module):
    def __init__(self, in_c, out_c):
        super().__init__()
        self.conv = nn.Sequential(
            nn.Conv2d(in_c, out_c, 3, padding=1, bias=False),
            nn.BatchNorm2d(out_c),
            nn.ReLU(inplace=True),
            nn.Conv2d(out_c, out_c, 3, padding=1, bias=False),
            nn.BatchNorm2d(out_c),
            nn.ReLU(inplace=True),
        )
    def forward(self, x):
        return self.conv(x)

class RailHeatmapNetLarge(nn.Module):
    def __init__(self):
        super().__init__()
        backbone = models.mobilenet_v3_large(weights=models.MobileNet_V3_Large_Weights.IMAGENET1K_V1)
        feats = backbone.features
        
        # MobileNetV3-Large stages:
        # C1: 1/2 scale (16 ch) -> feats[0]
        # C2: 1/4 scale (24 ch) -> feats[1:3]
        # C3: 1/8 scale (40 ch) -> feats[3:7]
        # C4: 1/16 scale (112 ch) -> feats[7:13]
        # C5: 1/32 scale (960 ch) -> feats[13:17]
        self.stage0 = feats[0]
        self.stage1 = feats[1:3]
        self.stage2 = feats[3:7]
        self.stage3 = feats[7:13]
        self.stage4 = feats[13:17]

        # Lateral 1x1 convolutions
        self.lat4 = nn.Conv2d(960, 48, 1)
        self.lat3 = nn.Conv2d(112, 48, 1)
        self.lat2 = nn.Conv2d(40, 48, 1)
        self.lat1 = nn.Conv2d(24, 48, 1)

        # Refinement blocks
        self.smooth3 = ConvBlock(48, 48)
        self.smooth2 = ConvBlock(48, 48)
        self.smooth1 = ConvBlock(48, 48)

        # Decoder Head up to 512x288
        self.head = nn.Sequential(
            ConvBlock(48, 32),
            nn.Upsample(scale_factor=2, mode="bilinear", align_corners=False), # 1/2
            ConvBlock(32, 24),
            nn.Upsample(scale_factor=2, mode="bilinear", align_corners=False), # 1/1
            ConvBlock(24, 16),
            nn.Conv2d(16, 2, 3, padding=1),
            nn.Sigmoid()
        )

    def forward(self, x):
        x = self.stage0(x)
        c1 = self.stage1(x)
        c2 = self.stage2(c1)
        c3 = self.stage3(c2)
        c4 = self.stage4(c3)

        p4 = self.lat4(c4)
        p3 = self.lat3(c3) + F.interpolate(p4, size=c3.shape[2:], mode="bilinear", align_corners=False)
        p3 = self.smooth3(p3)

        p2 = self.lat2(c2) + F.interpolate(p3, size=c2.shape[2:], mode="bilinear", align_corners=False)
        p2 = self.smooth2(p2)

        p1 = self.lat1(c1) + F.interpolate(p2, size=c1.shape[2:], mode="bilinear", align_corners=False)
        p1 = self.smooth1(p1)

        out = self.head(p1)
        return out

# ── Loss Function ─────────────────────────────────────────────────────────
class HeatmapLoss(nn.Module):
    def __init__(self, dice_weight=1.5, bce_weight=1.0):
        super().__init__()
        self.dice_w = dice_weight
        self.bce_w = bce_weight

    def forward(self, pred, target):
        eps = 1e-6
        intersection = (pred * target).sum(dim=(2, 3))
        union = (pred ** 2).sum(dim=(2, 3)) + (target ** 2).sum(dim=(2, 3))
        dice = (2.0 * intersection + eps) / (union + eps)
        dice_loss = 1.0 - dice.mean()

        bce_loss = F.binary_cross_entropy(pred, target, reduction="mean")
        return self.dice_w * dice_loss + self.bce_w * bce_loss

def main():
    print("=" * 70)
    print("RAILGUARD AI - HIGH ACCURACY SPATIAL HEATMAP TRAINING (512x288)")
    print("=" * 70)

    train_ds = RailHeatmapDatasetColab("dataset", split="train", augment=True)
    val_ds   = RailHeatmapDatasetColab("dataset", split="val", augment=False)

    train_loader = DataLoader(train_ds, batch_size=BATCH_SIZE, shuffle=True, num_workers=2, pin_memory=True)
    val_loader   = DataLoader(val_ds, batch_size=BATCH_SIZE, shuffle=False, num_workers=2, pin_memory=True)

    model = RailHeatmapNetLarge().to(device)
    total_params = sum(p.numel() for p in model.parameters())
    print(f"Total Model Parameters: {total_params:,}")

    criterion = HeatmapLoss(dice_weight=1.5, bce_weight=1.0)
    optimizer = optim.AdamW(model.parameters(), lr=LR, weight_decay=WEIGHT_DECAY)
    scheduler = optim.lr_scheduler.CosineAnnealingLR(optimizer, T_max=NUM_EPOCHS, eta_min=1e-5)

    best_val_loss = float("inf")
    output_dir = "output_model"
    os.makedirs(output_dir, exist_ok=True)

    for epoch in range(1, NUM_EPOCHS + 1):
        t0 = time.time()
        model.train()
        t_loss = 0.0
        for imgs, heatmaps in train_loader:
            imgs, heatmaps = imgs.to(device), heatmaps.to(device)
            optimizer.zero_grad()
            pred = model(imgs)
            loss = criterion(pred, heatmaps)
            loss.backward()
            optimizer.step()
            t_loss += loss.item() * len(imgs)
        t_loss /= len(train_ds)

        model.eval()
        v_loss = 0.0
        with torch.no_grad():
            for imgs, heatmaps in val_loader:
                imgs, heatmaps = imgs.to(device), heatmaps.to(device)
                pred = model(imgs)
                loss = criterion(pred, heatmaps)
                v_loss += loss.item() * len(imgs)
        v_loss /= len(val_ds)

        scheduler.step()
        elapsed = time.time() - t0

        improved = v_loss < best_val_loss
        if improved:
            best_val_loss = v_loss
            torch.save(model.state_dict(), os.path.join(output_dir, "best_model.pth"))

        marker = " <best>" if improved else ""
        print(f"Epoch [{epoch:3d}/{NUM_EPOCHS:3d}] ({elapsed:4.1f}s)  train={t_loss:.5f}  val={v_loss:.5f}{marker}")

    print("\nExporting Best Model to ONNX...")
    model.load_state_dict(torch.load(os.path.join(output_dir, "best_model.pth"), map_location=device))
    model.eval()
    dummy = torch.randn(1, 3, INPUT_H, INPUT_W).to(device)
    onnx_path = os.path.join(output_dir, "rail_heatmap_512x288.onnx")

    torch.onnx.export(
        model, dummy, onnx_path,
        input_names=["image"], output_names=["heatmap"],
        dynamic_axes={"image": {0: "batch"}, "heatmap": {0: "batch"}},
        opset_version=17,
        dynamo=False
    )
    print(f"ONNX Model saved successfully -> {onnx_path}")
    print("All training steps finished successfully!")

if __name__ == "__main__":
    main()
