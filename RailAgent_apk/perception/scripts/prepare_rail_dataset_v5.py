"""
prepare_rail_dataset_v5.py  — Fixed-Y-Grid dataset preparation
─────────────────────────────────────────────────────────────
KEY FIX vs v4:
  v4:  ys = np.linspace(common_min, common_max, 16)  ← variable per image
  v5:  ys = FIXED_Y_GRID                              ← always the same rows

16 fixed rows from Y=1.0 (camera bottom) to Y=0.38 (near horizon).
Rows outside annotation coverage get valid=0 and are masked out of the loss.
"""
import json, os
import numpy as np
from collections import defaultdict

BASE_DIR       = r"C:\RailGuardAI"
ANNOTATION_DIR = os.path.join(BASE_DIR, "railbench", "annotations", "rails")
OUTPUT_DIR     = os.path.join(BASE_DIR, "prepared_dataset_v5")
os.makedirs(OUTPUT_DIR, exist_ok=True)

NUM_SAMPLES         = 16
FIXED_Y_GRID        = np.linspace(1.0, 0.38, NUM_SAMPLES).astype(np.float32)
MIN_POINTS          = 5
MIN_Y_RANGE         = 0.20
MAX_X_JUMP          = 0.18
MIN_RAIL_SEPARATION = 0.03


def remove_duplicate_points(points):
    _, indices = np.unique(np.round(points, 5), axis=0, return_index=True)
    return points[np.sort(indices)]


def fit_segment(polyline, width, height, degree):
    if len(polyline) < MIN_POINTS:
        return None
    pts = np.array([(float(x)/width, float(y)/height) for x, y in polyline], dtype=np.float32)
    pts = remove_duplicate_points(pts)
    if len(pts) < MIN_POINTS:
        return None
    y, x = pts[:, 1], pts[:, 0]
    y_min, y_max = float(y.min()), float(y.max())
    if y_max - y_min < MIN_Y_RANGE:
        return None
    order = np.argsort(y)
    y, x = y[order], x[order]
    _, uid = np.unique(y, return_index=True)
    y, x = y[uid], x[uid]
    if len(y) < MIN_POINTS:
        return None
    try:
        coeffs = np.polyfit(y, x, degree)
        return {"coefficients": coeffs, "y_min": y_min, "y_max": y_max}
    except Exception:
        return None


def curve_quality(xs):
    return len(xs) >= 2 and np.max(np.abs(np.diff(xs))) <= MAX_X_JUMP


def sample_on_fixed_grid(curve):
    """Evaluate polynomial at each fixed Y row; mask rows outside coverage."""
    y_min, y_max = curve["y_min"], curve["y_max"]
    margin = 0.03
    xs    = np.full(NUM_SAMPLES, 0.5, dtype=np.float32)
    valid = np.zeros(NUM_SAMPLES, dtype=np.float32)
    for i, y in enumerate(FIXED_Y_GRID):
        if (y_min - margin) <= y <= (y_max + margin):
            xs[i]    = float(np.clip(np.polyval(curve["coefficients"], y), 0.0, 1.0))
            valid[i] = 1.0
    return xs, valid


def process_split(split):
    ann_file = os.path.join(ANNOTATION_DIR, f"annotations_{split}.json")
    print(f"\n{'='*70}\nPROCESSING {split.upper()}  (v5 Fixed-Y-Grid)\n{'='*70}")
    print(f"Y-grid: {np.round(FIXED_Y_GRID, 3).tolist()}")

    with open(ann_file, "r") as f:
        data = json.load(f)

    images = {img["id"]: img for img in data["images"]}
    by_image = defaultdict(list)
    for ann in data["annotations"]:
        if ann.get("category_id") == 1 and "polyline" in ann and ann.get("rightRail") in (0, 1):
            by_image[ann["image_id"]].append(ann)

    targets, metadata = [], []
    rej_no = rej_one = rej_ego = ok2 = ok1 = 0

    for img_id, anns in by_image.items():
        img = images[img_id]
        W, H = img["width"], img["height"]
        L_segs = [a for a in anns if a["rightRail"] == 0]
        R_segs = [a for a in anns if a["rightRail"] == 1]

        if not L_segs and not R_segs: rej_no  += 1; continue
        if not L_segs or  not R_segs: rej_one += 1; continue

        candidates = []
        for deg in [2, 1]:
            lcs = [c for s in L_segs if (c := fit_segment(s["polyline"], W, H, deg))]
            rcs = [c for s in R_segs if (c := fit_segment(s["polyline"], W, H, deg))]
            for lc in lcs:
                for rc in rcs:
                    cm = max(lc["y_min"], rc["y_min"])
                    cM = min(lc["y_max"], rc["y_max"])
                    if cM - cm < MIN_Y_RANGE: continue
                    ys_q  = np.linspace(cm, cM, 8)
                    lxs_q = np.clip(np.polyval(lc["coefficients"], ys_q), 0, 1)
                    rxs_q = np.clip(np.polyval(rc["coefficients"], ys_q), 0, 1)
                    sep   = rxs_q - lxs_q
                    if np.median(sep) < MIN_RAIL_SEPARATION: continue
                    if np.any(sep < 0): continue
                    if not curve_quality(lxs_q) or not curve_quality(rxs_q): continue
                    candidates.append({"lc": lc, "rc": rc, "y_min": cm, "y_max": cM, "deg": deg})
            if candidates: break

        if not candidates: rej_ego += 1; continue

        # Ego-track selection
        scored = []
        for c in candidates:
            yM = c["y_max"]
            mid_bot = (np.polyval(c["lc"]["coefficients"], yM) +
                       np.polyval(c["rc"]["coefficients"], yM)) / 2.0
            dist    = abs(mid_bot - 0.5)
            is_ego  = yM > 0.85 and 0.2 <= mid_bot <= 0.8
            scored.append(((10.0 if is_ego else 0.0) + yM * 2.0 - dist, c))
        scored.sort(key=lambda x: x[0], reverse=True)
        best = scored[0][1]

        l_xs, l_valid = sample_on_fixed_grid(best["lc"])
        r_xs, r_valid = sample_on_fixed_grid(best["rc"])
        if l_valid.sum() < MIN_POINTS or r_valid.sum() < MIN_POINTS:
            rej_ego += 1; continue

        # 96-dim target: left_x[16] + right_x[16] + left_y[16] + right_y[16]
        #              + left_valid[16] + right_valid[16]
        targets.append(np.concatenate([
            l_xs, r_xs,
            FIXED_Y_GRID, FIXED_Y_GRID,
            l_valid, r_valid
        ]).astype(np.float32))

        metadata.append({
            "image_id": img_id, "file_name": img["file_name"],
            "width": W, "height": H,
            "y_min": float(best["y_min"]), "y_max": float(best["y_max"]),
            "fit_degree": best["deg"],
            "l_valid": int(l_valid.sum()), "r_valid": int(r_valid.sum()),
        })
        if best["deg"] == 2: ok2 += 1
        else:                 ok1 += 1

    arr = np.asarray(targets, dtype=np.float32) if targets else np.empty((0, 96), np.float32)
    np.save(os.path.join(OUTPUT_DIR, f"{split}_targets.npy"), arr)
    with open(os.path.join(OUTPUT_DIR, f"{split}_metadata.json"), "w") as f:
        json.dump(metadata, f, indent=2)

    print(f"\nEgo tracks  : {len(targets)}  (deg2={ok2}, deg1={ok1})")
    print(f"Rejected    : no_rail={rej_no}  one_sided={rej_one}  no_ego={rej_ego}")
    print(f"Saved  ->  {OUTPUT_DIR}")


if __name__ == "__main__":
    process_split("train")
    process_split("val")
    print("\nDone! Dataset V5 preparation complete!")
