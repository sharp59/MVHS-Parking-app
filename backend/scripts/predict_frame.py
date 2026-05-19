import json
from pathlib import Path

import cv2
import numpy as np

#model notes:
# promising mAlexNet: (smaller models)
#  model 4: 17/19 | all false pos, most likely can't rescale bc of confidences
#  model 5: 19/19 | no incorrect, super certain on occupied, some unoccupied hovers ~20-40 confidence so we can rescale to tighten it up
#  model 6: 12/19 | all false pos, all occupied confidence is >0.9990, highest unoccupised at 0.91, so rescale likely fixes
# 
# will probably choose between model 5 and 6
# TODO: tighten up confidence on 5, try rescaling 6 and fix the errors
# no promising AlexNet (bigger models)
# ==========================================================
# BACKGROUND SUBTRACTION (OPTIONAL FUTURE FEATURE)
# ----------------------------------------------------------
# When you capture an empty parking lot image, enable this:
#
# background = cv2.imread("background.jpg")
#
# Then later replace the crop line with:
#
# crop = diff[y:y+h, x:x+w].copy()
#
# ==========================================================


def find_first_model(repo_root: Path):
    """
    Looks for:
      repo_root/backend/models/method4/**.prototxt
      repo_root/backend/models/method4/**.caffemodel
    Returns (prototxt_path, caffemodel_path)
    """

    base = repo_root / "backend" / "models" / "method4"

    prototxts = list(base.rglob("*.prototxt"))
    caffes = list(base.rglob("*.caffemodel"))

    if not prototxts or not caffes:
        raise FileNotFoundError(
            f"Could not find .prototxt/.caffemodel under {base}. Check your folder location."
        )

    return prototxts[0], caffes[0]


def main():

    repo_root = Path(__file__).resolve().parents[2]
    backend_root = Path(__file__).resolve().parents[1]

    # -----------------------------
    # LOAD MODEL
    # -----------------------------

    prototxt, model = find_first_model(repo_root)

    net = cv2.dnn.readNetFromCaffe(str(prototxt), str(model))

    # -----------------------------
    # LOAD DATA
    # -----------------------------

    img_path = backend_root / "data" / "frame.jpg"
    spots_path = backend_root / "data" / "spots.json"

    img = cv2.imread(str(img_path))

    if img is None:
        raise FileNotFoundError(f"Could not read image: {img_path}")

    # ==========================================================
    # BACKGROUND SUBTRACTION (ENABLE LATER)
    #
    # diff = cv2.absdiff(img, background)
    #
    # ==========================================================

    with open(spots_path, "r") as f:
        spots = json.load(f)["spots"]

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

    for s in spots:

        sid = s.get("id", "<unknown>")

        real_occupied = bool(s.get("occupied", False))

        # Validate polygon
        poly = s.get("polygon")
        if not poly:
            print(f"{sid}: missing/empty polygon, skipping")
            continue

        try:
            pts = np.array(poly, np.int32)
        except Exception as exc:
            print(f"{sid}: invalid polygon data: {exc}")
            continue

        # Compute bounding rectangle BEFORE any use of x,y,w,h
        x, y, w, h = cv2.boundingRect(pts)

        # Skip degenerate boxes
        if w <= 0 or h <= 0:
            print(f"{sid}: degenerate bounding box (w={w}, h={h}), skipping")
            continue

        # Clamp to image bounds
        img_h, img_w = img.shape[:2]
        x = max(0, min(x, img_w - 1))
        y = max(0, min(y, img_h - 1))
        w = max(0, min(w, img_w - x))
        h = max(0, min(h, img_h - y))

        # Crop and mask
        crop = img[y:y+h, x:x+w].copy()

        mask = np.zeros((h, w), dtype=np.uint8)
        pts_shifted = pts - [x, y]
        cv2.fillPoly(mask, [pts_shifted], 255)
        patch = cv2.bitwise_and(crop, crop, mask=mask)

        if patch.size == 0:
            print(f"{sid}: EMPTY PATCH (out of bounds)")
            continue

        # Resize to CNN input
        patch = cv2.resize(patch, (INPUT_W, INPUT_H))

        blob = cv2.dnn.blobFromImage(
            patch,
            scalefactor=SCALE,
            size=(INPUT_W, INPUT_H),
            mean=MEAN,
            swapRB=SWAP_RB,
            crop=False,
        )

        net.setInput(blob)

        out = net.forward().flatten()

        if out.size != 2:
            raise RuntimeError(f"Expected 2 outputs, got {out.shape} for {sid}: {out}")

        p_unocc = float(out[0])
        p_occ = float(out[1])

        pred_occupied = p_occ > 0.5
        is_correct = pred_occupied == real_occupied

        debug_boxes.append({
            "rect": (x, y, w, h),
            "correct": is_correct,
            "id": sid
        })

        # keep real_occupied from the data (already set above)
        is_correct = (pred_occupied == real_occupied)

        total += 1

        if is_correct:
            correct += 1
        else:
            if pred_occupied and not real_occupied:
                false_pos += 1
                false_pos_list.append(sid)
            elif not pred_occupied and real_occupied:
                false_neg += 1
                false_neg_list.append(sid)

        pred_text = "occupied" if pred_occupied else "unoccupied"
        real_text = "occupied" if real_occupied else "unoccupied"

        line = (
            f"{sid}:\t[{p_unocc:.6f},{p_occ:.6f}], "
            f"pred={pred_text},\treal={real_text}, \tcorrect?={is_correct}"
        )

        print(line)

        output_lines.append({
            "sid": sid,
            "real": real_occupied,
            "line": line
        })

        results.append(
            {
                "id": sid,
                "predicted_occupied": pred_occupied,
                "real_occupied": real_occupied,
                "confidence_occupied": p_occ,
                "confidence_unoccupied": p_unocc,
                "correct": is_correct,
            }
        )

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