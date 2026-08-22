import json
import os
import random

import cv2
import numpy as np


ANNOTATION_FILE = (
    r"C:\RailGuardAI\railbench\annotations"
    r"\rails\annotations_train.json"
)

# CHANGE THIS after you extract the image archive.
IMAGE_DIR = r"C:\RailGuardAI\railbench\images"

OUTPUT_DIR = r"C:\RailGuardAI\visualizations"


def load_annotations():

    with open(
        ANNOTATION_FILE,
        "r",
        encoding="utf-8"
    ) as f:
        data = json.load(f)

    return data


def draw_polyline(
    image,
    points,
    color,
    thickness=5
):

    if len(points) < 2:
        return

    pts = np.array(
        points,
        dtype=np.int32
    )

    pts = pts.reshape((-1, 1, 2))

    cv2.polylines(
        image,
        [pts],
        False,
        color,
        thickness,
        cv2.LINE_AA
    )


def main():

    os.makedirs(
        OUTPUT_DIR,
        exist_ok=True
    )

    data = load_annotations()

    images = data["images"]
    annotations = data["annotations"]

    annotations_by_image = {}

    for annotation in annotations:

        image_id = annotation["image_id"]

        annotations_by_image.setdefault(
            image_id,
            []
        ).append(annotation)

    # Pick images containing rails.
    candidates = []

    for image in images:

        anns = annotations_by_image.get(
            image["id"],
            []
        )

        rails = [
            a for a in anns
            if a["category_id"] == 1
        ]

        if len(rails) > 0:
            candidates.append(image)

    random.seed(42)

    selected = random.sample(
        candidates,
        min(20, len(candidates))
    )

    for image_info in selected:

        image_id = image_info["id"]

        image_name = image_info["file_name"]

        image_path = os.path.join(
            IMAGE_DIR,
            image_name
        )

        if not os.path.exists(image_path):

            print(
                "IMAGE NOT FOUND:",
                image_path
            )

            continue

        image = cv2.imread(
            image_path
        )

        if image is None:

            print(
                "FAILED TO READ:",
                image_path
            )

            continue

        anns = annotations_by_image.get(
            image_id,
            []
        )

        rail_number = 0

        for annotation in anns:

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
                    "rightRail"
                ) == 1
            )

            # BGR
            if right_rail:
                color = (0, 0, 255)
                label = "RIGHT"
            else:
                color = (255, 0, 0)
                label = "LEFT"

            draw_polyline(
                image,
                points,
                color
            )

            # Label near first point.
            x, y = map(
                int,
                points[0]
            )

            cv2.putText(
                image,
                f"{label} {rail_number}",
                (x, y),
                cv2.FONT_HERSHEY_SIMPLEX,
                0.8,
                color,
                2,
                cv2.LINE_AA
            )

            rail_number += 1

        output_name = (
            f"{image_id}_{os.path.basename(image_name)}"
        )

        output_path = os.path.join(
            OUTPUT_DIR,
            output_name
        )

        cv2.imwrite(
            output_path,
            image
        )

        print(
            "Saved:",
            output_path
        )


if __name__ == "__main__":
    main()