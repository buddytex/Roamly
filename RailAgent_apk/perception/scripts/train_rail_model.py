import json
import os

import math
import time

import numpy as np
import torch
import torch.nn as nn
from torch.utils.data import Dataset, DataLoader
from torchvision import models, transforms
from PIL import Image


# ============================================================
# PATHS
# ============================================================

BASE_DIR = r"C:\RailGuardAI"

DATASET_DIR  = os.path.join(BASE_DIR, "prepared_dataset_v3_1")
IMAGE_DIR    = os.path.join(BASE_DIR, "training_images")   # pre-extracted flat dir
OUTPUT_DIR   = os.path.join(BASE_DIR, "model_v1")

os.makedirs(OUTPUT_DIR, exist_ok=True)


# ============================================================
# SETTINGS
# ============================================================

INPUT_W     = 256       # Input width fed to the network
INPUT_H     = 144       # Input height (16:9)
NUM_SAMPLES = 16        # Points per rail
NUM_OUTPUTS = 64        # left_x[16] + right_x[16] + left_y[16] + right_y[16]

BATCH_SIZE  = 8
EPOCHS      = 50
LR          = 1e-3
WEIGHT_DECAY= 1e-4

# How much more to penalise X errors vs Y errors.
# Y is more predictable (evenly spaced by construction);
# X is the key rail-position signal.
X_LOSS_WEIGHT = 3.0
Y_LOSS_WEIGHT = 1.0


# ============================================================
# DATASET
# ============================================================

class RailDataset(Dataset):
    """
    Loads rail-tracking training samples.

    Pre-caches resized PIL images (256x144) in RAM to avoid repeated high-res PNG
    decoding from disk during training.
    """

    def __init__(self, split, transform=None):

        self.transform = transform

        target_file   = os.path.join(DATASET_DIR, f"{split}_targets.npy")
        metadata_file = os.path.join(DATASET_DIR, f"{split}_metadata.json")

        raw_targets = np.load(target_file)          # (N, 96)

        with open(metadata_file, "r", encoding="utf-8") as f:
            metadata = json.load(f)

        assert len(raw_targets) == len(metadata), "Target/metadata mismatch"

        self.targets = raw_targets[:, :64].astype(np.float32)

        self.samples = []
        self.cached_images = []
        skipped = 0

        print(f"[{split}] Pre-loading and resizing images to {INPUT_W}x{INPUT_H} into RAM …", flush=True)

        for i, meta in enumerate(metadata):
            basename = os.path.basename(
                meta["file_name"].replace("\\", "/")
            )

            img_path = os.path.join(IMAGE_DIR, basename)

            if not os.path.exists(img_path):
                skipped += 1
                continue

            with Image.open(img_path) as img:
                resized_img = img.convert("RGB").resize((INPUT_W, INPUT_H), Image.BILINEAR)

            self.cached_images.append(resized_img)
            self.samples.append(i)

        print(
            f"[{split}] {len(self.samples)} samples cached in RAM"
            f" ({skipped} images not found in {IMAGE_DIR})",
            flush=True
        )

    def __len__(self):
        return len(self.samples)

    def __getitem__(self, idx):
        image = self.cached_images[idx]
        target_idx = self.samples[idx]

        if self.transform:
            image = self.transform(image)

        target = torch.from_numpy(self.targets[target_idx])

        return image, target


# ============================================================
# TRANSFORMS
# ============================================================

_mean = [0.485, 0.456, 0.406]
_std  = [0.229, 0.224, 0.225]

train_transform = transforms.Compose(
    [
        transforms.ColorJitter(
            brightness=0.3,
            contrast=0.3,
            saturation=0.2
        ),
        transforms.ToTensor(),
        transforms.Normalize(_mean, _std),
    ]
)

val_transform = transforms.Compose(
    [
        transforms.ToTensor(),
        transforms.Normalize(_mean, _std),
    ]
)


# ============================================================
# MODEL
# ============================================================

class RailNet(nn.Module):
    """
    MobileNetV3-Small backbone with a custom regression head.

    Backbone output: 576-dimensional vector (avg-pooled)
    Head: 576 → 256 → 64
    """

    def __init__(self, num_outputs=NUM_OUTPUTS):
        super().__init__()

        # Load pretrained MobileNetV3-Small
        backbone = models.mobilenet_v3_small(
            weights=models.MobileNet_V3_Small_Weights.IMAGENET1K_V1
        )

        # Use only the feature extractor + adaptive pool
        self.features   = backbone.features
        self.avgpool    = backbone.avgpool

        # Feature dimension of MobileNetV3-Small = 576
        in_features = 576

        self.head = nn.Sequential(
            nn.Linear(in_features, 256),
            nn.Hardswish(),
            nn.Dropout(p=0.2),
            nn.Linear(256, num_outputs),
            nn.Sigmoid()          # outputs are normalized 0-1
        )

    def forward(self, x):
        x = self.features(x)
        x = self.avgpool(x)
        x = torch.flatten(x, 1)
        x = self.head(x)
        return x


# ============================================================
# LOSS
# ============================================================

def rail_loss(pred, target):
    """
    Weighted MSE: X positions are penalised more than Y positions
    since X is the rail lateral signal the robot needs.

    pred   : (B, 64)
    target : (B, 64)

    Layout (both pred and target):
        0 :16   left X
        16:32   right X
        32:48   left Y
        48:64   right Y
    """

    left_x_pred    = pred[:, 0:16]
    right_x_pred   = pred[:, 16:32]
    left_y_pred    = pred[:, 32:48]
    right_y_pred   = pred[:, 48:64]

    left_x_target  = target[:, 0:16]
    right_x_target = target[:, 16:32]
    left_y_target  = target[:, 32:48]
    right_y_target = target[:, 48:64]

    x_loss = (
        nn.functional.mse_loss(left_x_pred,  left_x_target)
        + nn.functional.mse_loss(right_x_pred, right_x_target)
    )

    y_loss = (
        nn.functional.mse_loss(left_y_pred,  left_y_target)
        + nn.functional.mse_loss(right_y_pred, right_y_target)
    )

    return X_LOSS_WEIGHT * x_loss + Y_LOSS_WEIGHT * y_loss


# ============================================================
# TRAINING LOOP
# ============================================================

def train():

    device = torch.device("cpu")

    print()
    print("=" * 70)
    print("RAIL DETECTION MODEL TRAINING")
    print("=" * 70)
    print(f"Device : {device}")
    print(f"Input  : {INPUT_W}x{INPUT_H}")
    print(f"Outputs: {NUM_OUTPUTS}")
    print(f"Epochs : {EPOCHS}")
    print(f"Batch  : {BATCH_SIZE}")
    print()

    # ---------------------------------------------------------
    # Datasets
    # ---------------------------------------------------------

    train_ds = RailDataset("train", transform=train_transform)
    val_ds   = RailDataset("val",   transform=val_transform)

    train_loader = DataLoader(
        train_ds,
        batch_size=BATCH_SIZE,
        shuffle=True,
        num_workers=0,
        pin_memory=False
    )

    val_loader = DataLoader(
        val_ds,
        batch_size=BATCH_SIZE,
        shuffle=False,
        num_workers=0,
        pin_memory=False
    )

    # ---------------------------------------------------------
    # Model
    # ---------------------------------------------------------

    model = RailNet().to(device)
    total_params = sum(p.numel() for p in model.parameters())
    trainable_params = sum(
        p.numel() for p in model.parameters() if p.requires_grad
    )
    print(f"Parameters: {total_params:,} total / {trainable_params:,} trainable")
    print()

    # ---------------------------------------------------------
    # Optimiser + LR schedule
    # ---------------------------------------------------------

    optimizer = torch.optim.Adam(
        model.parameters(),
        lr=LR,
        weight_decay=WEIGHT_DECAY
    )

    scheduler = torch.optim.lr_scheduler.CosineAnnealingLR(
        optimizer,
        T_max=EPOCHS,
        eta_min=1e-5
    )

    # ---------------------------------------------------------
    # Training
    # ---------------------------------------------------------

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
            preds = model(images)
            loss  = rail_loss(preds, targets)
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
                preds   = model(images)
                loss    = rail_loss(preds, targets)
                val_loss_sum += loss.item() * images.size(0)

        val_loss = val_loss_sum / len(val_ds)

        scheduler.step()

        elapsed = time.time() - t0
        current_lr = scheduler.get_last_lr()[0]

        print(
            f"{epoch:>6}  {train_loss:>12.6f}  {val_loss:>12.6f}"
            f"  {current_lr:>10.6f}  {elapsed:>6.1f}s",
            flush=True
        )

        history.append(
            {
                "epoch": epoch,
                "train_loss": train_loss,
                "val_loss": val_loss,
                "lr": current_lr
            }
        )

        if val_loss < best_val_loss:
            best_val_loss = val_loss
            best_epoch    = epoch
            torch.save(
                {
                    "epoch": epoch,
                    "model_state_dict": model.state_dict(),
                    "optimizer_state_dict": optimizer.state_dict(),
                    "val_loss": val_loss,
                },
                checkpoint_path
            )

    print()
    print(f"Best model: epoch {best_epoch}, val_loss={best_val_loss:.6f}")
    print(f"Saved: {checkpoint_path}")

    # Save training history
    history_path = os.path.join(OUTPUT_DIR, "training_history.json")
    with open(history_path, "w", encoding="utf-8") as f:
        json.dump(history, f, indent=2)
    print(f"Saved: {history_path}")

    # ---------------------------------------------------------
    # Export to ONNX
    # ---------------------------------------------------------

    print()
    print("Exporting to ONNX …")

    checkpoint = torch.load(checkpoint_path, map_location=device)
    model.load_state_dict(checkpoint["model_state_dict"])
    model.eval()

    dummy_input = torch.zeros(1, 3, INPUT_H, INPUT_W)

    onnx_path = os.path.join(OUTPUT_DIR, "rail_model.onnx")

    torch.onnx.export(
        model,
        dummy_input,
        onnx_path,
        input_names=["image"],
        output_names=["rail_coords"],
        dynamic_axes={
            "image":       {0: "batch"},
            "rail_coords": {0: "batch"}
        },
        opset_version=17,
        dynamo=False
    )

    print(f"Saved: {onnx_path}")

    # ---------------------------------------------------------
    # Verify ONNX
    # ---------------------------------------------------------

    try:
        import onnxruntime as ort

        sess = ort.InferenceSession(onnx_path)
        dummy = np.zeros((1, 3, INPUT_H, INPUT_W), dtype=np.float32)
        out   = sess.run(None, {"image": dummy})
        print(f"ONNX output shape: {out[0].shape}  ✓")

    except Exception as e:
        print(f"ONNX verification failed: {e}")

    print()
    print("=" * 70)
    print("TRAINING COMPLETE")
    print("=" * 70)
    print(f"Output directory: {OUTPUT_DIR}")


# ============================================================
# ENTRY POINT
# ============================================================

if __name__ == "__main__":
    train()
