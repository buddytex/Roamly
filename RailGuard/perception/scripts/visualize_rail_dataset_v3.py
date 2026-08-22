import json
import os
import random
import zipfile

import cv2
import numpy as np


BASE_DIR = r"C:\RailGuardAI"

ZIP_FILE = r"C:\Users\Naveen\Downloads\images.zip"

DATASET_DIR = os.path.join(
    BASE_DIR,
    "prepared_dataset_v3"
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
    "v3_visualizations"
)

NUMBER_OF_SAMPLES = 30

RANDOM_SEED = 42


print("=" * 70)
print("RAIL-BENCH V3 VISUALIZATION")
print("=" * 70)


targets = np.load(TARGET_FILE)

with open(
    METADATA_FILE,
    "r",
    encoding="utf-8"
) as f:
    metadata = json.load(f)


print()
print("Targets:", targets.shape)
print("Metadata:", len(metadata))


os.makedirs(
    OUTPUT_DIR,
    exist_ok=True
)


random.seed(RANDOM_SEED)

count = min(
    NUMBER_OF_SAMPLES,
    len(metadata)
)

indices = random.sample(
    range(len(metadata)),
    count
)


print()
print("Opening images.zip...")

archive = zipfile.ZipFile(
    ZIP_FILE,
    "r"
)

archive_names = set(
    archive.namelist()
)


for output_index, dataset_index in enumerate(
    indices,
    start=1
):

    info = metadata[dataset_index]

    target = targets[dataset_index]

    image_id = info["image_id"]
    file_name = info["file_name"]

    width = info["width"]
    height = info["height"]


    # --------------------------------------------------------
    # Target layout
    #
    # 0:16   left X
    # 16:32  right X
    # 32:48  left Y
    # 48:64  right Y
    # 64:80  left validity
    # 80:96  right validity
    # --------------------------------------------------------

    left_x = target[0:16]
    right_x = target[16:32]

    left_y = target[32:48]
    right_y = target[48:64]

    left_valid = target[64:80]
    right_valid = target[80:96]


    file_name = file_name.replace(
        "\\",
        "/"
    )


    basename = os.path.basename(
        file_name
    )


    matches = [
        name
        for name in archive_names
        if name.endswith("/" + basename)
    ]


    if not matches:

        print(
            f"WARNING: Could not find {basename}"
        )

        continue


    zip_path = matches[0]


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
            f"WARNING: Could not decode {basename}"
        )

        continue


    # ========================================================
    # LEFT RAIL
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


    # ========================================================
    # RIGHT RAIL
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


    # ========================================================
    # DRAW LEFT
    # ========================================================

    if len(left_points) >= 2:

        points = np.array(
            left_points,
            dtype=np.int32
        ).reshape(
            (-1, 1, 2)
        )

        cv2.polylines(
            image,
            [points],
            False,
            (255, 0, 0),
            10,
            cv2.LINE_AA
        )


    # ========================================================
    # DRAW RIGHT
    # ========================================================

    if len(right_points) >= 2:

        points = np.array(
            right_points,
            dtype=np.int32
        ).reshape(
            (-1, 1, 2)
        )

        cv2.polylines(
            image,
            [points],
            False,
            (0, 0, 255),
            10,
            cv2.LINE_AA
        )


    # ========================================================
    # DRAW SAMPLE POINTS
    # ========================================================

    for point in left_points:

        cv2.circle(
            image,
            point,
            12,
            (255, 0, 0),
            -1
        )


    for point in right_points:

        cv2.circle(
            image,
            point,
            12,
            (0, 0, 255),
            -1
        )


    # ========================================================
    # INFO
    # ========================================================

    panel_height = 150

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
        "BLUE = LEFT",
        (25, 90),
        cv2.FONT_HERSHEY_SIMPLEX,
        0.9,
        (255, 0, 0),
        2,
        cv2.LINE_AA
    )


    cv2.putText(
        panel,
        "RED = RIGHT",
        (250, 90),
        cv2.FONT_HERSHEY_SIMPLEX,
        0.9,
        (0, 0, 255),
        2,
        cv2.LINE_AA
    )


    cv2.putText(
        panel,
        f"Coverage: "
        f"{info['y_min']:.2f} - "
        f"{info['y_max']:.2f}",
        (500, 90),
        cv2.FONT_HERSHEY_SIMPLEX,
        0.9,
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


    output_path = os.path.join(
        OUTPUT_DIR,
        f"sample_{output_index:02d}_"
        f"{basename}"
    )


    cv2.imwrite(
        output_path,
        image
    )


    print(
        f"[{output_index:02d}/{count:02d}] "
        f"{basename}"
    )


archive.close()


print()
print("=" * 70)
print("V3 VISUALIZATION COMPLETE")
print("=" * 70)

print()
print("Open:")
print(OUTPUT_DIR)