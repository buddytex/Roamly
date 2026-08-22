import json
import os
import numpy as np
from collections import defaultdict


BASE_DIR = r"C:\RailGuardAI"

ANNOTATION_DIR = os.path.join(
    BASE_DIR,
    "railbench",
    "annotations",
    "rails"
)

OUTPUT_DIR = os.path.join(
    BASE_DIR,
    "prepared_dataset_v3"
)

os.makedirs(OUTPUT_DIR, exist_ok=True)


# ============================================================
# SETTINGS
# ============================================================

NUM_SAMPLES = 16

# Minimum number of raw points required for a rail candidate
MIN_POINTS = 5

# Minimum vertical coverage
MIN_Y_RANGE = 0.20

# Maximum allowed normalized X jump between neighbouring
# sampled points
MAX_X_JUMP = 0.18

# Minimum separation between left and right rails
MIN_RAIL_SEPARATION = 0.03


# ============================================================
# HELPERS
# ============================================================

def normalize_points(points, width, height):
    result = []

    for x, y in points:
        result.append(
            (
                float(x) / width,
                float(y) / height
            )
        )

    return np.asarray(result, dtype=np.float32)


def collect_points(segments, width, height):
    points = []

    for seg in segments:

        polyline = seg.get("polyline")

        if not polyline:
            continue

        p = normalize_points(
            polyline,
            width,
            height
        )

        for point in p:
            points.append(point)

    if not points:
        return np.empty(
            (0, 2),
            dtype=np.float32
        )

    return np.asarray(
        points,
        dtype=np.float32
    )


def remove_duplicate_points(points):
    if len(points) <= 1:
        return points

    rounded = np.round(
        points,
        decimals=5
    )

    _, indices = np.unique(
        rounded,
        axis=0,
        return_index=True
    )

    indices = np.sort(indices)

    return points[indices]


def fit_curve(points):
    """
    Fit X as a function of Y.

    We use a quadratic polynomial instead of trying to
    connect annotation segments directly.

    This gives us a smooth approximation of the rail.
    """

    if len(points) < MIN_POINTS:
        return None

    points = remove_duplicate_points(points)

    if len(points) < MIN_POINTS:
        return None

    y = points[:, 1]
    x = points[:, 0]

    y_min = float(np.min(y))
    y_max = float(np.max(y))

    if y_max - y_min < MIN_Y_RANGE:
        return None

    # Sort by vertical position
    order = np.argsort(y)

    y = y[order]
    x = x[order]

    # Remove duplicate Y values
    unique_y, unique_indices = np.unique(
        y,
        return_index=True
    )

    x = x[unique_indices]
    y = unique_y

    if len(y) < MIN_POINTS:
        return None

    degree = min(
        2,
        len(y) - 1
    )

    try:
        coefficients = np.polyfit(
            y,
            x,
            degree
        )
    except Exception:
        return None

    return {
        "coefficients": coefficients,
        "y_min": y_min,
        "y_max": y_max
    }


def sample_curve(curve):
    y_min = curve["y_min"]
    y_max = curve["y_max"]

    coefficients = curve["coefficients"]

    ys = np.linspace(
        y_min,
        y_max,
        NUM_SAMPLES
    )

    xs = np.polyval(
        coefficients,
        ys
    )

    xs = np.clip(
        xs,
        0.0,
        1.0
    )

    return xs, ys


def curve_quality(xs):
    """
    Check whether the fitted curve behaves sensibly.
    """

    if len(xs) < 2:
        return False

    jumps = np.abs(
        np.diff(xs)
    )

    if np.max(jumps) > MAX_X_JUMP:
        return False

    return True


# ============================================================
# PROCESS ONE SPLIT
# ============================================================

def process_split(split):

    annotation_file = os.path.join(
        ANNOTATION_DIR,
        f"annotations_{split}.json"
    )

    print()
    print("=" * 70)
    print(f"PROCESSING {split.upper()}")
    print("=" * 70)

    print(
        f"Loading: {annotation_file}"
    )

    with open(
        annotation_file,
        "r",
        encoding="utf-8"
    ) as f:

        data = json.load(f)


    images = {
        image["id"]: image
        for image in data["images"]
    }


    annotations_by_image = defaultdict(
        list
    )

    ignored = 0

    for annotation in data["annotations"]:

        # Only railway rail annotations
        if annotation.get("category_id") != 1:
            ignored += 1
            continue

        if "polyline" not in annotation:
            ignored += 1
            continue

        if annotation.get("rightRail") not in (0, 1):
            ignored += 1
            continue

        annotations_by_image[
            annotation["image_id"]
        ].append(annotation)


    targets = []
    metadata = []

    no_rail = 0
    one_sided = 0
    invalid = 0
    valid = 0


    for image_id, image in images.items():

        annotations = annotations_by_image[
            image_id
        ]

        left_segments = [
            a
            for a in annotations
            if a.get("rightRail") == 0
        ]

        right_segments = [
            a
            for a in annotations
            if a.get("rightRail") == 1
        ]


        if not left_segments and not right_segments:

            no_rail += 1
            continue


        if not left_segments or not right_segments:

            one_sided += 1
            continue


        width = image["width"]
        height = image["height"]


        # ----------------------------------------------------
        # Collect points
        # ----------------------------------------------------

        left_points = collect_points(
            left_segments,
            width,
            height
        )

        right_points = collect_points(
            right_segments,
            width,
            height
        )


        if (
            len(left_points) < MIN_POINTS
            or
            len(right_points) < MIN_POINTS
        ):

            invalid += 1
            continue


        # ----------------------------------------------------
        # Fit curves
        # ----------------------------------------------------

        left_curve = fit_curve(
            left_points
        )

        right_curve = fit_curve(
            right_points
        )


        if (
            left_curve is None
            or
            right_curve is None
        ):

            invalid += 1
            continue


        # ----------------------------------------------------
        # Find common vertical region
        # ----------------------------------------------------

        common_min = max(
            left_curve["y_min"],
            right_curve["y_min"]
        )

        common_max = min(
            left_curve["y_max"],
            right_curve["y_max"]
        )


        if common_max - common_min < MIN_Y_RANGE:

            invalid += 1
            continue


        # Rebuild curves using common range
        left_ys = np.linspace(
            common_min,
            common_max,
            NUM_SAMPLES
        )

        right_ys = left_ys.copy()


        left_xs = np.polyval(
            left_curve["coefficients"],
            left_ys
        )

        right_xs = np.polyval(
            right_curve["coefficients"],
            right_ys
        )


        left_xs = np.clip(
            left_xs,
            0.0,
            1.0
        )

        right_xs = np.clip(
            right_xs,
            0.0,
            1.0
        )


        # ----------------------------------------------------
        # Quality checks
        # ----------------------------------------------------

        if not curve_quality(left_xs):

            invalid += 1
            continue


        if not curve_quality(right_xs):

            invalid += 1
            continue


        # Left rail must normally be left of right rail.
        separation = (
            right_xs - left_xs
        )


        if np.median(separation) < MIN_RAIL_SEPARATION:

            invalid += 1
            continue


        # Reject curves that cross repeatedly.
        crossings = np.sum(
            separation < 0
        )


        if crossings > 2:

            invalid += 1
            continue


        # ----------------------------------------------------
        # Build target
        #
        # 0:16   left X
        # 16:32  right X
        # 32:48  left Y
        # 48:64  right Y
        # 64:80  left validity
        # 80:96  right validity
        # ----------------------------------------------------

        target = np.concatenate(
            [
                left_xs,
                right_xs,
                left_ys,
                right_ys,
                np.ones(NUM_SAMPLES),
                np.ones(NUM_SAMPLES)
            ]
        ).astype(
            np.float32
        )


        targets.append(
            target
        )


        metadata.append(
            {
                "image_id": image_id,
                "file_name": image["file_name"],
                "width": width,
                "height": height,
                "left_segments": len(left_segments),
                "right_segments": len(right_segments),
                "y_min": float(common_min),
                "y_max": float(common_max)
            }
        )


        valid += 1


    # ========================================================
    # SAVE
    # ========================================================

    if targets:

        targets_array = np.asarray(
            targets,
            dtype=np.float32
        )

    else:

        targets_array = np.empty(
            (0, 96),
            dtype=np.float32
        )


    target_path = os.path.join(
        OUTPUT_DIR,
        f"{split}_targets.npy"
    )

    metadata_path = os.path.join(
        OUTPUT_DIR,
        f"{split}_metadata.json"
    )


    np.save(
        target_path,
        targets_array
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
    # RESULTS
    # ========================================================

    print()
    print("RESULTS")
    print("-" * 70)

    print(
        f"Images:                 {len(images)}"
    )

    print(
        f"Valid two-rail samples: {valid}"
    )

    print(
        f"One-sided:              {one_sided}"
    )

    print(
        f"No rail:                {no_rail}"
    )

    print(
        f"Invalid:                {invalid}"
    )

    print(
        f"Ignored annotations:    {ignored}"
    )

    print()
    print(
        f"Target shape: {targets_array.shape}"
    )

    print()
    print(
        f"Saved: {target_path}"
    )

    print(
        f"Saved: {metadata_path}"
    )


# ============================================================
# MAIN
# ============================================================

process_split("train")

process_split("val")


print()
print("=" * 70)
print("RAIL-BENCH DATASET PREPARATION V3 COMPLETE")
print("=" * 70)

print()
print(
    f"Output: {OUTPUT_DIR}"
)