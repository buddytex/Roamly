"""
prepare_heatmap_dataset.py
──────────────────────────
Extracts and indexes ego-rail polylines from RailBench annotations for train and val splits.
The resulting metadata files contain normalized polyline coordinates for Left (rightRail=0)
and Right (rightRail=1) rails for dynamic spatial heatmap rendering.
"""
import os
import json
import numpy as np
from collections import defaultdict

BASE_DIR = r"C:\RailGuardAI"
ANNOTATION_DIR = os.path.join(BASE_DIR, "railbench", "annotations", "rails")
IMAGE_DIR = os.path.join(BASE_DIR, "training_images")
OUTPUT_DIR = os.path.join(BASE_DIR, "prepared_dataset_heatmap")
os.makedirs(OUTPUT_DIR, exist_ok=True)

MIN_POINTS = 4
MIN_Y_SPAN = 0.20

def process_split(split="train"):
    ann_file = os.path.join(ANNOTATION_DIR, f"annotations_{split}.json")
    print(f"\n{'='*70}\nPROCESSING {split.upper()} SPLIT (Heatmap Dataset)\n{'='*70}")
    
    if not os.path.exists(ann_file):
        print(f"Error: {ann_file} not found!")
        return

    with open(ann_file, "r", encoding="utf-8") as f:
        data = json.load(f)

    images = {img["id"]: img for img in data["images"]}
    by_image = defaultdict(list)
    for ann in data["annotations"]:
        if ann.get("category_id") == 1 and "polyline" in ann and ann.get("rightRail") in (0, 1):
            by_image[ann["image_id"]].append(ann)

    records = []
    skipped_no_image = 0
    skipped_no_rails = 0

    for img_id, anns in by_image.items():
        img = images[img_id]
        fn = os.path.basename(img["file_name"].replace("\\", "/"))
        img_path = os.path.join(IMAGE_DIR, fn)

        if not os.path.exists(img_path):
            skipped_no_image += 1
            continue

        w, h = img["width"], img["height"]

        l_segs = [a["polyline"] for a in anns if a["rightRail"] == 0 and len(a["polyline"]) >= MIN_POINTS]
        r_segs = [a["polyline"] for a in anns if a["rightRail"] == 1 and len(a["polyline"]) >= MIN_POINTS]

        if not l_segs or not r_segs:
            skipped_no_rails += 1
            continue

        # Normalize polylines to [0.0, 1.0]
        norm_l_segs = []
        for poly in l_segs:
            pts = [[round(p[0] / w, 5), round(p[1] / h, 5)] for p in poly]
            # Check Y-range
            ys = [p[1] for p in pts]
            if max(ys) - min(ys) >= MIN_Y_SPAN:
                norm_l_segs.append(pts)

        norm_r_segs = []
        for poly in r_segs:
            pts = [[round(p[0] / w, 5), round(p[1] / h, 5)] for p in poly]
            ys = [p[1] for p in pts]
            if max(ys) - min(ys) >= MIN_Y_SPAN:
                norm_r_segs.append(pts)

        if not norm_l_segs or not norm_r_segs:
            skipped_no_rails += 1
            continue

        records.append({
            "image_id": img_id,
            "file_name": fn,
            "width": w,
            "height": h,
            "left_polylines": norm_l_segs,
            "right_polylines": norm_r_segs,
        })

    out_file = os.path.join(OUTPUT_DIR, f"{split}_metadata.json")
    with open(out_file, "w", encoding="utf-8") as f:
        json.dump(records, f, indent=2)

    print(f"Successfully processed {len(records)} samples for {split}.")
    print(f"Skipped: {skipped_no_image} missing images, {skipped_no_rails} invalid rail annotations.")
    print(f"Saved: {out_file}")

if __name__ == "__main__":
    process_split("train")
    process_split("val")
    print("\nDataset preparation for Heatmap Network complete.")
