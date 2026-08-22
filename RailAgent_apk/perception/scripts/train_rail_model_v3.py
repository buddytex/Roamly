import json
import os
import math
import time
import random

import numpy as np
import torch
import torch.nn as nn
import torchvision.transforms.functional as TF
from torch.utils.data import Dataset, DataLoader
from torchvision import models, transforms
from PIL import Image, ImageFilter

# ============================================================
# PATHS
# ============================================================

BASE_DIR    = r"C:\RailGuardAI"
DATASET_DIR = os.path.join(BASE_DIR, "prepared_dataset_v4")
IMAGE_DIR   = os.path.join(BASE_DIR, "training_images")
OUTPUT_DIR  = os.path.join(BASE_DIR, "model_v3")

os.makedirs(OUTPUT_DIR, exist_ok=True)

# ============================================================
# SETTINGS
# ============================================================

INPUT_W     = 256
INPUT_H     = 144
NUM_SAMPLES = 16
NUM_OUTPUTS = 64   # left_x[16] + right_x[16] + left_y[16] + right_y[16]

BATCH_SIZE  = 8
EPOCHS      = 60
LR          = 1e-3
WEIGHT_DECAY= 1e-4

X_LOSS_WEIGHT = 3.0
Y_LOSS_WEIGHT = 1.0

# ============================================================
# TARGET FLIP HELPER
# ============================================================

def flip_target(t: np.ndarray) -> np.ndarray:
    left_x  = t[0:16]
    right_x = t[16:32]
    left_y  = t[32:48]
    right_y = t[48:64]
    return np.concatenate([
        1.0 - right_x,   # new left_x
        1.0 - left_x,    # new right_x
        right_y,         # new left_y
        left_y,          # new right_y
    ]).astype(np.float32)


# ============================================================
# DATASET
# ============================================================

_mean = [0.485, 0.456, 0.406]
_std  = [0.229, 0.224, 0.225]

class RailDatasetV3(Dataset):
    def __init__(self, split: str, augment: bool = False):
        self.augment = augment

        target_file   = os.path.join(DATASET_DIR, f"{split}_targets.npy")
        metadata_file = os.path.join(DATASET_DIR, f"{split}_metadata.json")

        raw = np.load(target_file)                    # (N, 96)
        with open(metadata_file, encoding="utf-8") as f:
            metadata = json.load(f)

        assert len(raw) == len(metadata)

        self.targets = raw[:, :64].astype(np.float32)

        self.samples       = []
        self.cached_images = []
        skipped = 0

        print(f"[{split}] Pre-loading images into RAM ...", flush=True)

        for i, meta in enumerate(metadata):
            basename = os.path.basename(meta["file_name"].replace("\\", "/"))
            img_path = os.path.join(IMAGE_DIR, basename)

            if not os.path.exists(img_path):
                skipped += 1
                continue

            with Image.open(img_path) as img:
                resized = img.convert("RGB").resize((INPUT_W, INPUT_H), Image.BILINEAR)

            self.cached_images.append(resized)
            self.samples.append(i)

        print(
            f"[{split}] {len(self.samples)} samples cached"
            f" ({skipped} not found)",
            flush=True,
        )

        # Fixed colour jitter used during augmentation
        self._jitter = transforms.ColorJitter(
            brightness=0.4,
            contrast=0.4,
            saturation=0.3,
            hue=0.05,
        )

        # Normalise to tensor
        self._to_tensor = transforms.ToTensor()
        self._normalize = transforms.Normalize(_mean, _std)

        # Random erasing (applied after ToTensor)
        self._erasing = transforms.RandomErasing(
            p=0.25,
            scale=(0.02, 0.12),
            ratio=(0.3, 3.3),
        )

    def __len__(self):
        return len(self.samples)

    def __getitem__(self, idx):
        img    = self.cached_images[idx]            # PIL (W,H,3) already 256x144
        target = self.targets[self.samples[idx]].copy()

        if self.augment:
            # 1. Horizontal flip + label swap
            if random.random() < 0.5:
                img    = TF.hflip(img)
                target = flip_target(target)

            # 2. Random affine (rotation + scale)
            if random.random() < 0.5:
                angle = random.uniform(-6.0, 6.0)
                scale = random.uniform(0.90, 1.10)
                img = TF.affine(
                    img,
                    angle=angle,
                    translate=[0, 0],
                    scale=scale,
                    shear=0,
                    interpolation=TF.InterpolationMode.BILINEAR,
                    fill=0,
                )
                # Transform target X/Y for rotation + scale about image centre
                cx, cy = 0.5, 0.5
                rad    = math.radians(angle)   # PIL rotates CCW; match angle directly
                cos_a, sin_a = math.cos(rad), math.sin(rad)

                left_x  = target[0:16];  right_x = target[16:32]
                left_y  = target[32:48]; right_y = target[48:64]

                def transform_pts(xs, ys):
                    xs_c = xs - cx;  ys_c = ys - cy
                    new_x = (cos_a * xs_c - sin_a * ys_c) / scale + cx
                    new_y = (sin_a * xs_c + cos_a * ys_c) / scale + cy
                    return (
                        np.clip(new_x, 0.0, 1.0).astype(np.float32),
                        np.clip(new_y, 0.0, 1.0).astype(np.float32),
                    )

                new_lx, new_ly = transform_pts(left_x,  left_y)
                new_rx, new_ry = transform_pts(right_x, right_y)
                target = np.concatenate([new_lx, new_rx, new_ly, new_ry])

            # 3. Colour jitter
            img = self._jitter(img)

            # 4. Random Gaussian blur
            if random.random() < 0.25:
                r = random.choice([1, 2])
                img = img.filter(ImageFilter.GaussianBlur(radius=r))

            # 5. Random grayscale
            if random.random() < 0.15:
                img = TF.to_grayscale(img, num_output_channels=3)

        # To tensor + normalize
        tensor = self._normalize(self._to_tensor(img))

        # 6. Random erasing (on tensor, train only)
        if self.augment:
            tensor = self._erasing(tensor)

        return tensor, torch.from_numpy(target)


# ============================================================
# MODEL
# ============================================================

class RailNet(nn.Module):
    def __init__(self, num_outputs=NUM_OUTPUTS):
        super().__init__()
        backbone = models.mobilenet_v3_small(
            weights=models.MobileNet_V3_Small_Weights.IMAGENET1K_V1
        )
        self.features = backbone.features
        self.avgpool  = backbone.avgpool
        self.head = nn.Sequential(
            nn.Linear(576, 256),
            nn.Hardswish(),
            nn.Dropout(p=0.3),
            nn.Linear(256, num_outputs),
            nn.Sigmoid(),
        )

    def forward(self, x):
        x = self.features(x)
        x = self.avgpool(x)
        x = torch.flatten(x, 1)
        return self.head(x)


# ============================================================
# LOSS
# ============================================================

def rail_loss(pred, target):
    lx = nn.functional.mse_loss(pred[:, 0:16],  target[:, 0:16])
    rx = nn.functional.mse_loss(pred[:, 16:32], target[:, 16:32])
    ly = nn.functional.mse_loss(pred[:, 32:48], target[:, 32:48])
    ry = nn.functional.mse_loss(pred[:, 48:64], target[:, 48:64])
    return X_LOSS_WEIGHT * (lx + rx) + Y_LOSS_WEIGHT * (ly + ry)


# ============================================================
# TRAINING LOOP
# ============================================================

def train():
    device = torch.device("cpu")

    print()
    print("=" * 70)
    print("RAILGUARDAI V3 - TRAINING WITH EGO-TRACK CLEAN DATASET")
    print("=" * 70)
    print(f"Device      : {device}")
    print(f"Input       : {INPUT_W}x{INPUT_H}")
    print(f"Epochs      : {EPOCHS}")
    print(f"Batch size  : {BATCH_SIZE}")
    print(f"Augmentation: flip + affine + jitter + blur + grayscale + erasing")
    print()

    train_ds = RailDatasetV3("train", augment=True)
    val_ds   = RailDatasetV3("val",   augment=False)

    train_loader = DataLoader(
        train_ds, batch_size=BATCH_SIZE, shuffle=True,
        num_workers=0, pin_memory=False,
    )
    val_loader = DataLoader(
        val_ds, batch_size=BATCH_SIZE, shuffle=False,
        num_workers=0, pin_memory=False,
    )

    model = RailNet().to(device)
    total_params    = sum(p.numel() for p in model.parameters())
    trainable_params = sum(p.numel() for p in model.parameters() if p.requires_grad)
    print(f"Parameters: {total_params:,} total / {trainable_params:,} trainable")
    print()

    optimizer = torch.optim.Adam(
        model.parameters(), lr=LR, weight_decay=WEIGHT_DECAY
    )
    scheduler = torch.optim.lr_scheduler.CosineAnnealingLR(
        optimizer, T_max=EPOCHS, eta_min=1e-5
    )

    best_val_loss = float("inf")
    best_epoch    = -1
    history       = []
    checkpoint_path = os.path.join(OUTPUT_DIR, "best_model.pt")

    print(f"{'Epoch':>6}  {'Train Loss':>12}  {'Val Loss':>12}  {'LR':>10}  {'Time':>8}")
    print("-" * 60)

    for epoch in range(1, EPOCHS + 1):
        t0 = time.time()

        # --- Train ---
        model.train()
        train_loss_sum = 0.0
        for images, targets in train_loader:
            images  = images.to(device)
            targets = targets.to(device)
            optimizer.zero_grad()
            loss = rail_loss(model(images), targets)
            loss.backward()
            optimizer.step()
            train_loss_sum += loss.item() * images.size(0)
        train_loss = train_loss_sum / len(train_ds)

        # --- Validate ---
        model.eval()
        val_loss_sum = 0.0
        with torch.no_grad():
            for images, targets in val_loader:
                images  = images.to(device)
                targets = targets.to(device)
                val_loss_sum += rail_loss(model(images), targets).item() * images.size(0)
        val_loss = val_loss_sum / len(val_ds)

        scheduler.step()
        elapsed    = time.time() - t0
        current_lr = scheduler.get_last_lr()[0]

        print(
            f"{epoch:>6}  {train_loss:>12.6f}  {val_loss:>12.6f}"
            f"  {current_lr:>10.6f}  {elapsed:>6.1f}s",
            flush=True,
        )

        history.append({
            "epoch": epoch,
            "train_loss": train_loss,
            "val_loss": val_loss,
            "lr": current_lr,
        })

        if val_loss < best_val_loss:
            best_val_loss = val_loss
            best_epoch    = epoch
            torch.save({
                "epoch": epoch,
                "model_state_dict": model.state_dict(),
                "optimizer_state_dict": optimizer.state_dict(),
                "val_loss": val_loss,
            }, checkpoint_path)

    print()
    print(f"Best model: epoch {best_epoch}, val_loss={best_val_loss:.6f}")
    print(f"Saved: {checkpoint_path}")

    history_path = os.path.join(OUTPUT_DIR, "training_history.json")
    with open(history_path, "w", encoding="utf-8") as f:
        json.dump(history, f, indent=2)
    print(f"Saved: {history_path}")

    # --- ONNX export ---
    print()
    print("Exporting to ONNX ...")
    ckpt = torch.load(checkpoint_path, map_location=device)
    model.load_state_dict(ckpt["model_state_dict"])
    model.eval()

    dummy    = torch.zeros(1, 3, INPUT_H, INPUT_W)
    onnx_path = os.path.join(OUTPUT_DIR, "rail_model.onnx")

    torch.onnx.export(
        model, dummy, onnx_path,
        input_names=["image"], output_names=["rail_coords"],
        dynamic_axes={"image": {0: "batch"}, "rail_coords": {0: "batch"}},
        opset_version=17,
        dynamo=False,
    )
    print(f"Saved: {onnx_path}")

    try:
        import onnxruntime as ort
        sess  = ort.InferenceSession(onnx_path)
        dummy_np = np.zeros((1, 3, INPUT_H, INPUT_W), dtype=np.float32)
        out   = sess.run(None, {"image": dummy_np})
        print(f"ONNX output shape: {out[0].shape}  OK")
    except Exception as e:
        print(f"ONNX verification failed: {e}")

    print()
    print("=" * 70)
    print("V3 TRAINING COMPLETE")
    print("=" * 70)
    print(f"Output: {OUTPUT_DIR}")


if __name__ == "__main__":
    train()
