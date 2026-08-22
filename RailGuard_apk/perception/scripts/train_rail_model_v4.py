"""
train_rail_model_v4.py
─────────────────────
Trains MobileNetV3-Small on prepared_dataset_v5 (fixed Y-grid).
KEY CHANGE: Masked MSE loss — only penalises valid points (valid=1).
Exports model_v4/rail_model.onnx on completion.
"""
import sys
# Force UTF-8 encoding for stdout/stderr to prevent UnicodeEncodeError on Windows console print
if hasattr(sys.stdout, 'reconfigure'):
    sys.stdout.reconfigure(encoding='utf-8')
if hasattr(sys.stderr, 'reconfigure'):
    sys.stderr.reconfigure(encoding='utf-8')

import json, os, math, time, shutil, random
import numpy as np
import torch
import torch.nn as nn
import torch.optim as optim
from torch.utils.data import Dataset, DataLoader
from torchvision import models
from PIL import Image, ImageEnhance
import cv2

# ── Paths ─────────────────────────────────────────────────────────────────
BASE_DIR     = r"C:\RailGuardAI"
DATASET_DIR  = os.path.join(BASE_DIR, "prepared_dataset_v5")
IMAGE_DIR    = os.path.join(BASE_DIR, "training_images")
OUTPUT_DIR   = os.path.join(BASE_DIR, "model_v4")
os.makedirs(OUTPUT_DIR, exist_ok=True)

# ── Hyper-parameters ───────────────────────────────────────────────────────
INPUT_W      = 256
INPUT_H      = 144
BATCH_SIZE   = 32
NUM_EPOCHS   = 60
LR           = 3e-4
WEIGHT_DECAY = 1e-4
NUM_WORKERS  = 0
PATIENCE     = 12   # early-stop patience

NUM_SAMPLES  = 16
TARGET_DIM   = 96   # 16*6

MEAN = np.array([0.485, 0.456, 0.406], dtype=np.float32)
STD  = np.array([0.229, 0.224, 0.225], dtype=np.float32)

device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
print(f"Device: {device}")


# ── Dataset ────────────────────────────────────────────────────────────────
class RailDataset(Dataset):
    def __init__(self, split, augment=False):
        self.augment  = augment
        self.targets  = np.load(os.path.join(DATASET_DIR, f"{split}_targets.npy"))
        with open(os.path.join(DATASET_DIR, f"{split}_metadata.json")) as f:
            self.meta = json.load(f)
        assert len(self.targets) == len(self.meta)

        # Pre-cache images in memory
        self.images = []
        skipped = 0
        for i, m in enumerate(self.meta):
            fn   = os.path.basename(m["file_name"])
            path = os.path.join(IMAGE_DIR, fn)
            if os.path.exists(path):
                try:
                    img = Image.open(path).convert("RGB").resize((INPUT_W, INPUT_H), Image.BILINEAR)
                    self.images.append(img)
                except Exception:
                    self.images.append(None); skipped += 1
            else:
                self.images.append(None); skipped += 1
        print(f"[{split}] Loaded {len(self.meta)-skipped}/{len(self.meta)} images")

        # Keep only samples with valid images
        valid_idx = [i for i, img in enumerate(self.images) if img is not None]
        self.images  = [self.images[i]  for i in valid_idx]
        self.targets = self.targets[valid_idx]
        self.meta    = [self.meta[i]    for i in valid_idx]
        print(f"[{split}] Dataset size after filter: {len(self.images)}")

    def __len__(self):
        return len(self.images)

    def _augment(self, img, target):
        """Apply random augmentation and mirror valid mask / coordinates."""
        t = target.copy()
        l_x, r_x = t[0:16], t[16:32]
        l_y, r_y = t[32:48], t[48:64]
        l_v, r_v = t[64:80], t[80:96]

        # ── Horizontal flip ───────────────────────────────────────────────
        if random.random() < 0.5:
            img   = img.transpose(Image.FLIP_LEFT_RIGHT)
            l_x   = 1.0 - r_x.copy()
            r_x   = 1.0 - t[0:16]
            l_v, r_v = r_v.copy(), l_v.copy()

        # ── Brightness / contrast jitter ──────────────────────────────────
        if random.random() < 0.5:
            img = ImageEnhance.Brightness(img).enhance(random.uniform(0.7, 1.3))
        if random.random() < 0.4:
            img = ImageEnhance.Contrast(img).enhance(random.uniform(0.8, 1.2))

        # ── Small random rotation (±6°) ───────────────────────────────────
        if random.random() < 0.4:
            angle = random.uniform(-6, 6)
            img   = img.rotate(angle, resample=Image.BILINEAR)
            rad   = math.radians(angle)
            cos_a, sin_a = math.cos(rad), math.sin(rad)
            cx, cy = 0.5, 0.5
            def rot_x(xs, ys):
                dx = xs - cx; dy = ys - cy
                return np.clip(cx + cos_a * dx - sin_a * dy, 0.0, 1.0)
            l_x = rot_x(l_x, l_y)
            r_x = rot_x(r_x, r_y)

        t = np.concatenate([l_x, r_x, l_y, r_y, l_v, r_v]).astype(np.float32)
        return img, t

    def __getitem__(self, idx):
        img    = self.images[idx].copy()
        target = self.targets[idx].copy()
        if self.augment:
            img, target = self._augment(img, target)
        arr  = np.array(img, dtype=np.float32) / 255.0
        arr  = (arr - MEAN) / STD
        tens = torch.from_numpy(arr.transpose(2, 0, 1))
        return tens, torch.from_numpy(target)


# ── Model ─────────────────────────────────────────────────────────────────
class RailModel(nn.Module):
    def __init__(self):
        super().__init__()
        backbone = models.mobilenet_v3_small(weights=models.MobileNet_V3_Small_Weights.IMAGENET1K_V1)
        self.features = backbone.features
        self.pool     = backbone.avgpool
        self.head     = nn.Sequential(
            nn.Linear(576, 256), nn.Hardswish(), nn.Dropout(0.2),
            nn.Linear(256, 128), nn.Hardswish(),
            nn.Linear(128, TARGET_DIM),
            nn.Sigmoid()          # outputs in [0,1]
        )

    def forward(self, x):
        x = self.features(x)
        x = self.pool(x)
        x = x.flatten(1)
        return self.head(x)


# ── Masked MSE Loss ────────────────────────────────────────────────────────
class MaskedMSELoss(nn.Module):
    """Only penalise coordinate predictions where valid mask == 1."""
    def forward(self, pred, target):
        # pred / target: (B, 96)
        # valid masks are in [64:80] and [80:96]
        l_valid = target[:, 64:80]   # (B, 16)
        r_valid = target[:, 80:96]   # (B, 16)

        # X coordinates
        l_x_pred, r_x_pred = pred[:, 0:16],  pred[:, 16:32]
        l_x_gt,   r_x_gt   = target[:, 0:16], target[:, 16:32]

        # Y coords are fixed (same for all images) — no need to predict Y;
        # we only predict X.  But model output still has 96 dims for clean ONNX.
        # Loss only on X (masked)
        l_loss = ((l_x_pred - l_x_gt) ** 2 * l_valid).sum() / (l_valid.sum() + 1e-6)
        r_loss = ((r_x_pred - r_x_gt) ** 2 * r_valid).sum() / (r_valid.sum() + 1e-6)
        return l_loss + r_loss


def main():
    print("=" * 60)
    print("RAILGUARD MODEL V4 - FIXED-Y-GRID TRAINING")
    print("=" * 60)

    train_ds = RailDataset("train", augment=True)
    val_ds   = RailDataset("val",   augment=False)

    train_dl = DataLoader(train_ds, batch_size=BATCH_SIZE, shuffle=True,
                          num_workers=NUM_WORKERS, pin_memory=False)
    val_dl   = DataLoader(val_ds,   batch_size=BATCH_SIZE, shuffle=False,
                          num_workers=NUM_WORKERS, pin_memory=False)

    model     = RailModel().to(device)
    criterion = MaskedMSELoss()
    optimizer = optim.AdamW(model.parameters(), lr=LR, weight_decay=WEIGHT_DECAY)
    scheduler = optim.lr_scheduler.CosineAnnealingLR(optimizer, T_max=NUM_EPOCHS, eta_min=1e-6)

    history       = {"train_loss": [], "val_loss": []}
    best_val_loss = float("inf")
    no_improve    = 0

    for epoch in range(1, NUM_EPOCHS + 1):
        # ── Train ──────────────────────────────────────────────────────────
        model.train()
        t_loss = 0.0
        for imgs, tgts in train_dl:
            imgs, tgts = imgs.to(device), tgts.to(device)
            optimizer.zero_grad()
            loss = criterion(model(imgs), tgts)
            loss.backward()
            torch.nn.utils.clip_grad_norm_(model.parameters(), 1.0)
            optimizer.step()
            t_loss += loss.item()
        t_loss /= len(train_dl)

        # ── Validate ───────────────────────────────────────────────────────
        model.eval()
        v_loss = 0.0
        with torch.no_grad():
            for imgs, tgts in val_dl:
                imgs, tgts = imgs.to(device), tgts.to(device)
                v_loss += criterion(model(imgs), tgts).item()
        v_loss /= len(val_dl)

        scheduler.step()
        history["train_loss"].append(t_loss)
        history["val_loss"].append(v_loss)

        improved = v_loss < best_val_loss
        if improved:
            best_val_loss = v_loss
            no_improve    = 0
            torch.save(model.state_dict(), os.path.join(OUTPUT_DIR, "best_model.pth"))
        else:
            no_improve += 1

        marker = " <best>" if improved else ""
        print(f"Epoch [{epoch:3d}/{NUM_EPOCHS}]  train={t_loss:.6f}  val={v_loss:.6f}{marker}")

        if no_improve >= PATIENCE:
            print(f"Early stop at epoch {epoch}.")
            break

    # ── Export ──────────────────────────────────────────────────────────────
    print("\nExporting best model to ONNX...")
    model.load_state_dict(torch.load(os.path.join(OUTPUT_DIR, "best_model.pth"),
                                     map_location=device))
    model.eval()
    dummy = torch.randn(1, 3, INPUT_H, INPUT_W).to(device)
    onnx_path = os.path.join(OUTPUT_DIR, "rail_model.onnx")
    torch.onnx.export(
        model, dummy, onnx_path,
        input_names=["image"], output_names=["coords"],
        dynamic_axes={"image": {0: "batch"}, "coords": {0: "batch"}},
        opset_version=12
    )
    print(f"ONNX saved -> {onnx_path}")

    # Save Y grid alongside model so inference knows the fixed rows
    np.save(os.path.join(OUTPUT_DIR, "fixed_y_grid.npy"),
            np.linspace(1.0, 0.38, NUM_SAMPLES).astype(np.float32))

    with open(os.path.join(OUTPUT_DIR, "training_history.json"), "w") as f:
        json.dump(history, f, indent=2)
    print("Training complete.")


if __name__ == "__main__":
    main()
