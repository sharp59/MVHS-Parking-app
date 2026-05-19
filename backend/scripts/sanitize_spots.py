import json
from pathlib import Path


def sanitize(src_path: Path):
    p = Path(src_path)
    if not p.exists():
        print("File not found:", p)
        return
    data = json.loads(p.read_text())
    changed = False
    for s in data.get("spots", []):
        if "id" in s:
            new_id = str(s["id"]).strip()
            if new_id != s["id"]:
                s["id"] = new_id
                changed = True
    if changed:
        p.write_text(json.dumps(data, indent=2))
        print("Sanitized and wrote:", p)
    else:
        print("No changes needed.")


if __name__ == "__main__":
    sanitize(Path(__file__).resolve().parents[2] / "backend" / "data" / "spots.json")
