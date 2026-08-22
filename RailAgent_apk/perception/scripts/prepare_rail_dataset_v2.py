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
    "prepared_dataset_v2"
)


# ============================================================
# MODEL TARGET SETTINGS
# ============================================================

# Number of points predicted along each rail.
NUM_SAMPLES = 16

# Number of vertical bins used to reconstruct the rail.
NUM_BINS = 80

# Minimum number of valid reconstructed bins.
MIN_VALID_BINS = 6

# Minimum vertical coverage required.
MIN_VERTICAL_RANGE = 0.05

# Maximum allowed normalized gap between valid bins.
MAX_INTERPOLATION_GAP = 0.20


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
# GROUP RAIL SEGMENTS
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

        # Only category 1 is a rail.
        if annotation.get("category_id") != 1:

            ignored += 1
            continue

        side = annotation.get("rightRail")

        polyline = annotation.get(
            "polyline"
        )

        if not polyline:
            continue

        if side == 0:

            grouped[
                annotation["image_id"]
            ]["left"].append(annotation)

        elif side == 1:

            grouped[
                annotation["image_id"]
            ]["right"].append(annotation)

    print(
        f"Ignored non-rail annotations: {ignored}"
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

            if not np.isfinite(x):
                continue

            if not np.isfinite(y):
                continue

            points.append(
                (x, y)
            )

    return points


# ============================================================
# RECONSTRUCT RAIL
# ============================================================

def reconstruct_rail(
    segments,
    width,
    height
):
    """
    Convert many annotated rail segments into
    a single robust rail representation.

    Returns:

        {
            "x": normalized X samples,
            "y": normalized Y samples,
            "valid": validity mask,
            "start_y": visible start,
            "end_y": visible end
        }

    or None if reconstruction fails.
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

        nx = float(
            np.clip(
                nx,
                0.0,
                1.0
            )
        )

        ny = float(
            np.clip(
                ny,
                0.0,
                1.0
            )
        )

        normalized.append(
            (nx, ny)
        )

    if len(normalized) < 2:
        return None

    # --------------------------------------------------------
    # Sort by Y.
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

    min_y = float(
        ys.min()
    )

    max_y = float(
        ys.max()
    )

    vertical_range = (
        max_y - min_y
    )

    if vertical_range < MIN_VERTICAL_RANGE:
        return None

    # --------------------------------------------------------
    # Create vertical bins over actual visible range.
    # --------------------------------------------------------

    bin_edges = np.linspace(
        min_y,
        max_y,
        NUM_BINS + 1
    )

    bin_centers = (
        bin_edges[:-1]
        + bin_edges[1:]
    ) / 2.0

    bin_x = np.full(
        NUM_BINS,
        np.nan,
        dtype=np.float32
    )

    bin_y = np.full(
        NUM_BINS,
        np.nan,
        dtype=np.float32
    )

    # --------------------------------------------------------
    # Median X per Y bin.
    # --------------------------------------------------------

    for i in range(NUM_BINS):

        low = bin_edges[i]
        high = bin_edges[i + 1]

        if i == NUM_BINS - 1:

            mask = (
                (ys >= low)
                &
                (ys <= high)
            )

        else:

            mask = (
                (ys >= low)
                &
                (ys < high)
            )

        if not np.any(mask):
            continue

        values = xs[mask]

        bin_x[i] = float(
            np.median(values)
        )

        bin_y[i] = float(
            np.median(ys[mask])
        )

    # --------------------------------------------------------
    # Valid bins.
    # --------------------------------------------------------

    valid = np.isfinite(
        bin_x
    )

    valid_count = int(
        np.sum(valid)
    )

    if valid_count < MIN_VALID_BINS:
        return None

    valid_indices = np.where(
        valid
    )[0]

    first_valid = int(
        valid_indices[0]
    )

    last_valid = int(
        valid_indices[-1]
    )

    # --------------------------------------------------------
    # Detect very large internal gaps.
    # --------------------------------------------------------

    valid_y = bin_y[valid]

    if len(valid_y) >= 2:

        gaps = np.diff(
            valid_y
        )

        if np.max(gaps) > MAX_INTERPOLATION_GAP:

            # Don't immediately reject the image.
            # We'll only use the largest continuous region.

            largest_start = 0
            current_start = 0

            for i, gap in enumerate(gaps):

                if gap > MAX_INTERPOLATION_GAP:

                    current_length = (
                        i
                        - current_start
                        + 1
                    )

                    largest_length = (
                        largest_start
                    )

                    if current_length > largest_length:

                        largest_start = (
                            current_start
                        )

                    current_start = (
                        i + 1
                    )

            # We intentionally don't reject here.
            # The actual sampled points will carry a
            # validity mask.
            pass

    # --------------------------------------------------------
    # Interpolate within the observed rail range.
    # --------------------------------------------------------

    valid_y = bin_y[valid]
    valid_x = bin_x[valid]

    order = np.argsort(
        valid_y
    )

    valid_y = valid_y[
        order
    ]

    valid_x = valid_x[
        order
    ]

    start_y = float(
        valid_y[0]
    )

    end_y = float(
        valid_y[-1]
    )

    # --------------------------------------------------------
    # Sample 16 points across actual visible range.
    # --------------------------------------------------------

    sample_y = np.linspace(
        start_y,
        end_y,
        NUM_SAMPLES,
        dtype=np.float32
    )

    sample_x = np.interp(
        sample_y,
        valid_y,
        valid_x
    ).astype(
        np.float32
    )

    # --------------------------------------------------------
    # Determine which samples are supported by actual
    # annotation coverage rather than a large interpolation
    # gap.
    # --------------------------------------------------------

    sample_valid = np.ones(
        NUM_SAMPLES,
        dtype=np.float32
    )

    for i, y in enumerate(sample_y):

        nearest = np.min(
            np.abs(
                valid_y - y
            )
        )

        # Allow interpolation between nearby bins.
        if nearest > MAX_INTERPOLATION_GAP:

            sample_valid[i] = 0.0

    return {
        "x": sample_x,
        "y": sample_y,
        "valid": sample_valid,
        "start_y": start_y,
        "end_y": end_y
    }


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

    images_by_id = {
        image["id"]: image
        for image in data["images"]
    }

    samples = []

    no_rail = 0
    one_sided = 0
    invalid = 0
    valid = 0

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
        # No rails.
        # ----------------------------------------------------

        if (
            not left_segments
            and
            not right_segments
        ):

            no_rail += 1
            continue

        # ----------------------------------------------------
        # One-sided.
        # ----------------------------------------------------

        if (
            not left_segments
            or
            not right_segments
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

            invalid += 1
            continue

        # ----------------------------------------------------
        # Reconstruct left rail.
        # ----------------------------------------------------

        left = reconstruct_rail(
            left_segments,
            width,
            height
        )

        # ----------------------------------------------------
        # Reconstruct right rail.
        # ----------------------------------------------------

        right = reconstruct_rail(
            right_segments,
            width,
            height
        )

        if (
            left is None
            or
            right is None
        ):

            invalid += 1
            continue

        # ----------------------------------------------------
        # Check that rails don't cross at supported samples.
        # ----------------------------------------------------

        both_valid = (
            (left["valid"] > 0)
            &
            (right["valid"] > 0)
        )

        if np.sum(both_valid) < 4:

            invalid += 1
            continue

        crossing = (
            left["x"][both_valid]
            >=
            right["x"][both_valid]
        )

        # Don't reject immediately for a tiny number of
        # inconsistent points.
        #
        # Reject only if most supported points cross.

        if (
            np.mean(crossing)
            > 0.50
        ):

            invalid += 1
            continue

        # ----------------------------------------------------
        # Build target.
        #
        # Layout:
        #
        # left_x[16]
        # right_x[16]
        # left_y[16]
        # right_y[16]
        # left_valid[16]
        # right_valid[16]
        #
        # Total = 96 values.
        # ----------------------------------------------------

        target = np.concatenate(
            [
                left["x"],
                right["x"],
                left["y"],
                right["y"],
                left["valid"],
                right["valid"]
            ]
        ).astype(
            np.float32
        )

        samples.append(
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

        valid += 1

    # ========================================================
    # SAVE
    # ========================================================

    if not samples:

        raise RuntimeError(
            f"No valid samples for {split_name}"
        )

    targets = np.stack(
        [
            sample["target"]
            for sample in samples
        ]
    )

    metadata = []

    for sample in samples:

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
    print("RESULTS")
    print("-" * 70)

    print(
        f"Images:                 "
        f"{len(images_by_id)}"
    )

    print(
        f"Valid two-rail samples: "
        f"{valid}"
    )

    print(
        f"One-sided:              "
        f"{one_sided}"
    )

    print(
        f"No rail:                "
        f"{no_rail}"
    )

    print(
        f"Invalid:                "
        f"{invalid}"
    )

    print()

    print(
        f"Target shape: "
        f"{targets.shape}"
    )

    print()

    print(
        f"Saved: "
        f"{target_path}"
    )

    print(
        f"Saved: "
        f"{metadata_path}"
    )

    return samples


# ============================================================
# MAIN
# ============================================================

def main():

    print()
    print("=" * 70)
    print("RAIL-BENCH DATASET PREPARATION V2")
    print("=" * 70)

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
    print("DATASET PREPARATION V2 COMPLETE")
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
        "Output:"
    )

    print(
        OUTPUT_DIR
    )


if __name__ == "__main__":
    main()