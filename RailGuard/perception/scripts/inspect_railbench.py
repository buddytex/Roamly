import json
import os
import statistics
from collections import defaultdict, Counter


TRAIN_FILE = r"C:\RailGuardAI\railbench\annotations\rails\annotations_train.json"
VAL_FILE = r"C:\RailGuardAI\railbench\annotations\rails\annotations_val.json"


def inspect_dataset(path):

    print("\n" + "=" * 70)
    print(f"DATASET: {os.path.basename(path)}")
    print("=" * 70)

    with open(path, "r", encoding="utf-8") as f:
        data = json.load(f)

    images = data["images"]
    annotations = data["annotations"]

    image_by_id = {
        image["id"]: image
        for image in images
    }

    annotations_by_image = defaultdict(list)

    for annotation in annotations:
        annotations_by_image[
            annotation["image_id"]
        ].append(annotation)

    rail_counts = []
    ignore_counts = []

    right_rail_count = 0
    left_rail_count = 0

    occlusion_counter = Counter()

    total_polyline_points = 0

    images_with_no_rail = 0

    for image in images:

        image_id = image["id"]

        anns = annotations_by_image.get(
            image_id,
            []
        )

        rails = [
            a for a in anns
            if a["category_id"] == 1
        ]

        ignores = [
            a for a in anns
            if a["category_id"] == 2
        ]

        rail_counts.append(
            len(rails)
        )

        ignore_counts.append(
            len(ignores)
        )

        if len(rails) == 0:
            images_with_no_rail += 1

        for rail in rails:

            if rail.get("rightRail") == 1:
                right_rail_count += 1

            else:
                left_rail_count += 1

            occlusion_counter[
                rail.get("occlusion", -1)
            ] += 1

            polyline = rail.get(
                "polyline",
                []
            )

            total_polyline_points += len(
                polyline
            )

    dimensions = Counter(
        (
            image.get("width"),
            image.get("height")
        )
        for image in images
    )

    print(f"Images:              {len(images)}")
    print(f"Annotations:         {len(annotations)}")
    print(f"Images without rail: {images_with_no_rail}")

    print()
    print("Rail annotations")
    print("----------------")
    print(f"Left rail:           {left_rail_count}")
    print(f"Right rail:          {right_rail_count}")
    print(f"Total rail points:   {total_polyline_points}")

    print()
    print("Rails per image")
    print("----------------")
    print(
        f"Minimum:             {min(rail_counts)}"
    )
    print(
        f"Median:              {statistics.median(rail_counts)}"
    )
    print(
        f"Maximum:             {max(rail_counts)}"
    )

    print()
    print("Most common rail counts")
    print("-----------------------")

    for count, frequency in Counter(
        rail_counts
    ).most_common(15):

        print(
            f"{count:>3} rails : "
            f"{frequency} images"
        )

    print()
    print("Occlusion values")
    print("----------------")

    for value, count in sorted(
        occlusion_counter.items()
    ):

        print(
            f"{value}: {count}"
        )

    print()
    print("Image dimensions")
    print("----------------")

    for dimension, count in dimensions.most_common(10):

        print(
            f"{dimension[0]} x "
            f"{dimension[1]} : "
            f"{count} images"
        )


if __name__ == "__main__":

    inspect_dataset(
        TRAIN_FILE
    )

    inspect_dataset(
        VAL_FILE
    )