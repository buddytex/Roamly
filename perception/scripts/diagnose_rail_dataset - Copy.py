import json
from collections import defaultdict, Counter


# ============================================================
# PATH
# ============================================================

ANNOTATION_FILE = (
    r"C:\RailGuardAI\railbench\annotations\rails"
    r"\annotations_train.json"
)


# ============================================================
# LOAD DATA
# ============================================================

print("=" * 70)
print("RAIL-BENCH DATASET DIAGNOSTIC")
print("=" * 70)

with open(
    ANNOTATION_FILE,
    "r",
    encoding="utf-8"
) as f:
    data = json.load(f)


images = {
    image["id"]: image
    for image in data["images"]
}


# ============================================================
# GROUP RAIL SEGMENTS
# ============================================================

rails = defaultdict(
    lambda: {
        "left": [],
        "right": []
    }
)


for annotation in data["annotations"]:

    # Ignore non-rail annotations
    if annotation.get("category_id") != 1:
        continue

    side = annotation.get("rightRail")

    if side == 0:
        rails[
            annotation["image_id"]
        ]["left"].append(annotation)

    elif side == 1:
        rails[
            annotation["image_id"]
        ]["right"].append(annotation)


# ============================================================
# DIAGNOSTICS
# ============================================================

counts = Counter()

vertical_ranges = []

segment_counts = []

examples = {}


# ============================================================
# PROCESS IMAGES
# ============================================================

for image_id, image in images.items():

    left_segments = rails[
        image_id
    ]["left"]

    right_segments = rails[
        image_id
    ]["right"]


    # --------------------------------------------------------
    # No rail
    # --------------------------------------------------------

    if (
        len(left_segments) == 0
        and
        len(right_segments) == 0
    ):

        counts["no_rail"] += 1
        continue


    # --------------------------------------------------------
    # Only one rail
    # --------------------------------------------------------

    if (
        len(left_segments) == 0
        or
        len(right_segments) == 0
    ):

        counts["one_sided"] += 1

        continue


    # --------------------------------------------------------
    # Collect all points
    # --------------------------------------------------------

    def collect_points(
        segments
    ):

        points = []

        for segment in segments:

            for point in segment.get(
                "polyline",
                []
            ):

                if len(point) != 2:
                    continue

                x = float(point[0])
                y = float(point[1])

                points.append(
                    (x, y)
                )

        return points


    left_points = collect_points(
        left_segments
    )

    right_points = collect_points(
        right_segments
    )


    if not left_points or not right_points:

        counts[
            "missing_points"
        ] += 1

        continue


    # --------------------------------------------------------
    # Vertical ranges
    # --------------------------------------------------------

    height = image["height"]

    left_y = [
        y / height
        for x, y in left_points
    ]

    right_y = [
        y / height
        for x, y in right_points
    ]


    left_min = min(left_y)
    left_max = max(left_y)

    right_min = min(right_y)
    right_max = max(right_y)


    left_range = (
        left_max - left_min
    )

    right_range = (
        right_max - right_min
    )


    vertical_ranges.append(
        (
            left_range,
            right_range
        )
    )


    # --------------------------------------------------------
    # Segment counts
    # --------------------------------------------------------

    segment_counts.append(
        (
            len(left_segments),
            len(right_segments)
        )
    )


    # --------------------------------------------------------
    # Short vertical coverage
    # --------------------------------------------------------

    if (
        left_range < 0.20
        or
        right_range < 0.20
    ):

        counts[
            "short_vertical_range"
        ] += 1

        if "short_example" not in examples:

            examples[
                "short_example"
            ] = {
                "image_id": image_id,
                "file_name": image[
                    "file_name"
                ],
                "left_range": left_range,
                "right_range": right_range,
                "left_min": left_min,
                "left_max": left_max,
                "right_min": right_min,
                "right_max": right_max
            }

        continue


    # --------------------------------------------------------
    # Candidate
    # --------------------------------------------------------

    counts["candidate"] += 1


# ============================================================
# REPORT
# ============================================================

print()
print("IMAGE COUNTS")
print("-" * 70)

print(
    f"Total images:          {len(images)}"
)

print(
    f"No rail:               {counts['no_rail']}"
)

print(
    f"One sided:             {counts['one_sided']}"
)

print(
    f"Missing points:        {counts['missing_points']}"
)

print(
    f"Short vertical range:  {counts['short_vertical_range']}"
)

print(
    f"Candidate:             {counts['candidate']}"
)


# ============================================================
# RANGE STATISTICS
# ============================================================

if vertical_ranges:

    left_ranges = [
        x[0]
        for x in vertical_ranges
    ]

    right_ranges = [
        x[1]
        for x in vertical_ranges
    ]

    print()
    print("VERTICAL COVERAGE")
    print("-" * 70)

    print(
        f"Left minimum:   "
        f"{min(left_ranges):.3f}"
    )

    print(
        f"Left maximum:   "
        f"{max(left_ranges):.3f}"
    )

    print(
        f"Left average:   "
        f"{sum(left_ranges) / len(left_ranges):.3f}"
    )

    print()

    print(
        f"Right minimum:  "
        f"{min(right_ranges):.3f}"
    )

    print(
        f"Right maximum:  "
        f"{max(right_ranges):.3f}"
    )

    print(
        f"Right average:  "
        f"{sum(right_ranges) / len(right_ranges):.3f}"
    )


# ============================================================
# SEGMENT STATISTICS
# ============================================================

if segment_counts:

    left_segments = [
        x[0]
        for x in segment_counts
    ]

    right_segments = [
        x[1]
        for x in segment_counts
    ]

    print()
    print("SEGMENT COUNTS")
    print("-" * 70)

    print(
        f"Left segments - min: "
        f"{min(left_segments)}"
    )

    print(
        f"Left segments - max: "
        f"{max(left_segments)}"
    )

    print(
        f"Right segments - min: "
        f"{min(right_segments)}"
    )

    print(
        f"Right segments - max: "
        f"{max(right_segments)}"
    )


# ============================================================
# EXAMPLE
# ============================================================

if "short_example" in examples:

    print()
    print("EXAMPLE SHORT-COVERAGE IMAGE")
    print("-" * 70)

    example = examples[
        "short_example"
    ]

    for key, value in example.items():

        print(
            f"{key}: {value}"
        )


# ============================================================
# DONE
# ============================================================

print()
print("=" * 70)
print("DIAGNOSTIC COMPLETE")
print("=" * 70)