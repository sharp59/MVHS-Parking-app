import argparse
import json
from pathlib import Path

import cv2
import numpy as np


# Model notes:
# method4: 17/19 | all false pos
# method5: 19/19 | best on validation
# method6: 12/19 | high occupied confidences


def find_first_model(repo_root: Path, method_name: str = "method5"):
    base = repo_root / "backend" / "models" / method_name

    prototxts = list(base.rglob("*.prototxt"))
    caffes = list(base.rglob("*.caffemodel"))

    if not prototxts or not caffes:
        raise FileNotFoundError(
            f"Could not find .prototxt/.caffemodel under {base}. Check your folder location."
        )

    return prototxts[0], caffes[0]


def main():
    parser = argparse.ArgumentParser(description="Predict parking spots from frame.jpg")
    parser.add_argument("--model-method", default="method5", help="model folder under backend/models (method4/method5/method6)")
    parser.add_argument("--threshold", type=float, default=0.5, help="confidence threshold for occupied decision")
    args = parser.parse_args()

    repo_root = Path(__file__).resolve().parents[2]
    backend_root = Path(__file__).resolve().parents[1]

    prototxt, model = find_first_model(repo_root, method_name=args.model_method)

    net = cv2.dnn.readNetFromCaffe(str(prototxt), str(model))

    img_path = backend_root / "data" / "frame.jpg"
    spots_path = backend_root / "data" / "spots.json"

    img = cv2.imread(str(img_path))

    if img is None:
        raise FileNotFoundError(f"Could not read image: {img_path}")

    with open(spots_path, "r") as f:
        spots = json.load(f).get("spots", [])

    INPUT_W, INPUT_H = 224, 224
    SCALE = 1 / 255.0
    MEAN = (0, 0, 0)
    SWAP_RB = False

    np.set_printoptions(precision=8, suppress=False)

    results = []
    debug_boxes = []
    output_lines = []

    correct = 0
    total = 0

    false_pos = 0
    false_neg = 0

    false_pos_list = []
    false_neg_list = []

    patches = []
    meta = []

    img_h, img_w = img.shape[:2]

    for s in spots:
        sid = s.get("id", "<unknown>")
        real_occupied = bool(s.get("occupied", False))
        poly = s.get("polygon")
        if not poly:
            print(f"{sid}: missing polygon, skipping")
            continue
        try:
            pts = np.array(poly, np.int32)
        except Exception as exc:
            print(f"{sid}: invalid polygon, skipping: {exc}")
            continue

        x, y, w, h = cv2.boundingRect(pts)
        if w <= 0 or h <= 0:
            print(f"{sid}: degenerate bbox, skipping")
            continue

        x = max(0, min(x, img_w - 1))
        y = max(0, min(y, img_h - 1))
        w = max(0, min(w, img_w - x))
        h = max(0, min(h, img_h - y))

        crop = img[y:y+h, x:x+w].copy()
        if crop.size == 0:
            print(f"{sid}: EMPTY CROP, skipping")
            continue

        mask = np.zeros((h, w), dtype=np.uint8)
        pts_shifted = pts - [x, y]
        cv2.fillPoly(mask, [pts_shifted], 255)
        patch = cv2.bitwise_and(crop, crop, mask=mask)
        if patch.size == 0:
            print(f"{sid}: EMPTY PATCH (masked out), skipping")
            continue

        patch = cv2.resize(patch, (INPUT_W, INPUT_H))
        patches.append(patch)
        meta.append({"sid": sid, "real": real_occupied, "rect": (x, y, w, h)})

    if len(patches) == 0:
        print("No valid patches to predict.")
    else:
        blob = cv2.dnn.blobFromImages(
            patches,
            scalefactor=SCALE,
            size=(INPUT_W, INPUT_H),
            mean=MEAN,
            swapRB=SWAP_RB,
            crop=False,
        )

        net.setInput(blob)
        outs = net.forward()

        # Ensure shape: (N, *) -> reshape to (N,2) if possible
        outs = np.array(outs)
        if outs.ndim > 2:
            outs = outs.reshape((outs.shape[0], -1))

        if outs.shape[0] != len(patches):
            raise RuntimeError(f"Mismatch: outputs {outs.shape[0]} != patches {len(patches)}")

        for i, out in enumerate(outs):
            sid = meta[i]["sid"]
            x, y, w, h = meta[i]["rect"]
            # handle case where model outputs channel order [unocc, occ]
            p_unocc = float(out[0])
            p_occ = float(out[1])
            pred_occupied = p_occ > args.threshold
            is_correct = pred_occupied == meta[i]["real"]

            debug_boxes.append({"rect": (x, y, w, h), "correct": is_correct, "id": sid})

            total += 1
            if is_correct:
                correct += 1
            else:
                if pred_occupied and not meta[i]["real"]:
                    false_pos += 1
                    false_pos_list.append(sid)
                elif not pred_occupied and meta[i]["real"]:
                    false_neg += 1
                    false_neg_list.append(sid)

            pred_text = "occupied" if pred_occupied else "unoccupied"
            real_text = "occupied" if meta[i]["real"] else "unoccupied"
            line = f"{sid}:\t[{p_unocc:.6f},{p_occ:.6f}], pred={pred_text},\treal={real_text}, \tcorrect?={is_correct}"
            print(line)
            output_lines.append({"sid": sid, "real": meta[i]["real"], "line": line})
            results.append({"id": sid, "predicted_occupied": pred_occupied, "real_occupied": meta[i]["real"], "confidence_occupied": p_occ, "confidence_unoccupied": p_unocc, "correct": is_correct})

    incorrect = total - correct

    print("\n==============================")
    print("RESULTS SORTED BY SPOT ID")
    print("==============================")

    for e in sorted(output_lines, key=lambda x: x["sid"]):
        print(e["line"])

    print("\n==============================")
    print("RESULTS GROUPED BY REAL VALUE")
    print("==============================")

    print("\n--- REAL: OCCUPIED ---")
    for e in output_lines:
        if e["real"]:
            print(e["line"])

    print("\n--- REAL: UNOCCUPIED ---")
    for e in output_lines:
        if not e["real"]:
            print(e["line"])

    print(f"\n{correct}/{total}")

    if incorrect > 0:
        print(f"{false_pos}/{incorrect} (thought occupied) -> {false_pos_list}")
        print(f"{false_neg}/{incorrect} (thought unoccupied) -> {false_neg_list}")
    else:
        print("0/0 (no incorrect predictions)")

    debug_img = img.copy()

    for b in debug_boxes:
        x, y, w, h = b["rect"]
        sid = b["id"]

        color = (0, 255, 0) if b["correct"] else (0, 0, 255)

        cv2.rectangle(debug_img, (x, y), (x + w, y + h), color, 2)

        cv2.putText(
            debug_img,
            sid,
            (x, max(y - 5, 15)),
            cv2.FONT_HERSHEY_SIMPLEX,
            0.5,
            color,
            1,
            cv2.LINE_AA,
        )

    debug_path = backend_root / "data" / "debug_boxes.jpg"
    cv2.imwrite(str(debug_path), debug_img)

    print("Wrote debug image:", debug_path)

    out_path = backend_root / "data" / "predictions.json"

    with open(out_path, "w") as f:
        json.dump(
            {
                "spots": results,
                "correct": correct,
                "total": total,
                "incorrect": incorrect,
                "false_positive": false_pos,
                "false_negative": false_neg,
                "false_positive_spots": false_pos_list,
                "false_negative_spots": false_neg_list,
            },
            f,
            indent=2,
        )

    print("Wrote:", out_path)


if __name__ == "__main__":
    main()
