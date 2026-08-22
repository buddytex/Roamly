import json
import os
import random

import cv2
import numpy as np


# ============================================================
# PATHS
# ============================================================

BASE_DIR = r"C:\RailGuardAI"

ZIP_FILE = r"C:\Users\Naveen\Downloads\images.zip"

DATASET_DIR = os.path.join(
    BASE_DIR,
    "prepared_dataset_v2"
)

TARGET_FILE = os.path.join(
    DATASET_DIR,
    "train_targets.npy"
)

METADATA_FILE = os.path.join(
    DATASET_DIR,
    "train_metadata.json"
)

OUTPUT_DIR = os.path.join(
    BASE_DIR,
    "rail_samples",
    "v2_visualizations"
)


# ============================================================
# SETTINGS
# ============================================================

NUMBER_OF_SAMPLES = 20

RANDOM_SEED = 42


# ============================================================
# LOAD DATA
# ============================================================

print("=" * 70)
print("RAIL-BENCH V2 VISUALIZATION")
print("=" * 70)

print()
print("Loading targets...")

targets = np.load(
    TARGET_FILE
)

with open(
    METADATA_FILE,
    "r",
    encoding="utf-8"
) as f:

    metadata = json.load(f)


print(
    f"Targets: {targets.shape}"
)

print(
    f"Metadata: {len(metadata)} images"
)


# ============================================================
# CREATE OUTPUT DIRECTORY
# ============================================================

os.makedirs(
    OUTPUT_DIR,
    exist_ok=True
)


# ============================================================
# SELECT SAMPLES
# ============================================================

random.seed(
    RANDOM_SEED
)

count = min(
    NUMBER_OF_SAMPLES,
    len(metadata)
)

indices = random.sample(
    range(len(metadata)),
    count
)


# ============================================================
# OPEN ZIP
# ============================================================

import zipfile

print()
print("Opening images.zip...")

archive = zipfile.ZipFile(
    ZIP_FILE,
    "r"
)

archive_names = set(
    archive.namelist()
)


# ============================================================
# PROCESS
# ============================================================

for output_index, dataset_index in enumerate(
    indices,
    start=1
):

    info = metadata[
        dataset_index
    ]

    target = targets[
        dataset_index
    ]

    image_id = info[
        "image_id"
    ]

    file_name = info[
        "file_name"
    ]

    width = info[
        "width"
    ]

    height = info[
        "height"
    ]


    # --------------------------------------------------------
    # Target layout
    #
    # [0:16]    left X
    # [16:32]   right X
    # [32:48]   left Y
    # [48:64]   right Y
    # [64:80]   left valid
    # [80:96]   right valid
    # --------------------------------------------------------

    left_x = target[
        0:16
    ]

    right_x = target[
        16:32
    ]

    left_y = target[
        32:48
    ]

    right_y = target[
        48:64
    ]

    left_valid = target[
        64:80
    ]

    right_valid = target[
        80:96
    ]


    # --------------------------------------------------------
    # Find ZIP path
    # --------------------------------------------------------

    file_name = file_name.replace(
        "\\",
        "/"
    )

    if file_name.startswith(
        "images/"
    ):

        zip_path = file_name

    else:

        # Training image
        zip_path = (
            "images/train/"
            + os.path.basename(
                file_name
            )
        )


    if zip_path not in archive_names:

        basename = os.path.basename(
            file_name
        )

        matches = [
            name
            for name in archive_names
            if name.endswith(
                "/" + basename
            )
        ]

        if not matches:

            print(
                f"WARNING: "
                f"Could not find {file_name}"
            )

            continue

        zip_path = matches[0]


    # --------------------------------------------------------
    # Read image directly from ZIP
    # --------------------------------------------------------

    image_bytes = archive.read(
        zip_path
    )

    image_array = np.frombuffer(
        image_bytes,
        dtype=np.uint8
    )

    image = cv2.imdecode(
        image_array,
        cv2.IMREAD_COLOR
    )

    if image is None:

        print(
            f"WARNING: "
            f"Could not decode {file_name}"
        )

        continue


    # ========================================================
    # DRAW LEFT RAIL
    # ========================================================

    left_points = []

    for x, y, valid in zip(
        left_x,
        left_y,
        left_valid
    ):

        if valid < 0.5:
            continue

        px = int(
            x * width
        )

        py = int(
            y * height
        )

        left_points.append(
            (px, py)
        )


    if len(left_points) >= 2:

        left_array = np.array(
            left_points,
            dtype=np.int32
        ).reshape(
            (-1, 1, 2)
        )

        cv2.polylines(
            image,
            [left_array],
            False,
            (255, 0, 0),
            8,
            cv2.LINE_AA
        )


    # ========================================================
    # DRAW RIGHT RAIL
    # ========================================================

    right_points = []

    for x, y, valid in zip(
        right_x,
        right_y,
        right_valid
    ):

        if valid < 0.5:
            continue

        px = int(
            x * width
        )

        py = int(
            y * height
        )

        right_points.append(
            (px, py)
        )


    if len(right_points) >= 2:

        right_array = np.array(
            right_points,
            dtype=np.int32
        ).reshape(
            (-1, 1, 2)
        )

        cv2.polylines(
            image,
            [right_array],
            False,
            (0, 0, 255),
            8,
            cv2.LINE_AA
        )


    # ========================================================
    # DRAW SAMPLE POINTS
    # ========================================================

    for point in left_points:

        cv2.circle(
            image,
            point,
            10,
            (255, 0, 0),
            -1
        )


    for point in right_points:

        cv2.circle(
            image,
            point,
            10,
            (0, 0, 255),
            -1
        )


    # ========================================================
    # INFORMATION PANEL
    # ========================================================

    panel_height = 140

    panel = np.zeros(
        (
            panel_height,
            image.shape[1],
            3
        ),
        dtype=np.uint8
    )


    cv2.putText(
        panel,
        f"Image ID: {image_id}",
        (25, 40),
        cv2.FONT_HERSHEY_SIMPLEX,
        1.0,
        (255, 255, 255),
        2,
        cv2.LINE_AA
    )


    cv2.putText(
        panel,
        "BLUE = LEFT RAIL",
        (25, 85),
        cv2.FONT_HERSHEY_SIMPLEX,
        0.9,
        (255, 0, 0),
        2,
        cv2.LINE_AA
    )


    cv2.putText(
        panel,
        "RED = RIGHT RAIL",
        (300, 85),
        cv2.FONT_HERSHEY_SIMPLEX,
        0.9,
        (0, 0, 255),
        2,
        cv2.LINE_AA
    )


    cv2.putText(
        panel,
        f"Left points: {len(left_points)}   "
        f"Right points: {len(right_points)}",
        (25, 125),
        cv2.FONT_HERSHEY_SIMPLEX,
        0.8,
        (255, 255, 255),
        2,
        cv2.LINE_AA
    )


    image = np.vstack(
        [
            panel,
            image
        ]
    )


    # ========================================================
    # SAVE
    # ========================================================

    output_path = os.path.join(
        OUTPUT_DIR,
        f"sample_{output_index:02d}_"
        f"{os.path.basename(file_name)}"
    )

    cv2.imwrite(
        output_path,
        image
    )


    print(
        f"[{output_index:02d}/{count:02d}] "
        f"{os.path.basename(file_name)}"
    )


# ============================================================
# CLOSE ZIP
# ============================================================

archive.close()


print()
print("=" * 70)
print("VISUALIZATION COMPLETE")
print("=" * 70)

print()
print(
    "Open:"
)

print(
    OUTPUT_DIR
)
