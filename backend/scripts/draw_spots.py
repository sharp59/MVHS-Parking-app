import json
import cv2
from pathlib import Path


def repo_root():
    """
    Walk upward until we find the repo root (folder containing 'backend').
    """
    p = Path(__file__).resolve()
    for parent in p.parents:
        if (parent / "backend").exists():
            return parent
    raise RuntimeError("Could not find repo root")


def main():
    root = repo_root()
    backend = root / "backend"

    img_path = backend / "data" / "frame.jpg"
    spots_path = backend / "data" / "spots.json"
    out_path = backend / "data" / "debug_boxes.jpg"

    print("Using image:", img_path)
    print("Using spots:", spots_path)

    img = cv2.imread(str(img_path))
    if img is None:
        raise FileNotFoundError(f"Could not read image: {img_path}")

    with open(spots_path, "r") as f:
        spots = json.load(f)["spots"]

    for s in spots:
        x, y, w, h = s["rect"]
        sid = s["id"]

        cv2.rectangle(
            img,
            (x, y),
            (x + w, y + h),
            (0, 255, 0),
            2,
        )

        cv2.putText(
            img,
            sid,
            (x, max(y - 5, 15)),
            cv2.FONT_HERSHEY_SIMPLEX,
            0.5,
            (0, 255, 0),
            1,
            cv2.LINE_AA,
)

    cv2.imwrite(str(out_path), img)
    print("Wrote", out_path)


if __name__ == "__main__":
    main()
