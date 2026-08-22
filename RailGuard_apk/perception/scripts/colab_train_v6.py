# ============================================================
#  RailGuard AI - V6 High-Precision Colab Training Notebook
#  Resolution: 512x288 | Backbone: EfficientNet-B3 + FPN
#  New: Horizon-Aware Loss, Sky-Suppression Mask, Better Augmentation
# ============================================================

# CELL 1: Mount Google Drive & Setup
from google.colab import drive
drive.mount('/content/drive')

import os
# Upload railguard_colab_package.zip to your Google Drive and set path:
DRIVE_ZIP = "/content/drive/MyDrive/railguard_colab_package.zip"
WORK_DIR = "/content/railguard"
os.makedirs(WORK_DIR, exist_ok=True)

# Install dependencies
import subprocess
subprocess.run(["pip", "install", "-q", "onnxruntime", "timm"], check=True)

# Unzip dataset
os.makedirs(f"{WORK_DIR}/dataset", exist_ok=True)
subprocess.run(["unzip", "-q", "-o", DRIVE_ZIP, "-d", f"{WORK_DIR}/dataset"], check=True)
print("Dataset ready!")

# ─────────────────────────────────────────────────────────────
# CELL 2: Imports & Config
# ─────────────────────────────────────────────────────────────

import json, math, time, random
import numpy as np
import cv2
import torch
import torch.nn as nn
import torch.nn.functional as F
import torch.optim as optim
from torch.utils.data import Dataset, DataLoader
import timm  # EfficientNet backbone

INPUT_W   = 512
INPUT_H   = 288
BATCH_SIZE = 16
NUM_EPOCHS = 100
LR         = 5e-4
WEIGHT_DECAY = 2e-4

# Ground horizon row (Y > this is road/rail; Y < this is sky/ceiling)
# Rows above this line should have near-zero activation
HORIZON_ROW = int(INPUT_H * 0.35)  # row 100 out of 288

MEAN = np.array([0.485, 0.456, 0.406], dtype=np.float32)
STD  = np.array([0.229, 0.224, 0.225], dtype=np.float32)

device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
print(f"Device: {device}")

# ─────────────────────────────────────────────────────────────
# CELL 3: Dataset with Rich Augmentation
# ─────────────────────────────────────────────────────────────

def render_heatmap_channel(polylines, w=INPUT_W, h=INPUT_H, sigma=2.5):
    """Render soft gaussian rail heatmap with sub-pixel quality."""
    mask = np.zeros((h, w), dtype=np.float32)
    for poly in polylines:
        pts = np.array([[int(p[0] * w), int(p[1] * h)] for p in poly], dtype=np.int32)
        if len(pts) >= 2:
            cv2.polylines(mask, [pts], isClosed=False, color=1.0, thickness=3, lineType=cv2.LINE_AA)
    if sigma > 0:
        ksize = int(math.ceil(sigma * 3)) * 2 + 1
        mask = cv2.GaussianBlur(mask, (ksize, ksize), sigma)
        max_v = mask.max()
        if max_v > 0:
            mask = mask / max_v
    return mask

class RailDatasetV6(Dataset):
    def __init__(self, data_root, split="train", augment=False):
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
        print(f"[{split}] {len(self.samples)} samples")

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
            # 1. Horizontal Flip
            if random.random() < 0.5:
                bgr = cv2.flip(bgr, 1)
                new_l = [[[1.0 - pt[0], pt[1]] for pt in poly] for poly in r_polys]
                new_r = [[[1.0 - pt[0], pt[1]] for pt in poly] for poly in l_polys]
                l_polys, r_polys = new_l, new_r

            # 2. Color Jitter (Brightness + Contrast + Saturation)
            if random.random() < 0.7:
                hsv = cv2.cvtColor(bgr, cv2.COLOR_BGR2HSV).astype(np.float32)
                hsv[:, :, 1] *= random.uniform(0.7, 1.3)   # Saturation
                hsv[:, :, 2] *= random.uniform(0.65, 1.35)  # Brightness
                hsv = np.clip(hsv, 0, 255)
                bgr = cv2.cvtColor(hsv.astype(np.uint8), cv2.COLOR_HSV2BGR)

            # 3. Random Gamma (simulate tunnel darkness / bright outdoor)
            if random.random() < 0.5:
                gamma = random.uniform(0.5, 1.8)
                lut = np.array([((i / 255.0) ** (1.0 / gamma)) * 255 for i in range(256)], dtype=np.uint8)
                bgr = cv2.LUT(bgr, lut)

            # 4. Gaussian Blur (motion blur simulation)
            if random.random() < 0.25:
                k = random.choice([3, 5])
                bgr = cv2.GaussianBlur(bgr, (k, k), 0)

            # 5. Random shadow stripe (horizontal dark band)
            if random.random() < 0.2:
                y1 = random.randint(INPUT_H // 2, INPUT_H - 30)
                y2 = min(INPUT_H, y1 + random.randint(10, 40))
                bgr[y1:y2, :] = (bgr[y1:y2, :] * 0.4).astype(np.uint8)

            # 6. Small horizontal shift (camera sway)
            if random.random() < 0.3:
                shift = random.randint(-15, 15)
                M = np.float32([[1, 0, shift], [0, 1, 0]])
                bgr = cv2.warpAffine(bgr, M, (INPUT_W, INPUT_H))
                shift_norm = shift / float(INPUT_W)
                l_polys = [[[max(0, min(1, pt[0] + shift_norm)), pt[1]] for pt in poly] for poly in l_polys]
                r_polys = [[[max(0, min(1, pt[0] + shift_norm)), pt[1]] for pt in poly] for poly in r_polys]

        hm_l = render_heatmap_channel(l_polys, INPUT_W, INPUT_H)
        hm_r = render_heatmap_channel(r_polys, INPUT_W, INPUT_H)

        # Sky suppression: ensure GT is zero above horizon
        hm_l[:HORIZON_ROW, :] = 0.0
        hm_r[:HORIZON_ROW, :] = 0.0

        heatmap = np.stack([hm_l, hm_r], axis=0)
        rgb = cv2.cvtColor(bgr, cv2.COLOR_BGR2RGB).astype(np.float32) / 255.0
        rgb = (rgb - MEAN) / STD
        chw = rgb.transpose(2, 0, 1)
        return torch.from_numpy(chw), torch.from_numpy(heatmap)

# ─────────────────────────────────────────────────────────────
# CELL 4: EfficientNet-B3 + FPN Model (Higher Capacity than V5)
# ─────────────────────────────────────────────────────────────

class ConvBNReLU(nn.Module):
    def __init__(self, in_c, out_c, k=3, s=1, p=1):
        super().__init__()
        self.block = nn.Sequential(
            nn.Conv2d(in_c, out_c, k, stride=s, padding=p, bias=False),
            nn.BatchNorm2d(out_c),
            nn.ReLU(inplace=True)
        )
    def forward(self, x): return self.block(x)

class FPNHead(nn.Module):
    def __init__(self, ch=96):
        super().__init__()
        # EfficientNet-B3 skip channels: [24, 32, 48, 136, 1536]
        self.lat5 = nn.Conv2d(1536, ch, 1)
        self.lat4 = nn.Conv2d(136, ch, 1)
        self.lat3 = nn.Conv2d(48, ch, 1)
        self.lat2 = nn.Conv2d(32, ch, 1)
        self.lat1 = nn.Conv2d(24, ch, 1)

        self.smooth4 = nn.Sequential(ConvBNReLU(ch, ch), ConvBNReLU(ch, ch))
        self.smooth3 = nn.Sequential(ConvBNReLU(ch, ch), ConvBNReLU(ch, ch))
        self.smooth2 = nn.Sequential(ConvBNReLU(ch, ch), ConvBNReLU(ch, ch))
        self.smooth1 = nn.Sequential(ConvBNReLU(ch, ch), ConvBNReLU(ch, ch))

        self.output_head = nn.Sequential(
            ConvBNReLU(ch, 64),
            nn.Upsample(scale_factor=2, mode="bilinear", align_corners=False),
            ConvBNReLU(64, 32),
            nn.Upsample(scale_factor=2, mode="bilinear", align_corners=False),
            ConvBNReLU(32, 16),
            nn.Conv2d(16, 2, 1),
            nn.Sigmoid()
        )

    def forward(self, skips):
        c1, c2, c3, c4, c5 = skips
        p5 = self.lat5(c5)
        p4 = self.lat4(c4) + F.interpolate(p5, size=c4.shape[2:], mode="bilinear", align_corners=False)
        p4 = self.smooth4(p4)
        p3 = self.lat3(c3) + F.interpolate(p4, size=c3.shape[2:], mode="bilinear", align_corners=False)
        p3 = self.smooth3(p3)
        p2 = self.lat2(c2) + F.interpolate(p3, size=c2.shape[2:], mode="bilinear", align_corners=False)
        p2 = self.smooth2(p2)
        p1 = self.lat1(c1) + F.interpolate(p2, size=c1.shape[2:], mode="bilinear", align_corners=False)
        p1 = self.smooth1(p1)
        return self.output_head(p1)

class RailHeatmapNetV6(nn.Module):
    """
    EfficientNet-B3 + 5-level FPN.
    ~12M parameters vs ~1M in V5 - much better at fine distant curves.
    """
    def __init__(self):
        super().__init__()
        self.backbone = timm.create_model("efficientnet_b3", pretrained=True, features_only=True)
        self.fpn = FPNHead(ch=96)

    def forward(self, x):
        feats = self.backbone(x)  # [c1, c2, c3, c4, c5]
        return self.fpn(feats)

# ─────────────────────────────────────────────────────────────
# CELL 5: Horizon-Aware Loss (Penalizes Sky Activations)
# ─────────────────────────────────────────────────────────────

class HorizonAwareLoss(nn.Module):
    """
    Combined Dice + Focal BCE + Sky Suppression Penalty.
    The sky penalty term strongly discourages the network from
    predicting any rail activation above the horizon line.
    This is the key fix for the 'lines going up into the air' issue.
    """
    def __init__(self, dice_w=1.5, bce_w=1.0, sky_w=3.0, horizon_row=HORIZON_ROW):
        super().__init__()
        self.dice_w = dice_w
        self.bce_w = bce_w
        self.sky_w = sky_w
        self.horizon_row = horizon_row

    def forward(self, pred, target):
        eps = 1e-6

        # 1. Soft Dice Loss
        intersection = (pred * target).sum(dim=(2, 3))
        union = (pred ** 2).sum(dim=(2, 3)) + (target ** 2).sum(dim=(2, 3))
        dice = (2.0 * intersection + eps) / (union + eps)
        dice_loss = 1.0 - dice.mean()

        # 2. Focal BCE
        alpha, gamma = 0.75, 2.0
        bce = F.binary_cross_entropy(pred, target, reduction="none")
        pt = torch.where(target >= 0.5, pred, 1 - pred)
        focal_weight = alpha * (1 - pt) ** gamma
        bce_loss = (focal_weight * bce).mean()

        # 3. Sky Suppression Penalty (strong zero-enforcement above horizon)
        sky_pred = pred[:, :, :self.horizon_row, :]  # (B, 2, H_sky, W)
        sky_loss = (sky_pred ** 2).mean()

        return self.dice_w * dice_loss + self.bce_w * bce_loss + self.sky_w * sky_loss

# ─────────────────────────────────────────────────────────────
# CELL 6: Training Loop
# ─────────────────────────────────────────────────────────────

def train():
    DATA_ROOT  = f"{WORK_DIR}/dataset"
    OUTPUT_DIR = f"{WORK_DIR}/output_v6"
    os.makedirs(OUTPUT_DIR, exist_ok=True)

    train_ds = RailDatasetV6(DATA_ROOT, "train", augment=True)
    val_ds   = RailDatasetV6(DATA_ROOT, "val", augment=False)
    train_dl = DataLoader(train_ds, batch_size=BATCH_SIZE, shuffle=True,
                          num_workers=4, pin_memory=True, persistent_workers=True)
    val_dl   = DataLoader(val_ds, batch_size=BATCH_SIZE, shuffle=False,
                          num_workers=4, pin_memory=True, persistent_workers=True)

    model = RailHeatmapNetV6().to(device)
    params = sum(p.numel() for p in model.parameters())
    print(f"Model Parameters: {params:,}")

    criterion = HorizonAwareLoss(dice_w=1.5, bce_w=1.0, sky_w=3.0)
    optimizer = optim.AdamW(model.parameters(), lr=LR, weight_decay=WEIGHT_DECAY)

    # Warmup 5 epochs then Cosine Anneal
    def lr_lambda(ep):
        if ep < 5:
            return (ep + 1) / 5.0
        return 0.5 * (1 + math.cos(math.pi * (ep - 5) / (NUM_EPOCHS - 5)))
    scheduler = optim.lr_scheduler.LambdaLR(optimizer, lr_lambda)

    best_val = float("inf")

    for epoch in range(1, NUM_EPOCHS + 1):
        t0 = time.time()
        model.train()
        tl = 0.0
        for imgs, hms in train_dl:
            imgs, hms = imgs.to(device), hms.to(device)
            optimizer.zero_grad()
            pred = model(imgs)
            loss = criterion(pred, hms)
            loss.backward()
            torch.nn.utils.clip_grad_norm_(model.parameters(), 2.0)
            optimizer.step()
            tl += loss.item() * len(imgs)
        tl /= len(train_ds)

        model.eval()
        vl = 0.0
        with torch.no_grad():
            for imgs, hms in val_dl:
                imgs, hms = imgs.to(device), hms.to(device)
                pred = model(imgs)
                loss = criterion(pred, hms)
                vl += loss.item() * len(imgs)
        vl /= len(val_ds)
        scheduler.step()

        improved = vl < best_val
        if improved:
            best_val = vl
            torch.save(model.state_dict(), f"{OUTPUT_DIR}/best_model.pth")

        tag = " <best>" if improved else ""
        print(f"Epoch [{epoch:3d}/{NUM_EPOCHS}] ({time.time()-t0:.0f}s) train={tl:.5f} val={vl:.5f} lr={optimizer.param_groups[0]['lr']:.2e}{tag}")

    # ── Export best model to ONNX ──
    print("\nExporting to ONNX...")
    model.load_state_dict(torch.load(f"{OUTPUT_DIR}/best_model.pth", map_location=device))
    model.eval()
    dummy = torch.randn(1, 3, INPUT_H, INPUT_W).to(device)
    onnx_path = f"{OUTPUT_DIR}/rail_heatmap_v6_512x288.onnx"
    torch.onnx.export(
        model, dummy, onnx_path,
        input_names=["image"], output_names=["heatmap"],
        dynamic_axes={"image": {0: "batch"}, "heatmap": {0: "batch"}},
        opset_version=17
    )
    print(f"ONNX saved -> {onnx_path}")

    # Copy to Google Drive for download
    drive_out = "/content/drive/MyDrive/rail_heatmap_v6_512x288.onnx"
    import shutil
    shutil.copy2(onnx_path, drive_out)
    print(f"Copied to Google Drive -> {drive_out}")
    print("DONE! Download the .onnx file from your Google Drive.")

train()
