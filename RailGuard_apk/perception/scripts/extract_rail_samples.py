import json
import os
import zipfile
import random

import cv2
import numpy as np


# ============================================================
# PATHS
# ============================================================

ZIP_FILE = r"C:\Users\Naveen\Downloads\images.zip"

ANNOTATION_FILE = (
    r"C:\RailGuardAI\railbench\annotations"
    r"\rails\annotations_train.json"
)

OUTPUT_DIR = r"C:\RailGuardAI\rail_samples"

EXTRACTED_DIR = os.path.join(
    OUTPUT_DIR,
    "images"
)

VISUALIZATION_DIR = os.path.join(
    OUTPUT_DIR,
    "visualizations"
)


# ============================================================
# SETTINGS
# ============================================================

NUMBER_OF_SAMPLES = 20

RANDOM_SEED = 42


# ============================================================
# LOAD ANNOTATIONS
# ============================================================

def load_annotations():

    print("Loading annotations...")

    with open(
        ANNOTATION_FILE,
        "r",
        encoding="utf-8"
    ) as f:

        data = json.load(f)

    print(
        f"Loaded {len(data['images'])} images"
    )

    print(
        f"Loaded {len(data['annotations'])} annotations"
    )

    return data


# ============================================================
# GROUP ANNOTATIONS BY IMAGE
# ============================================================

def group_annotations(data):

    grouped = {}

    for annotation in data["annotations"]:

        image_id = annotation["image_id"]

        if image_id not in grouped:
            grouped[image_id] = []

        grouped[image_id].append(
            annotation
        )

    return grouped


# ============================================================
# SELECT SAMPLE IMAGES
# ============================================================

def select_samples(
    data,
    annotations_by_image
):

    candidates = []

    for image in data["images"]:

        image_id = image["id"]

        annotations = annotations_by_image.get(
            image_id,
            []
        )

        rails = [
            a for a in annotations
            if a["category_id"] == 1
        ]

        # We want images that actually contain rails.
        if len(rails) > 0:

            candidates.append(image)

    print(
        f"Images containing rails: "
        f"{len(candidates)}"
    )

    random.seed(RANDOM_SEED)

    number = min(
        NUMBER_OF_SAMPLES,
        len(candidates)
    )

    selected = random.sample(
        candidates,
        number
    )

    return selected


# ============================================================
# DRAW RAIL ANNOTATIONS
# ============================================================

def draw_annotations(
    image,
    annotations
):

    rail_count = 0

    for annotation in annotations:

        # Only process rail annotations.
        if annotation["category_id"] != 1:
            continue

        points = annotation.get(
            "polyline",
            []
        )

        if len(points) < 2:
            continue

        right_rail = (
            annotation.get(
                "rightRail",
                0
            ) == 1
        )

        # ----------------------------------------------------
        # OpenCV uses BGR.
        #
        # Left rail  = BLUE
        # Right rail = RED
        # ----------------------------------------------------

        if right_rail:

            color = (
                0,
                0,
                255
            )

            label = "RIGHT"

        else:

            color = (
                255,
                0,
                0
            )

            label = "LEFT"

        # Convert points to OpenCV format.

        pts = np.array(
            points,
            dtype=np.int32
        )

        pts = pts.reshape(
            (-1, 1, 2)
        )

        # Draw polyline.

        cv2.polylines(
            image,
            [pts],
            False,
            color,
            5,
            cv2.LINE_AA
        )

        # ----------------------------------------------------
        # Draw annotation label.
        # ----------------------------------------------------

        x, y = map(
            int,
            points[0]
        )

        cv2.putText(
            image,
            f"{label} #{rail_count}",
            (x, y),
            cv2.FONT_HERSHEY_SIMPLEX,
            1.0,
            color,
            2,
            cv2.LINE_AA
        )

        rail_count += 1

    return image, rail_count


# ============================================================
# MAIN
# ============================================================

def main():

    print()
    print("=" * 70)
    print("RAIL-BENCH SAMPLE EXTRACTOR")
    print("=" * 70)

    # --------------------------------------------------------
    # Check files.
    # --------------------------------------------------------

    if not os.path.exists(ZIP_FILE):

        raise FileNotFoundError(
            f"ZIP not found:\n{ZIP_FILE}"
        )

    if not os.path.exists(
        ANNOTATION_FILE
    ):

        raise FileNotFoundError(
            "Annotation file not found:\n"
            f"{ANNOTATION_FILE}"
        )

    # --------------------------------------------------------
    # Create directories.
    # --------------------------------------------------------

    os.makedirs(
        EXTRACTED_DIR,
        exist_ok=True
    )

    os.makedirs(
        VISUALIZATION_DIR,
        exist_ok=True
    )

    # --------------------------------------------------------
    # Load annotation data.
    # --------------------------------------------------------

    data = load_annotations()

    annotations_by_image = (
        group_annotations(data)
    )

    # --------------------------------------------------------
    # Select samples.
    # --------------------------------------------------------

    selected_images = select_samples(
        data,
        annotations_by_image
    )

    print()
    print(
        f"Selected "
        f"{len(selected_images)} samples."
    )

    # --------------------------------------------------------
    # Open ZIP.
    # --------------------------------------------------------

    print()
    print("Opening images.zip...")

    with zipfile.ZipFile(
        ZIP_FILE,
        "r"
    ) as archive:

        for index, image_info in enumerate(
            selected_images,
            start=1
        ):

            image_id = image_info["id"]

            file_name = image_info[
                "file_name"
            ]

            # ------------------------------------------------
            # RAIL-BENCH annotation filenames may be stored
            # relative to the dataset.
            #
            # Normalize separators.
            # ------------------------------------------------

            file_name = file_name.replace(
                "\\",
                "/"
            )

            # The ZIP uses:
            #
            # images/train/img_xxx_train.png
            #
            # If the annotation only gives:
            #
            # img_xxx_train.png
            #
            # construct the ZIP path.
            # ------------------------------------------------

            if file_name.startswith(
                "images/"
            ):

                zip_path = file_name

            else:

                zip_path = (
                    "images/train/"
                    + file_name
                )

            # ------------------------------------------------
            # Verify file exists in archive.
            # ------------------------------------------------

            if zip_path not in archive.namelist():

                # Try basename as fallback.

                basename = os.path.basename(
                    file_name
                )

                possible = [
                    name
                    for name in archive.namelist()
                    if name.endswith(
                        "/" + basename
                    )
                ]

                if len(possible) == 0:

                    print()
                    print(
                        "WARNING: Image not found:"
                    )

                    print(
                        zip_path
                    )

                    continue

                zip_path = possible[0]

            # ------------------------------------------------
            # Extract only this image.
            # ------------------------------------------------

            image_bytes = archive.read(
                zip_path
            )

            output_filename = os.path.basename(
                zip_path
            )

            output_path = os.path.join(
                EXTRACTED_DIR,
                output_filename
            )

            with open(
                output_path,
                "wb"
            ) as f:

                f.write(
                    image_bytes
                )

            # ------------------------------------------------
            # Load image.
            # ------------------------------------------------

            image = cv2.imread(
                output_path
            )

            if image is None:

                print(
                    "WARNING: Could not read:",
                    output_path
                )

                continue

            # ------------------------------------------------
            # Get annotations.
            # ------------------------------------------------

            annotations = (
                annotations_by_image.get(
                    image_id,
                    []
                )
            )

            # ------------------------------------------------
            # Draw annotations.
            # ------------------------------------------------

            annotated_image, rail_count = (
                draw_annotations(
                    image,
                    annotations
                )
            )

            # ------------------------------------------------
            # Add information panel.
            # ------------------------------------------------

            height, width = (
                annotated_image.shape[:2]
            )

            panel_height = 100

            panel = np.zeros(
                (
                    panel_height,
                    width,
                    3
                ),
                dtype=np.uint8
            )

            cv2.putText(
                panel,
                f"Image ID: {image_id}",
                (20, 35),
                cv2.FONT_HERSHEY_SIMPLEX,
                1.0,
                (255, 255, 255),
                2,
                cv2.LINE_AA
            )

            cv2.putText(
                panel,
                f"Rail segments: {rail_count}",
                (20, 75),
                cv2.FONT_HERSHEY_SIMPLEX,
                1.0,
                (255, 255, 255),
                2,
                cv2.LINE_AA
            )

            annotated_image = np.vstack(
                [
                    panel,
                    annotated_image
                ]
            )

            # ------------------------------------------------
            # Save visualization.
            # ------------------------------------------------

            visualization_path = os.path.join(
                VISUALIZATION_DIR,
                f"sample_{index:02d}_{output_filename}"
            )

            cv2.imwrite(
                visualization_path,
                annotated_image
            )

            print(
                f"[{index:02d}/"
                f"{len(selected_images):02d}] "
                f"{output_filename} "
                f"-> {rail_count} rail segments"
            )

    print()
    print("=" * 70)
    print("DONE")
    print("=" * 70)

    print()
    print(
        "Extracted images:"
    )

    print(
        EXTRACTED_DIR
    )

    print()
    print(
        "Annotated visualizations:"
    )

    print(
        VISUALIZATION_DIR
    )


if __name__ == "__main__":
    main()