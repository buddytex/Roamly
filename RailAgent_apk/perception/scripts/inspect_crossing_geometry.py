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
    "prepared_dataset_v3_diagnostic"
)

os.makedirs(OUTPUT_DIR, exist_ok=True)


# ============================================================
# SETTINGS  (unchanged from V3)
# ============================================================

NUM_SAMPLES = 16
MIN_POINTS = 5
MIN_Y_RANGE = 0.20
MAX_X_JUMP = 0.18
MIN_RAIL_SEPARATION = 0.03

# How many example filenames to keep per rejection category
MAX_EXAMPLES_PER_CATEGORY = 25


# ============================================================
# HELPERS  (unchanged from V3)
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

    DIAGNOSTIC CHANGE:
    Returns (curve_or_None, reason_string) instead of just
    curve_or_None, so the caller knows exactly why a fit
    failed. Also accepts force_degree to test alternate
    polynomial degrees without touching production logic.
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

    order = np.argsort(y)

    y = y[order]
    x = x[order]

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

    print(f"Loading: {annotation_file}")

    with open(annotation_file, "r", encoding="utf-8") as f:
        data = json.load(f)

    images = {
        image["id"]: image
        for image in data["images"]
    }

    annotations_by_image = defaultdict(list)

    ignored = 0

    for annotation in data["annotations"]:

        if annotation.get("category_id") != 1:
            ignored += 1
            continue

        if "polyline" not in annotation:
            ignored += 1
            continue

        if annotation.get("rightRail") not in (0, 1):
            ignored += 1
            continue

        annotations_by_image[annotation["image_id"]].append(annotation)

    targets = []
    metadata = []

    valid = 0

    # DIAGNOSTIC: one counter + one example list per specific reason
    reasons = [
        "no_rail",
        "one_sided",
        "too_few_points_raw_left",
        "too_few_points_raw_right",
        "curve_fit_failed_left",
        "curve_fit_failed_right",
        "insufficient_common_y_range",
        "x_jump_left",
        "x_jump_right",
        "insufficient_separation",
        "too_many_crossings",
    ]

    counts = {r: 0 for r in reasons}
    examples = {r: [] for r in reasons}
    # sub-reason breakdown for why fit_curve failed specifically
    fit_fail_subreasons = defaultdict(int)

    # DIAGNOSTIC: testing the "left/right labels are swapped for
    # some images" hypothesis against separation/crossings failures
    separation_swap_would_fix = 0
    crossings_swap_would_fix = 0

    # DIAGNOSTIC: testing whether forcing a linear (degree-1) fit
    # instead of degree-2 would recover separation/crossings failures
    separation_linear_would_fix = 0
    crossings_linear_would_fix = 0
    # of those linear "fixes", how many are physically implausible
    # (separation decreasing toward the camera / bottom of image)
    separation_linear_fix_but_implausible = 0
    crossings_linear_fix_but_implausible = 0

    # DIAGNOSTIC: full (untruncated) segment-count lists per category,
    # to test the "multi-track / switch scene" hypothesis
    segment_counts = defaultdict(list)  # reason -> [(left_segs, right_segs), ...]

    def reject(reason, image_id, file_name, detail=None,
               left_segs=None, right_segs=None):
        counts[reason] += 1
        if len(examples[reason]) < MAX_EXAMPLES_PER_CATEGORY:
            entry = {"image_id": image_id, "file_name": file_name}
            if detail is not None:
                entry["detail"] = detail
            examples[reason].append(entry)
        if left_segs is not None:
            segment_counts[reason].append((left_segs, right_segs))

    for image_id, image in images.items():

        annotations = annotations_by_image[image_id]
        file_name = image["file_name"]

        left_segments = [a for a in annotations if a.get("rightRail") == 0]
        right_segments = [a for a in annotations if a.get("rightRail") == 1]

        if not left_segments and not right_segments:
            reject("no_rail", image_id, file_name)
            continue

        if not left_segments or not right_segments:
            reject("one_sided", image_id, file_name)
            continue

        width = image["width"]
        height = image["height"]

        left_points = collect_points(left_segments, width, height)
        right_points = collect_points(right_segments, width, height)

        # DIAGNOSTIC: split the old combined "too few points" check
        # into left vs right so we know which side is the problem.
        left_too_few = len(left_points) < MIN_POINTS
        right_too_few = len(right_points) < MIN_POINTS

        if left_too_few:
            reject(
                "too_few_points_raw_left", image_id, file_name,
                detail={"n_points": int(len(left_points))},
                left_segs=len(left_segments), right_segs=len(right_segments)
            )
            continue

        if right_too_few:
            reject(
                "too_few_points_raw_right", image_id, file_name,
                detail={"n_points": int(len(right_points))},
                left_segs=len(left_segments), right_segs=len(right_segments)
            )
            continue

        left_curve, left_reason = fit_curve(left_points)
        right_curve, right_reason = fit_curve(right_points)

        if left_curve is None:
            fit_fail_subreasons[f"left:{left_reason}"] += 1
            reject(
                "curve_fit_failed_left", image_id, file_name,
                detail={"sub_reason": left_reason},
                left_segs=len(left_segments), right_segs=len(right_segments)
            )
            continue

        if right_curve is None:
            fit_fail_subreasons[f"right:{right_reason}"] += 1
            reject(
                "curve_fit_failed_right", image_id, file_name,
                detail={"sub_reason": right_reason},
                left_segs=len(left_segments), right_segs=len(right_segments)
            )
            continue

        common_min = max(left_curve["y_min"], right_curve["y_min"])
        common_max = min(left_curve["y_max"], right_curve["y_max"])

        if common_max - common_min < MIN_Y_RANGE:
            reject(
                "insufficient_common_y_range", image_id, file_name,
                detail={"common_range": float(common_max - common_min)},
                left_segs=len(left_segments), right_segs=len(right_segments)
            )
            continue

        left_ys = np.linspace(common_min, common_max, NUM_SAMPLES)
        right_ys = left_ys.copy()

        left_xs = np.polyval(left_curve["coefficients"], left_ys)
        right_xs = np.polyval(right_curve["coefficients"], right_ys)

        # DIAGNOSTIC NOTE: clipping still happens here, same as V3.
        # We separately record how many samples were actually
        # out-of-bounds BEFORE clipping, so we can judge later
        # whether clipping is hiding bad extrapolation.
        left_oob = int(np.sum((left_xs < 0.0) | (left_xs > 1.0)))
        right_oob = int(np.sum((right_xs < 0.0) | (right_xs > 1.0)))

        left_xs = np.clip(left_xs, 0.0, 1.0)
        right_xs = np.clip(right_xs, 0.0, 1.0)

        if not curve_quality(left_xs):
            reject(
                "x_jump_left", image_id, file_name,
                detail={
                    "max_jump": float(np.max(np.abs(np.diff(left_xs)))),
                    "oob_points_before_clip": left_oob
                },
                left_segs=len(left_segments), right_segs=len(right_segments)
            )
            continue

        if not curve_quality(right_xs):
            reject(
                "x_jump_right", image_id, file_name,
                detail={
                    "max_jump": float(np.max(np.abs(np.diff(right_xs)))),
                    "oob_points_before_clip": right_oob
                },
                left_segs=len(left_segments), right_segs=len(right_segments)
            )
            continue

        separation = right_xs - left_xs
        swap_separation = left_xs - right_xs  # what it'd be if labels were swapped
        swap_median = float(np.median(swap_separation))
        swap_crossings = int(np.sum(swap_separation < 0))
        would_fix_by_swap = (
            swap_median >= MIN_RAIL_SEPARATION and swap_crossings <= 2
        )

        # DIAGNOSTIC: test whether a forced degree-1 (linear) refit
        # would pass separation/crossings, and whether that refit is
        # physically plausible (separation should not decrease toward
        # the camera / bottom of image, y index increasing = closer)
        linear_left_curve, _ = fit_curve(left_points, force_degree=1)
        linear_right_curve, _ = fit_curve(right_points, force_degree=1)

        linear_would_fix = False
        linear_fix_plausible = False

        if linear_left_curve is not None and linear_right_curve is not None:
            lin_common_min = max(linear_left_curve["y_min"], linear_right_curve["y_min"])
            lin_common_max = min(linear_left_curve["y_max"], linear_right_curve["y_max"])

            if lin_common_max - lin_common_min >= MIN_Y_RANGE:
                lin_ys = np.linspace(lin_common_min, lin_common_max, NUM_SAMPLES)
                lin_left_xs = np.clip(
                    np.polyval(linear_left_curve["coefficients"], lin_ys), 0.0, 1.0
                )
                lin_right_xs = np.clip(
                    np.polyval(linear_right_curve["coefficients"], lin_ys), 0.0, 1.0
                )
                lin_separation = lin_right_xs - lin_left_xs
                lin_crossings = int(np.sum(lin_separation < 0))

                linear_would_fix = (
                    np.median(lin_separation) >= MIN_RAIL_SEPARATION
                    and lin_crossings <= 2
                )
                # plausible = separation does not meaningfully shrink
                # toward the camera (last sample vs first sample)
                linear_fix_plausible = (
                    linear_would_fix
                    and lin_separation[-1] >= lin_separation[0] - 0.01
                )

        if np.median(separation) < MIN_RAIL_SEPARATION:
            if would_fix_by_swap:
                separation_swap_would_fix += 1
            if linear_would_fix:
                separation_linear_would_fix += 1
                if not linear_fix_plausible:
                    separation_linear_fix_but_implausible += 1
            reject(
                "insufficient_separation", image_id, file_name,
                detail={
                    "median_separation": float(np.median(separation)),
                    "left_segments": len(left_segments),
                    "right_segments": len(right_segments),
                    "swap_median_separation": swap_median,
                    "would_fix_by_swap": would_fix_by_swap,
                    "linear_would_fix": linear_would_fix,
                    "linear_fix_plausible": linear_fix_plausible
                },
                left_segs=len(left_segments), right_segs=len(right_segments)
            )
            continue

        crossings = np.sum(separation < 0)

        if crossings > 2:
            if would_fix_by_swap:
                crossings_swap_would_fix += 1
            if linear_would_fix:
                crossings_linear_would_fix += 1
                if not linear_fix_plausible:
                    crossings_linear_fix_but_implausible += 1
            reject(
                "too_many_crossings", image_id, file_name,
                detail={
                    "crossings": int(crossings),
                    "left_segments": len(left_segments),
                    "right_segments": len(right_segments),
                    "swap_crossings": swap_crossings,
                    "would_fix_by_swap": would_fix_by_swap,
                    "linear_would_fix": linear_would_fix,
                    "linear_fix_plausible": linear_fix_plausible
                },
                left_segs=len(left_segments), right_segs=len(right_segments)
            )
            continue

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
                "left_oob_points_before_clip": left_oob,
                "right_oob_points_before_clip": right_oob
            }
        )

        valid += 1
        segment_counts["valid"].append(
            (len(left_segments), len(right_segments))
        )

    # ========================================================
    # SAVE  (same as V3, plus diagnostic report)
    # ========================================================

    if targets:
        targets_array = np.asarray(targets, dtype=np.float32)
    else:
        targets_array = np.empty((0, 96), dtype=np.float32)

    target_path = os.path.join(OUTPUT_DIR, f"{split}_targets.npy")
    metadata_path = os.path.join(OUTPUT_DIR, f"{split}_metadata.json")
    report_path = os.path.join(OUTPUT_DIR, f"{split}_rejection_report.json")

    np.save(target_path, targets_array)

    with open(metadata_path, "w", encoding="utf-8") as f:
        json.dump(metadata, f, indent=2)

    segment_stats_summary = {}
    for cat, pairs in segment_counts.items():
        if not pairs:
            continue
        segment_stats_summary[cat] = {
            "n": len(pairs),
            "avg_left_segments": float(np.mean([p[0] for p in pairs])),
            "avg_right_segments": float(np.mean([p[1] for p in pairs])),
        }

    report = {
        "total_images": len(images),
        "valid": valid,
        "counts": counts,
        "fit_fail_subreasons": dict(fit_fail_subreasons),
        "separation_swap_would_fix": separation_swap_would_fix,
        "crossings_swap_would_fix": crossings_swap_would_fix,
        "separation_linear_would_fix": separation_linear_would_fix,
        "crossings_linear_would_fix": crossings_linear_would_fix,
        "separation_linear_fix_but_implausible": separation_linear_fix_but_implausible,
        "crossings_linear_fix_but_implausible": crossings_linear_fix_but_implausible,
        "segment_stats": segment_stats_summary,
        "examples": examples,
    }

    with open(report_path, "w", encoding="utf-8") as f:
        json.dump(report, f, indent=2)

    # ========================================================
    # PRINT SUMMARY
    # ========================================================

    total_rejected = sum(counts.values())

    print()
    print(f"{split.upper()}")
    print("=" * 70)
    print()
    print(f"Total images: {len(images)}")
    print()
    print(f"Valid two-rail: {valid}")
    print()
    print("Rejected:")
    print(f"    No rail:                     {counts['no_rail']}")
    print(f"    One-sided:                   {counts['one_sided']}")
    print(f"    Too few points (left):       {counts['too_few_points_raw_left']}")
    print(f"    Too few points (right):      {counts['too_few_points_raw_right']}")
    print(f"    Curve fit failure (left):    {counts['curve_fit_failed_left']}")
    print(f"    Curve fit failure (right):   {counts['curve_fit_failed_right']}")
    print(f"    Insufficient Y range:        {counts['insufficient_common_y_range']}")
    print(f"    X jump (left):                {counts['x_jump_left']}")
    print(f"    X jump (right):               {counts['x_jump_right']}")
    print(f"    Separation:                  {counts['insufficient_separation']}")
    print(f"    Crossings:                   {counts['too_many_crossings']}")
    print()
    print(f"Sum of rejections: {total_rejected}")
    print(f"Sanity check (valid + rejected == total): "
          f"{valid + total_rejected == len(images)}")
    print()
    print("Curve-fit failure sub-reasons:")
    for k, v in sorted(fit_fail_subreasons.items(), key=lambda kv: -kv[1]):
        print(f"    {k}: {v}")
    print()
    print("Swap hypothesis (would left/right label swap fix the rejection?):")
    print(f"    Separation failures fixed by swap: "
          f"{separation_swap_would_fix} / {counts['insufficient_separation']}")
    print(f"    Crossings failures fixed by swap:  "
          f"{crossings_swap_would_fix} / {counts['too_many_crossings']}")
    print()
    print("Linear-fit hypothesis (would forcing degree-1 fix the rejection?):")
    print(f"    Separation failures fixed by linear: "
          f"{separation_linear_would_fix} / {counts['insufficient_separation']}"
          f"  (of which implausible: {separation_linear_fix_but_implausible})")
    print(f"    Crossings failures fixed by linear:  "
          f"{crossings_linear_would_fix} / {counts['too_many_crossings']}"
          f"  (of which implausible: {crossings_linear_fix_but_implausible})")
    print()
    print("Segment-count comparison (multi-track / switch hypothesis):")
    print(f"    {'category':<28}{'n':>6}{'avg_left_segs':>16}{'avg_right_segs':>16}")
    for cat in ["valid", "insufficient_separation", "too_many_crossings"]:
        pairs = segment_counts.get(cat, [])
        if not pairs:
            continue
        avg_left = float(np.mean([p[0] for p in pairs]))
        avg_right = float(np.mean([p[1] for p in pairs]))
        print(f"    {cat:<28}{len(pairs):>6}{avg_left:>16.2f}{avg_right:>16.2f}")
    print()
    print(f"Target shape: {targets_array.shape}")
    print()
    print(f"Saved: {target_path}")
    print(f"Saved: {metadata_path}")
    print(f"Saved: {report_path}")


# ============================================================
# MAIN
# ============================================================

process_split("train")
process_split("val")

print()
print("=" * 70)
print("RAIL-BENCH DATASET PREPARATION V3 DIAGNOSTIC COMPLETE")
print("=" * 70)
print()
print(f"Output: {OUTPUT_DIR}")