"""
train_heatmap_model.py
──────────────────────
Trains a lightweight Spatial Heatmap Architecture (RailHeatmapNet) based on MobileNetV3-Small
with a Feature Pyramid Network (FPN) decoder.
Outputs 2-channel spatial heatmaps (Left Rail & Right Rail) of size 256x144.
"""
import sys
if hasattr(sys.stdout, 'reconfigure'):
    sys.stdout.reconfigure(encoding='utf-8')
if hasattr(sys.stderr, 'reconfigure'):
    sys.stderr.reconfigure(encoding='utf-8')

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

# ── Configuration ─────────────────────────────────────────────────────────
BASE_DIR = r"C:\RailGuardAI"
DATASET_DIR = os.path.join(BASE_DIR, "prepared_dataset_heatmap")
IMAGE_DIR = os.path.join(BASE_DIR, "training_images")
OUTPUT_DIR = os.path.join(BASE_DIR, "model_v5_heatmap")
os.makedirs(OUTPUT_DIR, exist_ok=True)

INPUT_W = 256
INPUT_H = 144
BATCH_SIZE = 16
NUM_EPOCHS = 35
LR = 5e-4
WEIGHT_DECAY = 1e-4

MEAN = np.array([0.485, 0.456, 0.406], dtype=np.float32)
STD  = np.array([0.229, 0.224, 0.225], dtype=np.float32)

device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
print(f"Using device: {device}")

# ── Dynamic Heatmap Dataset ───────────────────────────────────────────────
def render_heatmap_channel(polylines, w=INPUT_W, h=INPUT_H, line_thickness=2, sigma=1.5):
    mask = np.zeros((h, w), dtype=np.float32)
    for poly in polylines:
        pts = np.array([[int(p[0] * w), int(p[1] * h)] for p in poly], dtype=np.int32)
        if len(pts) >= 2:
            cv2.polylines(mask, [pts], isClosed=False, color=1.0, thickness=line_thickness, lineType=cv2.LINE_AA)
    # Apply Gaussian smoothing for soft heatmap target
    if sigma > 0:
        ksize = int(math.ceil(sigma * 3)) * 2 + 1
        mask = cv2.GaussianBlur(mask, (ksize, ksize), sigma)
        max_v = mask.max()
        if max_v > 0:
            mask = mask / max_v
    return mask

class RailHeatmapDataset(Dataset):
    def __init__(self, split="train", augment=False):
        self.augment = augment
        meta_path = os.path.join(DATASET_DIR, f"{split}_metadata.json")
        with open(meta_path, "r", encoding="utf-8") as f:
            self.metadata = json.load(f)

        self.samples = []
        for m in self.metadata:
            path = os.path.join(IMAGE_DIR, m["file_name"])
            if os.path.exists(path):
                self.samples.append(m)
        print(f"[{split}] Loaded {len(self.samples)} valid samples.")

    def __len__(self):
        return len(self.samples)

    def __getitem__(self, idx):
        item = self.samples[idx]
        img_path = os.path.join(IMAGE_DIR, item["file_name"])
        
        # Load image
        bgr = cv2.imread(img_path)
        if bgr is None:
            bgr = np.zeros((INPUT_H, INPUT_W, 3), dtype=np.uint8)
        else:
            bgr = cv2.resize(bgr, (INPUT_W, INPUT_H), interpolation=cv2.INTER_LINEAR)

        l_polys = [p.copy() for p in item["left_polylines"]]
        r_polys = [p.copy() for p in item["right_polylines"]]

        # Data augmentation
        if self.augment:
            # 1. Random Horizontal Flip (swap left and right rail channels!)
            if random.random() < 0.5:
                bgr = cv2.flip(bgr, 1)
                # Flip X coordinates
                new_l = [[[1.0 - pt[0], pt[1]] for pt in poly] for poly in r_polys]
                new_r = [[[1.0 - pt[0], pt[1]] for pt in poly] for poly in l_polys]
                l_polys, r_polys = new_l, new_r

            # 2. Random Brightness / Contrast
            if random.random() < 0.5:
                alpha = random.uniform(0.8, 1.2)
                beta = random.uniform(-15, 15)
                bgr = np.clip(bgr.astype(np.float32) * alpha + beta, 0, 255).astype(np.uint8)

        # Render 2-channel heatmap
        hm_l = render_heatmap_channel(l_polys, INPUT_W, INPUT_H)
        hm_r = render_heatmap_channel(r_polys, INPUT_W, INPUT_H)
        heatmap = np.stack([hm_l, hm_r], axis=0) # (2, H, W)

        # Normalize RGB
        rgb = cv2.cvtColor(bgr, cv2.COLOR_BGR2RGB).astype(np.float32) / 255.0
        rgb = (rgb - MEAN) / STD
        chw = rgb.transpose(2, 0, 1) # (3, H, W)

        return torch.from_numpy(chw), torch.from_numpy(heatmap)

# ── Model Architecture: RailHeatmapNet ────────────────────────────────────
class ConvBlock(nn.Module):
    def __init__(self, in_c, out_c):
        super().__init__()
        self.conv = nn.Sequential(
            nn.Conv2d(in_c, out_c, 3, padding=1, bias=False),
            nn.BatchNorm2d(out_c),
            nn.ReLU(inplace=True),
        )
    def forward(self, x):
        return self.conv(x)

class RailHeatmapNet(nn.Module):
    def __init__(self):
        super().__init__()
        backbone = models.mobilenet_v3_small(weights=models.MobileNet_V3_Small_Weights.IMAGENET1K_V1)
        feats = backbone.features
        
        # Backbone stages for MobileNetV3-Small:
        # C1: 1/2 scale, 16 ch (feats[0])
        # C2: 1/4 scale, 16 ch (feats[1])
        # C3: 1/8 scale, 24 ch (feats[2:4])
        # C4: 1/16 scale, 48 ch (feats[4:9])
        # C5: 1/32 scale, 576 ch (feats[9:13])
        self.stage0 = feats[0]      # 1/2 (16)
        self.stage1 = feats[1]      # 1/4 (16)
        self.stage2 = feats[2:4]    # 1/8 (24)
        self.stage3 = feats[4:9]    # 1/16 (48)
        self.stage4 = feats[9:13]   # 1/32 (576)

        # Lateral 1x1 convolutions (FPN)
        self.lat4 = nn.Conv2d(576, 32, 1)
        self.lat3 = nn.Conv2d(48, 32, 1)
        self.lat2 = nn.Conv2d(24, 32, 1)
        self.lat1 = nn.Conv2d(16, 32, 1)

        # Smooth layers
        self.smooth3 = ConvBlock(32, 32)
        self.smooth2 = ConvBlock(32, 32)
        self.smooth1 = ConvBlock(32, 32)

        # Final upsample from 1/4 to 1/1 full resolution
        self.head = nn.Sequential(
            ConvBlock(32, 24),
            nn.Upsample(scale_factor=2, mode="bilinear", align_corners=False), # 1/2 (72x128)
            ConvBlock(24, 16),
            nn.Upsample(scale_factor=2, mode="bilinear", align_corners=False), # 1/1 (144x256)
            ConvBlock(16, 16),
            nn.Conv2d(16, 2, 3, padding=1),
            nn.Sigmoid() # Soft probability heatmaps in [0, 1]
        )

    def forward(self, x):
        # Encoder
        x = self.stage0(x)   # 1/2 (72x128)
        c1 = self.stage1(x)  # 1/4 (36x64)
        c2 = self.stage2(c1) # 1/8 (18x32)
        c3 = self.stage3(c2) # 1/16 (9x16)
        c4 = self.stage4(c3) # 1/32 (5x8)

        # Decoder (Top-Down FPN with exact spatial matching)
        p4 = self.lat4(c4)
        p3 = self.lat3(c3) + F.interpolate(p4, size=c3.shape[2:], mode="bilinear", align_corners=False)
        p3 = self.smooth3(p3)

        p2 = self.lat2(c2) + F.interpolate(p3, size=c2.shape[2:], mode="bilinear", align_corners=False)
        p2 = self.smooth2(p2)

        p1 = self.lat1(c1) + F.interpolate(p2, size=c1.shape[2:], mode="bilinear", align_corners=False)
        p1 = self.smooth1(p1)

        # Full resolution output (144x256)
        out = self.head(p1)
        return out

# ── Loss Function: Soft Dice + Focal BCE Loss ─────────────────────────────
class HeatmapLoss(nn.Module):
    def __init__(self, dice_weight=1.0, bce_weight=1.0):
        super().__init__()
        self.dice_w = dice_weight
        self.bce_w = bce_weight

    def forward(self, pred, target):
        eps = 1e-6
        # 1. Soft Dice Loss
        intersection = (pred * target).sum(dim=(2, 3))
        union = (pred ** 2).sum(dim=(2, 3)) + (target ** 2).sum(dim=(2, 3))
        dice = (2.0 * intersection + eps) / (union + eps)
        dice_loss = 1.0 - dice.mean()

        # 2. Weighted BCE Loss (heavy penalty on false negatives for thin rail lines)
        bce_loss = F.binary_cross_entropy(pred, target, reduction="mean")

        return self.dice_w * dice_loss + self.bce_w * bce_loss

# ── Training Loop ─────────────────────────────────────────────────────────
def train():
    print("=" * 70)
    print("RAILGUARD AI - SPATIAL HEATMAP ARCHITECTURE TRAINING (V5)")
    print("=" * 70)

    train_ds = RailHeatmapDataset("train", augment=True)
    val_ds   = RailHeatmapDataset("val", augment=False)

    train_loader = DataLoader(train_ds, batch_size=BATCH_SIZE, shuffle=True, num_workers=0)
    val_loader   = DataLoader(val_ds, batch_size=BATCH_SIZE, shuffle=False, num_workers=0)

    model = RailHeatmapNet().to(device)
    total_params = sum(p.numel() for p in model.parameters())
    print(f"Total Parameters: {total_params:,}")

    criterion = HeatmapLoss(dice_weight=1.0, bce_weight=1.0)
    optimizer = optim.AdamW(model.parameters(), lr=LR, weight_decay=WEIGHT_DECAY)
    scheduler = optim.lr_scheduler.CosineAnnealingLR(optimizer, T_max=NUM_EPOCHS, eta_min=1e-5)

    best_val_loss = float("inf")
    history = {"train_loss": [], "val_loss": []}

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

        history["train_loss"].append(t_loss)
        history["val_loss"].append(v_loss)

        improved = v_loss < best_val_loss
        if improved:
            best_val_loss = v_loss
            torch.save(model.state_dict(), os.path.join(OUTPUT_DIR, "best_model.pth"))

        marker = " <best>" if improved else ""
        print(f"Epoch [{epoch:2d}/{NUM_EPOCHS:2d}] ({elapsed:4.1f}s)  train={t_loss:.5f}  val={v_loss:.5f}{marker}")

    # Export to ONNX
    print("\nExporting RailHeatmapNet to ONNX...")
    model.load_state_dict(torch.load(os.path.join(OUTPUT_DIR, "best_model.pth"), map_location=device))
    model.eval()
    dummy = torch.randn(1, 3, INPUT_H, INPUT_W).to(device)
    onnx_path = os.path.join(OUTPUT_DIR, "rail_model.onnx")

    torch.onnx.export(
        model, dummy, onnx_path,
        input_names=["image"], output_names=["heatmap"],
        dynamic_axes={"image": {0: "batch"}, "heatmap": {0: "batch"}},
        opset_version=17,
        dynamo=False
    )
    print(f"ONNX Model saved -> {onnx_path}")

    with open(os.path.join(OUTPUT_DIR, "training_history.json"), "w", encoding="utf-8") as f:
        json.dump(history, f, indent=2)
    print("Training and export complete.")

if __name__ == "__main__":
    train()
