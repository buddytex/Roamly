import json
import os
from collections import defaultdict

import numpy as np


# ============================================================
# PATHS
# ============================================================

BASE_DIR = r"C:\RailGuardAI"

ANNOTATION_DIR = os.path.join(
    BASE_DIR,
    "railbench",
    "annotations",
    "rails"
)

TRAIN_JSON = os.path.join(
    ANNOTATION_DIR,
    "annotations_train.json"
)

VAL_JSON = os.path.join(
    ANNOTATION_DIR,
    "annotations_val.json"
)

OUTPUT_DIR = os.path.join(
    BASE_DIR,
    "prepared_dataset"
)


# ============================================================
# SETTINGS
# ============================================================

# Fixed vertical positions at which the model will predict
# the horizontal position of each rail.

Y_SAMPLES = np.array(
    [
        0.10,
        0.15,
        0.20,
        0.25,
        0.30,
        0.35,
        0.40,
        0.45,
        0.50,
        0.55,
        0.60,
        0.65,
        0.70,
        0.75,
        0.82,
        0.90,
    ],
    dtype=np.float32
)


# ============================================================
# LOAD JSON
# ============================================================

def load_json(path):

    print(f"Loading: {path}")

    with open(
        path,
        "r",
        encoding="utf-8"
    ) as f:

        return json.load(f)


# ============================================================
# GROUP RAIL ANNOTATIONS BY IMAGE
# ============================================================

def group_rail_annotations(data):

    grouped = defaultdict(
        lambda: {
            "left": [],
            "right": []
        }
    )

    ignored = 0

    for annotation in data["annotations"]:

        # ----------------------------------------------------
        # Only category 1 is a rail.
        # ----------------------------------------------------

        if annotation.get("category_id") != 1:

            ignored += 1

            continue

        polyline = annotation.get(
            "polyline"
        )

        if not polyline:

            continue

        # ----------------------------------------------------
        # rightRail:
        #
        # 0 = left
        # 1 = right
        # ----------------------------------------------------

        side = annotation.get(
            "rightRail"
        )

        if side == 0:

            grouped[
                annotation["image_id"]
            ]["left"].append(
                annotation
            )

        elif side == 1:

            grouped[
                annotation["image_id"]
            ]["right"].append(
                annotation
            )

    print(
        f"Ignored non-rail annotations: "
        f"{ignored}"
    )

    return grouped


# ============================================================
# COLLECT POINTS
# ============================================================

def collect_points(segments):

    points = []

    for segment in segments:

        polyline = segment.get(
            "polyline",
            []
        )

        for point in polyline:

            if len(point) != 2:
                continue

            x = float(point[0])
            y = float(point[1])

            points.append(
                (x, y)
            )

    return points


# ============================================================
# INTERPOLATE RAIL
# ============================================================

def interpolate_rail(
    segments,
    width,
    height
):
    """
    Convert multiple rail segments into a single
    normalized rail curve.

    Output:
        [x1, x2, ..., x16]

    where every x corresponds to Y_SAMPLES.
    """

    points = collect_points(
        segments
    )

    if len(points) < 2:

        return None

    # --------------------------------------------------------
    # Normalize coordinates.
    # --------------------------------------------------------

    normalized = []

    for x, y in points:

        nx = x / width
        ny = y / height

        nx = np.clip(
            nx,
            0.0,
            1.0
        )

        ny = np.clip(
            ny,
            0.0,
            1.0
        )

        normalized.append(
            (nx, ny)
        )

    # --------------------------------------------------------
    # Multiple annotations can contain duplicate/overlapping
    # points. Combine nearby points by Y.
    # --------------------------------------------------------

    normalized.sort(
        key=lambda p: p[1]
    )

    ys = np.array(
        [p[1] for p in normalized],
        dtype=np.float32
    )

    xs = np.array(
        [p[0] for p in normalized],
        dtype=np.float32
    )

    # --------------------------------------------------------
    # Remove exact duplicate Y values by averaging X.
    # --------------------------------------------------------

    unique_y = []
    unique_x = []

    i = 0

    while i < len(ys):

        current_y = ys[i]

        same_y = np.abs(
            ys - current_y
        ) < 0.002

        indices = np.where(
            same_y
        )[0]

        indices = indices[
            indices >= i
        ]

        if len(indices) == 0:

            i += 1

            continue

        last_index = int(
            indices[-1]
        )

        values = xs[
            i:last_index + 1
        ]

        unique_y.append(
            current_y
        )

        unique_x.append(
            float(
                np.mean(values)
            )
        )

        i = last_index + 1

    if len(unique_y) < 2:

        return None

    unique_y = np.array(
        unique_y,
        dtype=np.float32
    )

    unique_x = np.array(
        unique_x,
        dtype=np.float32
    )

    # --------------------------------------------------------
    # Sort again.
    # --------------------------------------------------------

    order = np.argsort(
        unique_y
    )

    unique_y = unique_y[
        order
    ]

    unique_x = unique_x[
        order
    ]

    # --------------------------------------------------------
    # Remove pathological cases where X jumps wildly.
    #
    # We don't delete individual points here because the
    # dataset itself contains curved rails.
    # --------------------------------------------------------

    # --------------------------------------------------------
    # Determine valid Y range.
    # --------------------------------------------------------

    min_y = float(
        unique_y.min()
    )

    max_y = float(
        unique_y.max()
    )

    # We need enough vertical coverage.
    if max_y - min_y < 0.20:

        return None

    # --------------------------------------------------------
    # Only sample within the annotated range.
    # --------------------------------------------------------

    valid_mask = (
        (Y_SAMPLES >= min_y) &
        (Y_SAMPLES <= max_y)
    )

    if np.sum(valid_mask) < 8:

        return None

    # --------------------------------------------------------
    # Interpolate X for each Y.
    # --------------------------------------------------------

    result = np.full(
        len(Y_SAMPLES),
        -1.0,
        dtype=np.float32
    )

    result[valid_mask] = np.interp(
        Y_SAMPLES[valid_mask],
        unique_y,
        unique_x
    )

    return result


# ============================================================
# PROCESS SPLIT
# ============================================================

def process_split(
    json_path,
    split_name
):

    print()
    print("=" * 70)
    print(
        f"PROCESSING {split_name.upper()}"
    )
    print("=" * 70)

    data = load_json(
        json_path
    )

    grouped = group_rail_annotations(
        data
    )

    # --------------------------------------------------------
    # Image lookup
    # --------------------------------------------------------

    images_by_id = {
        image["id"]: image
        for image in data["images"]
    }

    valid_samples = []

    one_sided = 0
    no_rail = 0
    invalid_geometry = 0

    # --------------------------------------------------------
    # Process every image.
    # --------------------------------------------------------

    for image_id, image in images_by_id.items():

        rails = grouped.get(
            image_id,
            {
                "left": [],
                "right": []
            }
        )

        left_segments = rails[
            "left"
        ]

        right_segments = rails[
            "right"
        ]

        # ----------------------------------------------------
        # No rail
        # ----------------------------------------------------

        if (
            len(left_segments) == 0 and
            len(right_segments) == 0
        ):

            no_rail += 1

            continue

        # ----------------------------------------------------
        # One-sided
        # ----------------------------------------------------

        if (
            len(left_segments) == 0 or
            len(right_segments) == 0
        ):

            one_sided += 1

            continue

        width = image.get(
            "width"
        )

        height = image.get(
            "height"
        )

        if not width or not height:

            invalid_geometry += 1

            continue

        # ----------------------------------------------------
        # Reconstruct rails
        # ----------------------------------------------------

        left = interpolate_rail(
            left_segments,
            width,
            height
        )

        right = interpolate_rail(
            right_segments,
            width,
            height
        )

        if (
            left is None or
            right is None
        ):

            invalid_geometry += 1

            continue

        # ----------------------------------------------------
        # Verify left/right ordering where both are visible.
        # ----------------------------------------------------

        valid_positions = (
            (left >= 0) &
            (right >= 0)
        )

        if np.sum(valid_positions) < 8:

            invalid_geometry += 1

            continue

        if not np.all(
            left[valid_positions]
            <
            right[valid_positions]
        ):

            invalid_geometry += 1

            continue

        # ----------------------------------------------------
        # Create target.
        # ----------------------------------------------------

        target = np.concatenate(
            [
                left,
                right
            ]
        ).astype(
            np.float32
        )

        valid_samples.append(
            {
                "image_id": image_id,
                "file_name": image[
                    "file_name"
                ],
                "width": width,
                "height": height,
                "target": target
            }
        )

    # ========================================================
    # SAVE
    # ========================================================

    if not valid_samples:

        raise RuntimeError(
            f"No valid samples generated "
            f"for {split_name}"
        )

    targets = np.stack(
        [
            sample["target"]
            for sample in valid_samples
        ]
    )

    metadata = []

    for sample in valid_samples:

        metadata.append(
            {
                "image_id":
                    sample["image_id"],

                "file_name":
                    sample["file_name"],

                "width":
                    sample["width"],

                "height":
                    sample["height"]
            }
        )

    os.makedirs(
        OUTPUT_DIR,
        exist_ok=True
    )

    target_path = os.path.join(
        OUTPUT_DIR,
        f"{split_name}_targets.npy"
    )

    metadata_path = os.path.join(
        OUTPUT_DIR,
        f"{split_name}_metadata.json"
    )

    np.save(
        target_path,
        targets
    )

    with open(
        metadata_path,
        "w",
        encoding="utf-8"
    ) as f:

        json.dump(
            metadata,
            f,
            indent=2
        )

    # ========================================================
    # REPORT
    # ========================================================

    print()
    print(
        f"Images in split:       "
        f"{len(images_by_id)}"
    )

    print(
        f"Valid two-rail samples: "
        f"{len(valid_samples)}"
    )

    print(
        f"One-sided images:       "
        f"{one_sided}"
    )

    print(
        f"No-rail images:         "
        f"{no_rail}"
    )

    print(
        f"Invalid geometry:       "
        f"{invalid_geometry}"
    )

    print()
    print(
        f"Target shape: "
        f"{targets.shape}"
    )

    print(
        f"Saved targets: "
        f"{target_path}"
    )

    print(
        f"Saved metadata: "
        f"{metadata_path}"
    )

    return valid_samples


# ============================================================
# MAIN
# ============================================================

def main():

    print()
    print("=" * 70)
    print("RAIL-BENCH DATASET PREPARATION")
    print("=" * 70)

    if not os.path.exists(
        TRAIN_JSON
    ):

        raise FileNotFoundError(
            f"Training annotations not found:\n"
            f"{TRAIN_JSON}"
        )

    if not os.path.exists(
        VAL_JSON
    ):

        raise FileNotFoundError(
            f"Validation annotations not found:\n"
            f"{VAL_JSON}"
        )

    train_samples = process_split(
        TRAIN_JSON,
        "train"
    )

    val_samples = process_split(
        VAL_JSON,
        "val"
    )

    print()
    print("=" * 70)
    print("DATASET PREPARATION COMPLETE")
    print("=" * 70)

    print()
    print(
        f"Training samples:   "
        f"{len(train_samples)}"
    )

    print(
        f"Validation samples: "
        f"{len(val_samples)}"
    )

    print()
    print(
        "Output directory:"
    )

    print(
        OUTPUT_DIR
    )


if __name__ == "__main__":
    main()