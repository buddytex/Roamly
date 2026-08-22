import json
import os
import numpy as np
from collections import defaultdict

BASE_DIR = r"C:\RailGuardAI"
ANNOTATION_DIR = os.path.join(BASE_DIR, "railbench", "annotations", "rails")
OUTPUT_DIR = os.path.join(BASE_DIR, "prepared_dataset_v4")
os.makedirs(OUTPUT_DIR, exist_ok=True)

# Settings matching v3_1
NUM_SAMPLES = 16
MIN_POINTS = 5
MIN_Y_RANGE = 0.20
MAX_X_JUMP = 0.18
MIN_RAIL_SEPARATION = 0.03

def remove_duplicate_points(points):
    if len(points) <= 1:
        return points
    rounded = np.round(points, decimals=5)
    _, indices = np.unique(rounded, axis=0, return_index=True)
    return points[np.sort(indices)]

def fit_individual_segment(polyline, width, height, degree):
    if len(polyline) < MIN_POINTS:
        return None
    
    # Normalize points
    norm_pts = []
    for x, y in polyline:
        norm_pts.append((float(x) / width, float(y) / height))
    norm_pts = np.asarray(norm_pts, dtype=np.float32)
    
    # Dedup
    norm_pts = remove_duplicate_points(norm_pts)
    if len(norm_pts) < MIN_POINTS:
        return None
        
    y = norm_pts[:, 1]
    x = norm_pts[:, 0]
    
    y_min = float(np.min(y))
    y_max = float(np.max(y))
    
    if y_max - y_min < MIN_Y_RANGE:
        return None
        
    # Sort by vertical position
    order = np.argsort(y)
    y = y[order]
    x = x[order]
    
    # Remove duplicate Y values
    unique_y, unique_idx = np.unique(y, return_index=True)
    x = x[unique_idx]
    y = unique_y
    
    if len(y) < MIN_POINTS:
        return None
        
    try:
        coefficients = np.polyfit(y, x, degree)
        return {
            "coefficients": coefficients,
            "y_min": y_min,
            "y_max": y_max,
            "degree": degree
        }
    except Exception:
        return None

def curve_quality(xs):
    if len(xs) < 2:
        return False
    jumps = np.abs(np.diff(xs))
    if np.max(jumps) > MAX_X_JUMP:
        return False
    return True

def process_split(split):
    annotation_file = os.path.join(ANNOTATION_DIR, f"annotations_{split}.json")
    print("\n" + "=" * 70)
    print(f"PROCESSING {split.upper()} SPLIT (EGO-TRACK PAIRING)")
    print("=" * 70)
    print(f"Loading: {annotation_file}")
    
    with open(annotation_file, "r", encoding="utf-8") as f:
        data = json.load(f)
        
    images = {img["id"]: img for img in data["images"]}
    
    # Group annotations by image
    annotations_by_image = defaultdict(list)
    ignored = 0
    for ann in data["annotations"]:
        if ann.get("category_id") != 1:
            ignored += 1
            continue
        if "polyline" not in ann:
            ignored += 1
            continue
        if ann.get("rightRail") not in (0, 1):
            ignored += 1
            continue
        annotations_by_image[ann["image_id"]].append(ann)
        
    targets = []
    metadata = []
    
    valid_degree2 = 0
    valid_degree1 = 0
    rejected_no_rail = 0
    rejected_one_sided = 0
    rejected_no_ego_track = 0
    
    for img_id, anns in annotations_by_image.items():
        img = images[img_id]
        width, height = img["width"], img["height"]
        file_name = img["file_name"]
        
        left_segs = [a for a in anns if a.get("rightRail") == 0]
        right_segs = [a for a in anns if a.get("rightRail") == 1]
        
        if not left_segs and not right_segs:
            rejected_no_rail += 1
            continue
            
        if not left_segs or not right_segs:
            rejected_one_sided += 1
            continue
            
        # Fit candidates
        candidates = []
        for degree in [2, 1]:  # Try Degree-2 first, then Fallback Degree-1
            left_curves = []
            for l_idx, seg in enumerate(left_segs):
                c = fit_individual_segment(seg["polyline"], width, height, degree)
                if c is not None:
                    left_curves.append(c)
            
            right_curves = []
            for r_idx, seg in enumerate(right_segs):
                c = fit_individual_segment(seg["polyline"], width, height, degree)
                if c is not None:
                    right_curves.append(c)
                    
            # Pair left and right curves
            for l_c in left_curves:
                for r_c in right_curves:
                    common_min = max(l_c["y_min"], r_c["y_min"])
                    common_max = min(l_c["y_max"], r_c["y_max"])
                    
                    if common_max - common_min < MIN_Y_RANGE:
                        continue
                        
                    ys = np.linspace(common_min, common_max, NUM_SAMPLES)
                    l_xs = np.clip(np.polyval(l_c["coefficients"], ys), 0.0, 1.0)
                    r_xs = np.clip(np.polyval(r_c["coefficients"], ys), 0.0, 1.0)
                    
                    separation = r_xs - l_xs
                    min_sep = np.min(separation)
                    median_sep = np.median(separation)
                    crossings = np.sum(separation < 0)
                    
                    # Track quality checks
                    if median_sep >= MIN_RAIL_SEPARATION and crossings == 0 and curve_quality(l_xs) and curve_quality(r_xs):
                        candidates.append({
                            "l_xs": l_xs,
                            "r_xs": r_xs,
                            "ys": ys,
                            "y_min": common_min,
                            "y_max": common_max,
                            "degree": degree
                        })
            
            if candidates:
                break # If we found valid tracks with Degree-2, don't try Degree-1 fallback.
                
        if not candidates:
            rejected_no_ego_track += 1
            continue
            
        # Ego selection ranking:
        # 1. Prioritize tracks reaching the bottom of the image (y_max > 0.85)
        # 2. Prioritize tracks where the midpoint at the bottom is close to center (x=0.5)
        # 3. Prioritize tracks with larger Y range
        scored = []
        for c in candidates:
            y_max = c["y_max"]
            y_min = c["y_min"]
            y_range = y_max - y_min
            midpoint_bottom = (c["l_xs"][-1] + c["r_xs"][-1]) / 2.0
            dist_to_center = abs(midpoint_bottom - 0.5)
            
            is_ego_pos = y_max > 0.85 and (0.2 <= midpoint_bottom <= 0.8)
            score = (10.0 if is_ego_pos else 0.0) + (y_max * 2.0) - dist_to_center
            scored.append((score, c))
            
        scored.sort(key=lambda x: x[0], reverse=True)
        best_c = scored[0][1]
        
        if best_c["degree"] == 2:
            valid_degree2 += 1
        else:
            valid_degree1 += 1
            
        target = np.concatenate([
            best_c["l_xs"],
            best_c["r_xs"],
            best_c["ys"],
            best_c["ys"],
            np.ones(NUM_SAMPLES),
            np.ones(NUM_SAMPLES)
        ]).astype(np.float32)
        
        targets.append(target)
        metadata.append({
            "image_id": img_id,
            "file_name": file_name,
            "width": width,
            "height": height,
            "left_segments": len(left_segs),
            "right_segments": len(right_segs),
            "y_min": float(best_c["y_min"]),
            "y_max": float(best_c["y_max"]),
            "fit_degree": best_c["degree"]
        })
        
    # Save target files
    if targets:
        targets_array = np.asarray(targets, dtype=np.float32)
    else:
        targets_array = np.empty((0, 96), dtype=np.float32)
        
    target_path = os.path.join(OUTPUT_DIR, f"{split}_targets.npy")
    metadata_path = os.path.join(OUTPUT_DIR, f"{split}_metadata.json")
    
    np.save(target_path, targets_array)
    with open(metadata_path, "w", encoding="utf-8") as f:
        json.dump(metadata, f, indent=2)
        
    print()
    print("RESULTS")
    print("-" * 70)
    print(f"Total Images processed: {len(images)}")
    print(f"Ego tracks prepared   : {len(targets)}  (Degree-2: {valid_degree2}, Degree-1 fallback: {valid_degree1})")
    print(f"Rejected:")
    print(f"    No rail           : {rejected_no_rail}")
    print(f"    One-sided rail    : {rejected_one_sided}")
    print(f"    No valid ego-track: {rejected_no_ego_track}")
    print(f"Saved targets to      : {target_path}")
    print(f"Saved metadata to     : {metadata_path}")

if __name__ == "__main__":
    process_split("train")
    process_split("val")
    print("\nDataset preparation completed successfully!")
