import json
import os
import sys
# pyrefly: ignore [missing-import]
import numpy as np
import torch
from torchvision import transforms
from PIL import Image

sys.path.insert(0, os.path.dirname(__file__))
from train_rail_model_v3 import (
    RailDatasetV3, RailNet, rail_loss,
    INPUT_W, INPUT_H, NUM_OUTPUTS,
    OUTPUT_DIR
)

def evaluate(split="val"):
    device = torch.device("cpu")
    checkpoint_path = os.path.join(OUTPUT_DIR, "best_model.pt")

    if not os.path.exists(checkpoint_path):
        raise FileNotFoundError(
            f"Checkpoint not found:\n{checkpoint_path}\n"
            "Run train_rail_model_v3.py first."
        )

    print(f"Loading checkpoint: {checkpoint_path}")
    checkpoint = torch.load(checkpoint_path, map_location=device)

    model = RailNet(num_outputs=NUM_OUTPUTS).to(device)
    model.load_state_dict(checkpoint["model_state_dict"])
    model.eval()

    print(
        f"Checkpoint from epoch {checkpoint['epoch']}, "
        f"val_loss={checkpoint['val_loss']:.6f}"
    )

    ds = RailDatasetV3(split, augment=False)

    all_preds   = []
    all_targets = []

    with torch.no_grad():
        for idx in range(len(ds)):
            image, target = ds[idx]
            image = image.unsqueeze(0).to(device)
            pred  = model(image).squeeze(0).cpu().numpy()
            all_preds.append(pred)
            all_targets.append(target.numpy())

    preds   = np.stack(all_preds)    # (N, 64)
    targets = np.stack(all_targets)  # (N, 64)

    print()
    print("=" * 70)
    print(f"EVALUATION - {split.upper()} SPLIT (CLEAN DATASET V4)", flush=True)
    print("=" * 70)
    print()

    def _mae(p, t):
        return float(np.mean(np.abs(p - t)))

    left_x_mae  = _mae(preds[:, 0:16],  targets[:, 0:16])
    right_x_mae = _mae(preds[:, 16:32], targets[:, 16:32])
    left_y_mae  = _mae(preds[:, 32:48], targets[:, 32:48])
    right_y_mae = _mae(preds[:, 48:64], targets[:, 48:64])

    left_x_px  = left_x_mae  * INPUT_W
    right_x_px = right_x_mae * INPUT_W
    left_y_px  = left_y_mae  * INPUT_H
    right_y_px = right_y_mae * INPUT_H

    overall_x_mae_px = (left_x_px + right_x_px) / 2
    overall_y_mae_px = (left_y_px + right_y_px) / 2

    print(f"{'Metric':<25} {'Normalised MAE':>16}  {'~Pixels':>10}", flush=True)
    print("-" * 56, flush=True)
    print(f"{'Left  rail X':<25} {left_x_mae:>16.5f}  {left_x_px:>10.2f}px", flush=True)
    print(f"{'Right rail X':<25} {right_x_mae:>16.5f}  {right_x_px:>10.2f}px", flush=True)
    print(f"{'Left  rail Y':<25} {left_y_mae:>16.5f}  {left_y_px:>10.2f}px", flush=True)
    print(f"{'Right rail Y':<25} {right_y_mae:>16.5f}  {right_y_px:>10.2f}px", flush=True)
    print("-" * 56, flush=True)
    print(f"{'Overall X MAE':<25} {'':>16}  {overall_x_mae_px:>10.2f}px", flush=True)
    print(f"{'Overall Y MAE':<25} {'':>16}  {overall_y_mae_px:>10.2f}px", flush=True)
    print(flush=True)

    onnx_path = os.path.join(OUTPUT_DIR, "rail_model.onnx")

    if os.path.exists(onnx_path):
        try:
            import onnxruntime as ort
            sess = ort.InferenceSession(onnx_path)

            image, _ = ds[0]
            image_np = image.unsqueeze(0).numpy()

            pt_out  = model(torch.from_numpy(image_np)).detach().numpy()
            ort_out = sess.run(None, {"image": image_np})[0]

            max_diff = float(np.max(np.abs(pt_out - ort_out)))
            print(
                f"ONNX vs PyTorch max diff: {max_diff:.2e}"
                f"  {'[OK]' if max_diff < 1e-4 else '[HIGH]'}",
                flush=True
            )

        except Exception as e:
            print(f"ONNX check failed: {e}")

    print()
    print("=" * 70)
    print("EVALUATION COMPLETE")
    print("=" * 70)


if __name__ == "__main__":
    evaluate("val")
    evaluate("train")
