r"""
infer_rail_model.py

Run the trained RailGuardAI ONNX model on one image or a folder of images.
Draws predicted left (blue) and right (red) rail curves on each image and
saves the result to C:\RailGuardAI\rail_samples\predictions\

Usage
-----
# Single image
python scripts/infer_rail_model.py --image path/to/frame.png

# Folder of images (processes every PNG/JPG inside)
python scripts/infer_rail_model.py --folder path/to/frames/

# Use training_images/ folder as a quick sanity-check
python scripts/infer_rail_model.py --folder C:\RailGuardAI\training_images --max 20
"""

import argparse
import os
import sys
import time

import cv2
import numpy as np

# ============================================================
# PATHS
# ============================================================

BASE_DIR   = r"C:\RailGuardAI"
ONNX_PATH  = os.path.join(BASE_DIR, "model_v1", "rail_model.onnx")
OUTPUT_DIR = os.path.join(BASE_DIR, "rail_samples", "predictions")

os.makedirs(OUTPUT_DIR, exist_ok=True)

# ============================================================
# MODEL SETTINGS  (must match training)
# ============================================================

INPUT_W    = 256
INPUT_H    = 144
NUM_POINTS = 16

_MEAN = np.array([0.485, 0.456, 0.406], dtype=np.float32)
_STD  = np.array([0.229, 0.224, 0.225], dtype=np.float32)

# ============================================================
# COLOURS
# ============================================================

LEFT_COLOUR  = (255,  60,  60)   # Blue-ish (BGR)
RIGHT_COLOUR = ( 60,  60, 255)   # Red-ish  (BGR)
POINT_RADIUS = 6
LINE_THICKNESS = 3


# ============================================================
# HELPERS
# ============================================================

def load_onnx_session(onnx_path: str):
    try:
        import onnxruntime as ort
    except ImportError:
        sys.exit(
            "onnxruntime is not installed.\n"
            "Run:  pip install onnxruntime"
        )
    sess = ort.InferenceSession(onnx_path)
    return sess


def preprocess(image_bgr: np.ndarray) -> np.ndarray:
    """
    BGR uint8 (H, W, 3)  ->  float32 (1, 3, INPUT_H, INPUT_W)
    ImageNet normalisation applied.
    """
    resized = cv2.resize(image_bgr, (INPUT_W, INPUT_H))
    rgb     = resized[:, :, ::-1].astype(np.float32) / 255.0
    rgb     = (rgb - _MEAN) / _STD
    chw     = rgb.transpose(2, 0, 1)
    return chw[np.newaxis, :, :, :]


def run_inference(sess, image_bgr: np.ndarray) -> dict:
    """
    Returns a dict with pixel-space rail keypoints on the *original* image.
    """
    h, w = image_bgr.shape[:2]

    inp  = preprocess(image_bgr)
    raw  = sess.run(None, {"image": inp})[0][0]

    left_x_norm  = raw[0:16]
    right_x_norm = raw[16:32]
    left_y_norm  = raw[32:48]
    right_y_norm = raw[48:64]

    left_pts  = [(int(x * w), int(y * h))
                 for x, y in zip(left_x_norm,  left_y_norm)]
    right_pts = [(int(x * w), int(y * h))
                 for x, y in zip(right_x_norm, right_y_norm)]

    return {
        "left_pts":       left_pts,
        "right_pts":      right_pts,
        "left_x_norm":    left_x_norm,
        "right_x_norm":   right_x_norm,
        "left_y_norm":    left_y_norm,
        "right_y_norm":   right_y_norm,
    }


def draw_predictions(image_bgr: np.ndarray, result: dict) -> np.ndarray:
    vis = image_bgr.copy()
    h, w = vis.shape[:2]

    left_pts  = result["left_pts"]
    right_pts = result["right_pts"]

    if len(left_pts) >= 2:
        pts = np.array(left_pts, dtype=np.int32).reshape(-1, 1, 2)
        cv2.polylines(vis, [pts], False, LEFT_COLOUR, LINE_THICKNESS, cv2.LINE_AA)

    if len(right_pts) >= 2:
        pts = np.array(right_pts, dtype=np.int32).reshape(-1, 1, 2)
        cv2.polylines(vis, [pts], False, RIGHT_COLOUR, LINE_THICKNESS, cv2.LINE_AA)

    for pt in left_pts:
        cv2.circle(vis, pt, POINT_RADIUS, LEFT_COLOUR,  -1, cv2.LINE_AA)

    for pt in right_pts:
        cv2.circle(vis, pt, POINT_RADIUS, RIGHT_COLOUR, -1, cv2.LINE_AA)

    panel_h = 60
    panel   = np.zeros((panel_h, w, 3), dtype=np.uint8)
    font    = cv2.FONT_HERSHEY_SIMPLEX

    left_x_last  = int(result["left_x_norm"][-1]  * w)
    right_x_last = int(result["right_x_norm"][-1] * w)
    spread_px    = abs(right_x_last - left_x_last)

    cv2.putText(panel, "LEFT RAIL",  (15, 38), font, 0.7, LEFT_COLOUR,  2, cv2.LINE_AA)
    cv2.putText(panel, "RIGHT RAIL", (200, 38), font, 0.7, RIGHT_COLOUR, 2, cv2.LINE_AA)
    cv2.putText(panel, f"Rail spread (bottom): {spread_px}px",
                (420, 38), font, 0.65, (220, 220, 220), 1, cv2.LINE_AA)

    vis = np.vstack([panel, vis])
    return vis


def collect_images(path: str, max_count: int) -> list:
    exts = {".png", ".jpg", ".jpeg", ".bmp", ".tiff"}

    if os.path.isfile(path):
        return [path]

    paths = []
    for fname in sorted(os.listdir(path)):
        if os.path.splitext(fname)[1].lower() in exts:
            paths.append(os.path.join(path, fname))
        if len(paths) >= max_count:
            break

    return paths


# ============================================================
# MAIN
# ============================================================

def main():
    parser = argparse.ArgumentParser(description="RailGuardAI ONNX inference")
    grp = parser.add_mutually_exclusive_group(required=True)
    grp.add_argument("--image",  help="Path to a single image file")
    grp.add_argument("--folder", help="Path to a folder of images")
    parser.add_argument("--max",    type=int, default=50,
                        help="Max images from folder (default 50)")
    parser.add_argument("--onnx",   default=ONNX_PATH,
                        help=f"Path to ONNX model (default: {ONNX_PATH})")
    parser.add_argument("--output", default=OUTPUT_DIR,
                        help=f"Output directory (default: {OUTPUT_DIR})")
    parser.add_argument("--show",   action="store_true",
                        help="Display results in a window (press any key to advance, q to quit)")
    args = parser.parse_args()

    os.makedirs(args.output, exist_ok=True)

    print()
    print("=" * 70)
    print("RAILGUARDAI -- ONNX INFERENCE")
    print("=" * 70)
    print(f"Model : {args.onnx}")
    print(f"Output: {args.output}")
    print()

    if not os.path.exists(args.onnx):
        sys.exit(f"ONNX model not found: {args.onnx}\nRun train_rail_model.py first.")

    print("Loading ONNX session ...")
    sess = load_onnx_session(args.onnx)
    print("Model loaded.\n")

    dummy = np.zeros((1, 3, INPUT_H, INPUT_W), dtype=np.float32)
    sess.run(None, {"image": dummy})

    source = args.image or args.folder
    images = collect_images(source, args.max)

    if not images:
        sys.exit(f"No images found at: {source}")

    print(f"Found {len(images)} image(s) to process.\n")

    t_total = 0.0
    ok      = 0

    for idx, img_path in enumerate(images, start=1):
        bgr = cv2.imread(img_path)

        if bgr is None:
            print(f"[{idx:3d}/{len(images)}]  SKIP  {os.path.basename(img_path)}")
            continue

        t0     = time.perf_counter()
        result = run_inference(sess, bgr)
        t_ms   = (time.perf_counter() - t0) * 1000
        t_total += t_ms

        vis = draw_predictions(bgr, result)

        out_name = f"pred_{idx:04d}_{os.path.basename(img_path)}"
        out_path = os.path.join(args.output, out_name)
        cv2.imwrite(out_path, vis)

        left_x_mid  = float(result["left_x_norm"][8])
        right_x_mid = float(result["right_x_norm"][8])
        spread_mid  = right_x_mid - left_x_mid

        print(
            f"[{idx:3d}/{len(images)}]  {t_ms:5.1f}ms  "
            f"L_x={left_x_mid:.3f}  R_x={right_x_mid:.3f}  "
            f"spread={spread_mid:.3f}  {os.path.basename(img_path)}"
        )

        if args.show:
            disp_h = min(768, vis.shape[0])
            disp_w = int(vis.shape[1] * disp_h / vis.shape[0])
            small = cv2.resize(vis, (disp_w, disp_h))
            cv2.imshow("RailGuardAI", small)
            key = cv2.waitKey(0)
            if key == ord("q"):
                break

        ok += 1

    if args.show:
        cv2.destroyAllWindows()

    print()
    print("=" * 70)
    print("INFERENCE COMPLETE")
    print("=" * 70)
    print(f"  Processed : {ok}/{len(images)}")
    if ok > 0:
        print(f"  Avg time  : {t_total/ok:.1f} ms/frame")
        print(f"  Throughput: {1000/(t_total/ok):.1f} FPS (CPU)")
    print(f"  Output    : {args.output}")


if __name__ == "__main__":
    main()

