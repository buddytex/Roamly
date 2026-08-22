"""
extract_training_images.py

Extracts only the images referenced in the v3_1 train and val metadata
from images.zip to a flat directory: C:\RailGuardAI\training_images\

This avoids re-opening and re-seeking the ZIP on every training batch.
"""

import json
import os
import zipfile

BASE_DIR   = r"C:\RailGuardAI"
ZIP_FILE   = r"C:\Users\Naveen\Downloads\images.zip"
OUTPUT_DIR = os.path.join(BASE_DIR, "training_images")
DATASET_DIR = os.path.join(BASE_DIR, "prepared_dataset_v3_1")

os.makedirs(OUTPUT_DIR, exist_ok=True)

# --------------------------------------------------------
# Collect all filenames we need
# --------------------------------------------------------

needed = set()

for split in ("train", "val"):
    meta_path = os.path.join(DATASET_DIR, f"{split}_metadata.json")
    with open(meta_path, "r", encoding="utf-8") as f:
        metadata = json.load(f)

    for m in metadata:
        basename = os.path.basename(
            m["file_name"].replace("\\", "/")
        )
        needed.add(basename)

print(f"Need to extract: {len(needed)} unique images")

# --------------------------------------------------------
# Extract from ZIP
# --------------------------------------------------------

print(f"Opening: {ZIP_FILE}")

with zipfile.ZipFile(ZIP_FILE, "r") as archive:

    # Build basename -> zip_path index
    name_index = {
        os.path.basename(n): n
        for n in archive.namelist()
        if not n.endswith("/")
    }

    extracted = 0
    skipped   = 0
    already   = 0

    for basename in needed:

        dest_path = os.path.join(OUTPUT_DIR, basename)

        # Skip if already extracted
        if os.path.exists(dest_path):
            already += 1
            continue

        zip_path = name_index.get(basename)

        if zip_path is None:
            print(f"  WARNING: {basename} not found in ZIP")
            skipped += 1
            continue

        image_bytes = archive.read(zip_path)

        with open(dest_path, "wb") as f:
            f.write(image_bytes)

        extracted += 1

        if extracted % 100 == 0:
            print(f"  {extracted} extracted …")

print()
print("=" * 60)
print("EXTRACTION COMPLETE")
print("=" * 60)
print(f"  Extracted : {extracted}")
print(f"  Already   : {already}")
print(f"  Not found : {skipped}")
print(f"  Output    : {OUTPUT_DIR}")
