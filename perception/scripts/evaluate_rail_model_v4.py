"""
evaluate_rail_model_v4.py
─────────────────────────
Loads the model_v4/best_model.pth weight checkpoint and evaluates the model on
train/val splits of prepared_dataset_v5, computing Masked Mean Absolute Error (MAE)
for valid points. Also performs consistency validation against rail_model.onnx.
"""
import sys
# Force UTF-8 encoding for stdout/stderr to prevent UnicodeEncodeError on Windows console print
if hasattr(sys.stdout, 'reconfigure'):
    sys.stdout.reconfigure(encoding='utf-8')
if hasattr(sys.stderr, 'reconfigure'):
    sys.stderr.reconfigure(encoding='utf-8')

import json
import os
import numpy as np
import torch

sys.path.insert(0, os.path.dirname(__file__))
from train_rail_model_v4 import RailModel, RailDataset, INPUT_W, INPUT_H, OUTPUT_DIR

def evaluate(split="val"):
    device = torch.device("cpu")
    model_path = os.path.join(OUTPUT_DIR, "best_model.pth")

    if not os.path.exists(model_path):
        raise FileNotFoundError(
            f"Checkpoint not found:\n{model_path}\n"
            "Run train_rail_model_v4.py or finalize_model_v4.py first."
        )

    print(f"Loading checkpoint: {model_path}")
    model = RailModel().to(device)
    model.load_state_dict(torch.load(model_path, map_location=device))
    model.eval()

    ds = RailDataset(split, augment=False)

    all_preds   = []
    all_targets = []

    with torch.no_grad():
        for idx in range(len(ds)):
            image, target = ds[idx]
            image = image.unsqueeze(0).to(device)
            pred  = model(image).squeeze(0).cpu().numpy()
            all_preds.append(pred)
            all_targets.append(target.numpy())

    preds   = np.stack(all_preds)    # (N, 96)
    targets = np.stack(all_targets)  # (N, 96)

    # Decode target dimensions
    # 0:16 = left_x, 16:32 = right_x
    # 32:48 = left_y, 48:64 = right_y
    # 64:80 = left_valid, 80:96 = right_valid
    pred_lx, pred_rx = preds[:, 0:16], preds[:, 16:32]
    gt_lx, gt_rx = targets[:, 0:16], targets[:, 16:32]
    val_l, val_r = targets[:, 64:80], targets[:, 80:96]

    # Compute Masked MAE
    left_x_mae = float(np.sum(np.abs(pred_lx - gt_lx) * val_l) / (np.sum(val_l) + 1e-6))
    right_x_mae = float(np.sum(np.abs(pred_rx - gt_rx) * val_r) / (np.sum(val_r) + 1e-6))

    left_x_px = left_x_mae * INPUT_W
    right_x_px = right_x_mae * INPUT_W
    overall_x_mae_px = (left_x_px + right_x_px) / 2

    print()
    print("=" * 70)
    print(f"EVALUATION - {split.upper()} SPLIT (DATASET V5 - MASKED)", flush=True)
    print("=" * 70)
    print()
    print(f"{'Metric':<25} {'Normalised MAE':>16}  {'~Pixels':>10}", flush=True)
    print("-" * 56, flush=True)
    print(f"{'Left  rail X (Masked)':<25} {left_x_mae:>16.5f}  {left_x_px:>10.2f}px", flush=True)
    print(f"{'Right rail X (Masked)':<25} {right_x_mae:>16.5f}  {right_x_px:>10.2f}px", flush=True)
    print("-" * 56, flush=True)
    print(f"{'Overall X MAE (Masked)':<25} {'':>16}  {overall_x_mae_px:>10.2f}px", flush=True)
    print(flush=True)

    # ── ONNX Consistency Check ─────────────────────────────────────────────
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
            print(f"ONNX consistency check failed: {e}")

    print()
    print("=" * 70)
    print("EVALUATION COMPLETE")
    print("=" * 70)

if __name__ == "__main__":
    evaluate("val")
    evaluate("train")
