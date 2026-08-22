"""
create_colab_package.py
───────────────────────
Pre-resizes the 1,379 training images to 512x288 high-resolution JPEGs and packages
them together with train/val metadata and a turnkey Google Colab notebook.
Reduces dataset upload size from 24 GB to ~35 MB!
"""
import os
import json
import zipfile
import cv2
import numpy as np

BASE_DIR = r"C:\RailGuardAI"
IMAGE_DIR = os.path.join(BASE_DIR, "training_images")
DATASET_DIR = os.path.join(BASE_DIR, "prepared_dataset_heatmap")
OUT_ZIP = os.path.join(BASE_DIR, "railguard_colab_package.zip")
NOTEBOOK_PATH = os.path.join(BASE_DIR, "RailGuardAI_HeatmapNet_Colab.ipynb")

TARGET_W = 512
TARGET_H = 288

def create_package():
    print("=" * 70)
    print("CREATING HIGH-RES GOOGLE COLAB TRAINING PACKAGE (512x288)")
    print("=" * 70)

    train_meta_file = os.path.join(DATASET_DIR, "train_metadata.json")
    val_meta_file = os.path.join(DATASET_DIR, "val_metadata.json")

    with open(train_meta_file, "r") as f:
        train_meta = json.load(f)
    with open(val_meta_file, "r") as f:
        val_meta = json.load(f)

    all_files = set([m["file_name"] for m in train_meta] + [m["file_name"] for m in val_meta])
    print(f"Total unique images to package: {len(all_files)}")

    # Create Zip File
    with zipfile.ZipFile(OUT_ZIP, "w", compression=zipfile.ZIP_DEFLATED) as zf:
        # Add metadata JSONs
        zf.write(train_meta_file, "dataset/train_metadata.json")
        zf.write(val_meta_file, "dataset/val_metadata.json")

        count = 0
        for fn in sorted(all_files):
            img_path = os.path.join(IMAGE_DIR, fn)
            if os.path.exists(img_path):
                img = cv2.imread(img_path)
                if img is not None:
                    # High-quality resize to 512x288
                    resized = cv2.resize(img, (TARGET_W, TARGET_H), interpolation=cv2.INTER_AREA)
                    # Encode to high-quality JPEG
                    success, enc = cv2.imencode(".jpg", resized, [cv2.IMWRITE_JPEG_QUALITY, 95])
                    if success:
                        zf.writestr(f"dataset/images/{os.path.splitext(fn)[0]}.jpg", enc.tobytes())
                        count += 1
            if count % 200 == 0 and count > 0:
                print(f"  Processed {count}/{len(all_files)} images...")

    zip_size_mb = os.path.getsize(OUT_ZIP) / (1024 * 1024)
    print(f"\nPackage created successfully: {OUT_ZIP}")
    print(f"Total packaged images: {count}")
    print(f"Compressed package size: {zip_size_mb:.2f} MB (vs 24,000 MB original)")

if __name__ == "__main__":
    create_package()
