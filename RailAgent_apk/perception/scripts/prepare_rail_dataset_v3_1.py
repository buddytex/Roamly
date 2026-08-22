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
    "prepared_dataset_v3_1"
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


def fit_curve(points, force_degree=None):
    """
    Fit X as a function of Y.
    """

    if len(points) < MIN_POINTS:
        return None, "too_few_points_raw"

    points = remove_duplicate_points(points)

    if len(points) < MIN_POINTS:
        return None, "too_few_points_after_dedup"

    y = points[:, 1]
    x = points[:, 0]

    y_min = float(np.min(y))
    y_max = float(np.max(y))

    if y_max - y_min < MIN_Y_RANGE:
        return None, "insufficient_y_range_single_rail"

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
        return None, "too_few_unique_y"

    if force_degree is not None:
        degree = min(force_degree, len(y) - 1)
    else:
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
        return None, "polyfit_exception"

    return {
        "coefficients": coefficients,
        "y_min": y_min,
        "y_max": y_max
    }, "ok"


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

    # Rejection and validation stats
    valid_degree2 = 0
    valid_degree1 = 0
    rejected_no_rail = 0
    rejected_one_sided = 0
    rejected_too_few_points_left = 0
    rejected_too_few_points_right = 0
    rejected_curve_fit_failed_left = 0
    rejected_curve_fit_failed_right = 0
    rejected_insufficient_common_y_range = 0
    rejected_other = 0

    rejection_reasons = defaultdict(int)

    for image_id, image in images.items():

        annotations = annotations_by_image[
            image_id
        ]

        file_name = image["file_name"]

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

            rejected_no_rail += 1
            continue


        if not left_segments or not right_segments:

            rejected_one_sided += 1
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


        if len(left_points) < MIN_POINTS:
            rejected_too_few_points_left += 1
            continue

        if len(right_points) < MIN_POINTS:
            rejected_too_few_points_right += 1
            continue


        # ----------------------------------------------------
        # 1. Try Degree-2 Fit
        # ----------------------------------------------------

        left_curve, left_reason = fit_curve(left_points, force_degree=2)
        right_curve, right_reason = fit_curve(right_points, force_degree=2)

        degree2_fit_ok = (left_curve is not None) and (right_curve is not None)
        degree2_ok = False

        if degree2_fit_ok:
            common_min = max(
                left_curve["y_min"],
                right_curve["y_min"]
            )

            common_max = min(
                left_curve["y_max"],
                right_curve["y_max"]
            )

            if common_max - common_min >= MIN_Y_RANGE:
                left_ys = np.linspace(
                    common_min,
                    common_max,
                    NUM_SAMPLES
                )

                right_ys = left_ys.copy()

                left_xs = np.clip(
                    np.polyval(left_curve["coefficients"], left_ys),
                    0.0,
                    1.0
                )

                right_xs = np.clip(
                    np.polyval(right_curve["coefficients"], right_ys),
                    0.0,
                    1.0
                )

                if curve_quality(left_xs) and curve_quality(right_xs):
                    separation = right_xs - left_xs
                    median_separation = np.median(separation)
                    crossings = np.sum(separation < 0)

                    if median_separation >= MIN_RAIL_SEPARATION and crossings <= 2:
                        degree2_ok = True
                        valid_degree2 += 1

                        target = np.concatenate(
                            [
                                left_xs,
                                right_xs,
                                left_ys,
                                right_ys,
                                np.ones(NUM_SAMPLES),
                                np.ones(NUM_SAMPLES)
                            ]
                        ).astype(np.float32)

                        targets.append(target)

                        metadata.append(
                            {
                                "image_id": image_id,
                                "file_name": file_name,
                                "width": width,
                                "height": height,
                                "left_segments": len(left_segments),
                                "right_segments": len(right_segments),
                                "y_min": float(common_min),
                                "y_max": float(common_max),
                                "fit_degree": 2
                            }
                        )

        if degree2_ok:
            continue

        # If Degree-2 fit failed or wasn't suitable, check why
        if not degree2_fit_ok:
            if left_curve is None:
                rejected_curve_fit_failed_left += 1
            else:
                rejected_curve_fit_failed_right += 1
            continue

        common_min = max(
            left_curve["y_min"],
            right_curve["y_min"]
        )

        common_max = min(
            left_curve["y_max"],
            right_curve["y_max"]
        )

        if common_max - common_min < MIN_Y_RANGE:
            rejected_insufficient_common_y_range += 1
            continue


        # ----------------------------------------------------
        # 2. Try Degree-1 (linear) Fallback
        # ----------------------------------------------------

        linear_left_curve, _ = fit_curve(left_points, force_degree=1)
        linear_right_curve, _ = fit_curve(right_points, force_degree=1)

        degree1_ok = False

        if linear_left_curve is not None and linear_right_curve is not None:
            lin_common_min = max(
                linear_left_curve["y_min"],
                linear_right_curve["y_min"]
            )

            lin_common_max = min(
                linear_right_curve["y_max"],
                linear_right_curve["y_max"]
            )

            if lin_common_max - lin_common_min >= MIN_Y_RANGE:
                lin_ys = np.linspace(
                    lin_common_min,
                    lin_common_max,
                    NUM_SAMPLES
                )

                lin_left_xs = np.clip(
                    np.polyval(linear_left_curve["coefficients"], lin_ys),
                    0.0,
                    1.0
                )

                lin_right_xs = np.clip(
                    np.polyval(linear_right_curve["coefficients"], lin_ys),
                    0.0,
                    1.0
                )

                if curve_quality(lin_left_xs) and curve_quality(lin_right_xs):
                    lin_separation = lin_right_xs - lin_left_xs
                    lin_median_separation = np.median(lin_separation)
                    lin_crossings = np.sum(lin_separation < 0)

                    # Plausibility check: separation must not shrink towards the camera (bottom of image)
                    is_plausible = bool(lin_separation[-1] >= lin_separation[0] - 0.01)

                    if lin_median_separation >= MIN_RAIL_SEPARATION and lin_crossings <= 2 and is_plausible:
                        degree1_ok = True
                        valid_degree1 += 1

                        target = np.concatenate(
                            [
                                lin_left_xs,
                                lin_right_xs,
                                lin_ys,
                                lin_ys,
                                np.ones(NUM_SAMPLES),
                                np.ones(NUM_SAMPLES)
                            ]
                        ).astype(np.float32)

                        targets.append(target)

                        metadata.append(
                            {
                                "image_id": image_id,
                                "file_name": file_name,
                                "width": width,
                                "height": height,
                                "left_segments": len(left_segments),
                                "right_segments": len(right_segments),
                                "y_min": float(lin_common_min),
                                "y_max": float(lin_common_max),
                                "fit_degree": 1
                            }
                        )

        if degree1_ok:
            continue


        # ----------------------------------------------------
        # 3. Permanent Rejection (Track failures using standard degree-2 checks)
        # ----------------------------------------------------
        rejected_other += 1

        left_ys = np.linspace(common_min, common_max, NUM_SAMPLES)
        right_ys = left_ys.copy()
        left_xs = np.clip(np.polyval(left_curve["coefficients"], left_ys), 0.0, 1.0)
        right_xs = np.clip(np.polyval(right_curve["coefficients"], right_ys), 0.0, 1.0)

        if not curve_quality(left_xs):
            rejection_reasons["x_jump_left"] += 1
        elif not curve_quality(right_xs):
            rejection_reasons["x_jump_right"] += 1
        else:
            separation = right_xs - left_xs
            median_separation = np.median(separation)
            crossings = np.sum(separation < 0)

            if median_separation < MIN_RAIL_SEPARATION:
                rejection_reasons["insufficient_separation"] += 1
            elif crossings > 2:
                rejection_reasons["too_many_crossings"] += 1
            else:
                rejection_reasons["unknown_failure"] += 1


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

    total_valid = valid_degree2 + valid_degree1
    total_rejected = (
        rejected_no_rail
        + rejected_one_sided
        + rejected_too_few_points_left
        + rejected_too_few_points_right
        + rejected_curve_fit_failed_left
        + rejected_curve_fit_failed_right
        + rejected_insufficient_common_y_range
        + rejected_other
    )

    print()
    print("RESULTS")
    print("-" * 70)

    print(
        f"Images:                 {len(images)}"
    )

    print(
        f"Valid samples (total):  {total_valid}  (Degree-2: {valid_degree2}, Degree-1 (recovered): {valid_degree1})"
    )

    print(
        f"Rejected:               {total_rejected}"
    )

    print(
        f"    No rail:                     {rejected_no_rail}"
    )

    print(
        f"    One-sided:                   {rejected_one_sided}"
    )

    print(
        f"    Too few points (left):       {rejected_too_few_points_left}"
    )

    print(
        f"    Too few points (right):      {rejected_too_few_points_right}"
    )

    print(
        f"    Curve fit failure (left):    {rejected_curve_fit_failed_left}"
    )

    print(
        f"    Curve fit failure (right):   {rejected_curve_fit_failed_right}"
    )

    print(
        f"    Insufficient Y range:        {rejected_insufficient_common_y_range}"
    )

    print(
        f"    X jump (left):                {rejection_reasons['x_jump_left']}"
    )

    print(
        f"    X jump (right):               {rejection_reasons['x_jump_right']}"
    )

    print(
        f"    Separation:                  {rejection_reasons['insufficient_separation']}"
    )

    print(
        f"    Crossings:                   {rejection_reasons['too_many_crossings']}"
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

if __name__ == "__main__":
    process_split("train")
    process_split("val")

    print()
    print("=" * 70)
    print("RAIL-BENCH DATASET PREPARATION V3_1 COMPLETE")
    print("=" * 70)
    print()
    print(f"Output: {OUTPUT_DIR}")
