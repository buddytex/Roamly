import json
import os
import zipfile

import cv2
import numpy as np


BASE_DIR = r"C:\RailGuardAI"

ZIP_FILE = r"C:\Users\Naveen\Downloads\images.zip"

ANNOTATION_DIR = os.path.join(BASE_DIR, "railbench", "annotations", "rails")

REPORT_DIR = os.path.join(BASE_DIR, "prepared_dataset_v3_diagnostic")

OUTPUT_DIR = os.path.join(BASE_DIR, "rail_samples", "rejection_visualizations")

# Which split + category to inspect. Change these two lines to look
# at a different bucket (e.g. "val" / "insufficient_separation").
SPLIT = "train"
CATEGORY = "too_many_crossings"

NUMBER_OF_SAMPLES = 12

# Distinct BGR colors to cycle through per segment, so overlapping
# segments belonging to different physical rails/tracks are visible
SEGMENT_COLORS = [
    (255, 0, 0),     # blue
    (0, 0, 255),     # red
    (0, 255, 0),     # green
    (0, 255, 255),   # yellow
    (255, 0, 255),   # magenta
    (255, 255, 0),   # cyan
    (128, 0, 255),   # pink/purple
    (0, 128, 255),   # orange
]


print("=" * 70)
print(f"VISUALIZING REJECTED SAMPLES: {SPLIT.upper()} / {CATEGORY}")
print("=" * 70)


report_path = os.path.join(REPORT_DIR, f"{SPLIT}_rejection_report.json")

with open(report_path, "r", encoding="utf-8") as f:
    report = json.load(f)

examples = report["examples"].get(CATEGORY, [])

if not examples:
    print(f"No examples found for category '{CATEGORY}' in {report_path}")
    raise SystemExit(0)

print(f"Found {len(examples)} saved examples, using up to {NUMBER_OF_SAMPLES}")

examples = examples[:NUMBER_OF_SAMPLES]
target_image_ids = {e["image_id"] for e in examples}


annotation_file = os.path.join(ANNOTATION_DIR, f"annotations_{SPLIT}.json")

with open(annotation_file, "r", encoding="utf-8") as f:
    data = json.load(f)

images_by_id = {image["id"]: image for image in data["images"]}

from collections import defaultdict
annotations_by_image = defaultdict(list)

for annotation in data["annotations"]:
    if annotation.get("category_id") != 1:
        continue
    if "polyline" not in annotation:
        continue
    if annotation.get("rightRail") not in (0, 1):
        continue
    if annotation["image_id"] in target_image_ids:
        annotations_by_image[annotation["image_id"]].append(annotation)


os.makedirs(OUTPUT_DIR, exist_ok=True)

print()
print("Opening images.zip...")

archive = zipfile.ZipFile(ZIP_FILE, "r")
archive_names = set(archive.namelist())


for output_index, example in enumerate(examples, start=1):

    image_id = example["image_id"]
    file_name = example["file_name"].replace("\\", "/")
    basename = os.path.basename(file_name)

    image_info = images_by_id.get(image_id)
    if image_info is None:
        print(f"WARNING: image_id {image_id} not found in annotations")
        continue

    matches = [n for n in archive_names if n.endswith("/" + basename)]

    if not matches:
        print(f"WARNING: Could not find {basename}")
        continue

    image_bytes = archive.read(matches[0])
    image_array = np.frombuffer(image_bytes, dtype=np.uint8)
    image = cv2.imdecode(image_array, cv2.IMREAD_COLOR)

    if image is None:
        print(f"WARNING: Could not decode {basename}")
        continue

    annotations = annotations_by_image.get(image_id, [])

    left_segs = [a for a in annotations if a.get("rightRail") == 0]
    right_segs = [a for a in annotations if a.get("rightRail") == 1]

    # Draw LEFT segments as solid lines, thick
    for i, seg in enumerate(left_segs):
        polyline = seg.get("polyline")
        if not polyline or len(polyline) < 2:
            continue
        pts = np.array(
            [[int(x), int(y)] for x, y in polyline],
            dtype=np.int32
        ).reshape((-1, 1, 2))
        color = SEGMENT_COLORS[i % len(SEGMENT_COLORS)]
        cv2.polylines(image, [pts], False, color, 6, cv2.LINE_AA)
        cv2.putText(
            image, f"L{i}", tuple(pts[0][0]),
            cv2.FONT_HERSHEY_SIMPLEX, 1.0, color, 3, cv2.LINE_AA
        )

    # Draw RIGHT segments as dashed-looking (thinner) lines
    for i, seg in enumerate(right_segs):
        polyline = seg.get("polyline")
        if not polyline or len(polyline) < 2:
            continue
        pts = np.array(
            [[int(x), int(y)] for x, y in polyline],
            dtype=np.int32
        ).reshape((-1, 1, 2))
        color = SEGMENT_COLORS[i % len(SEGMENT_COLORS)]
        cv2.polylines(image, [pts], False, color, 2, cv2.LINE_AA)
        cv2.putText(
            image, f"R{i}", tuple(pts[0][0]),
            cv2.FONT_HERSHEY_SIMPLEX, 1.0, color, 3, cv2.LINE_AA
        )

    panel_height = 90
    panel = np.zeros((panel_height, image.shape[1], 3), dtype=np.uint8)

    cv2.putText(
        panel, f"ID:{image_id}  L_segs={len(left_segs)} R_segs={len(right_segs)}  "
        f"(thick=LEFT, thin=RIGHT, each color = one segment)",
        (20, 50), cv2.FONT_HERSHEY_SIMPLEX, 0.8, (255, 255, 255), 2, cv2.LINE_AA
    )

    image = np.vstack([panel, image])

    output_path = os.path.join(
        OUTPUT_DIR, f"{SPLIT}_{CATEGORY}_{output_index:02d}_{basename}"
    )
    cv2.imwrite(output_path, image)

    print(f"[{output_index:02d}/{len(examples):02d}] {basename} -> saved")


archive.close()

print()
print("=" * 70)
print("DONE")
print("=" * 70)
print(f"Open: {OUTPUT_DIR}")