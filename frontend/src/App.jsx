import { useEffect, useMemo, useState } from "react";

/**
 * Put these files here:
 *  - frontend/public/lot.jpg
 *  - frontend/public/spots.json
 *
 * spots.json format:
 * {
 *   "spots": [
 *     { "id": "A1", "rect": [x, y, w, h] },
 *     ...
 *   ]
 * }
 *
 * Backend should return something like:
 * {
 *   "updated_at": "2026-01-31T05:00:00Z",
 *   "spots": [
 *     { "id": "A1", "occupied": true, "confidence": 0.996 },
 *     ...
 *   ]
 * }
 *
 * If your backend returns a different shape, tweak normalizeOccupancy().
 */

const API_URL = "http://127.0.0.1:8000/occupancy";
const BG_IMAGE_URL = "/lot.jpg";
const SPOTS_JSON_URL = "/spots.json";

function clamp01(x) {
  if (Number.isNaN(x)) return 0;
  return Math.max(0, Math.min(1, x));
}

function normalizeOccupancy(raw) {
  // Returns: { updatedAt: string|null, map: Map(id -> {occupied, confidence}) }
  const map = new Map();
  let updatedAt = null;

  if (!raw) return { updatedAt, map };

  // updated time
  if (typeof raw.updated_at === "string") updatedAt = raw.updated_at;
  else if (typeof raw.updatedAt === "string") updatedAt = raw.updatedAt;

  // spots array
  const arr = Array.isArray(raw.spots) ? raw.spots : [];

  for (const s of arr) {
    if (!s || typeof s.id !== "string") continue;

    // occupied can be boolean or 0/1
    const occupied =
      typeof s.occupied === "boolean"
        ? s.occupied
        : s.occupied === 1 || s.occupied === "1";

    const confidence = clamp01(Number(s.confidence ?? s.p_occ ?? s.pOcc ?? 0));

    map.set(s.id, { occupied, confidence });
  }

  return { updatedAt, map };
}

export default function App() {
  const [spotsDef, setSpotsDef] = useState([]); // [{id, rect:[x,y,w,h]}]
  const [occRaw, setOccRaw] = useState(null);
  const [err, setErr] = useState("");
  const [lastFetchMs, setLastFetchMs] = useState(null);

  // Load spots.json once
  useEffect(() => {
    let cancelled = false;

    (async () => {
      try {
        const res = await fetch(SPOTS_JSON_URL, { cache: "no-store" });
        if (!res.ok) throw new Error(`spots.json HTTP ${res.status}`);
        const json = await res.json();
        const spots = Array.isArray(json.spots) ? json.spots : [];
        if (!cancelled) setSpotsDef(spots);
      } catch (e) {
        if (!cancelled) setErr(`Failed to load spots.json: ${e}`);
      }
    })();

    return () => {
      cancelled = true;
    };
  }, []);

  // Poll occupancy
  useEffect(() => {
    let cancelled = false;

    const tick = async () => {
      try {
        const res = await fetch(API_URL, { cache: "no-store" });
        if (!res.ok) throw new Error(`API HTTP ${res.status}`);
        const json = await res.json();
        if (!cancelled) {
          setOccRaw(json);
          setErr("");
          setLastFetchMs(Date.now());
        }
      } catch (e) {
        if (!cancelled) setErr(String(e));
      }
    };

    tick();
    const id = setInterval(tick, 1000);
    return () => {
      cancelled = true;
      clearInterval(id);
    };
  }, []);

  const occ = useMemo(() => normalizeOccupancy(occRaw), [occRaw]);

  return (
    <div style={styles.page}>
      <header style={styles.header}>
        <div>
          <div style={styles.title}>MV Park Live Occupancy</div>
          <div style={styles.sub}>
            API: <code style={styles.code}>{API_URL}</code>
          </div>
          <div style={styles.sub}>
            Last fetch:{" "}
            {lastFetchMs ? new Date(lastFetchMs).toLocaleTimeString() : "—"}{" "}
            {occ.updatedAt ? `(backend updated_at: ${occ.updatedAt})` : ""}
          </div>
        </div>

        <div style={styles.legend}>
          <span style={{ ...styles.pill, background: "rgba(0,200,0,0.18)" }}>
            🟩 empty
          </span>
          <span style={{ ...styles.pill, background: "rgba(255,0,0,0.18)" }}>
            🟥 occupied
          </span>
        </div>
      </header>

      {err ? <div style={styles.error}>Error: {err}</div> : null}

      <div style={styles.stageWrap}>
        <div style={styles.stage}>
          {/* Background image */}
          <img
            src={BG_IMAGE_URL}
            alt="Parking lot"
            style={styles.bgImg}
            draggable={false}
          />

          {/* Overlays */}
          {spotsDef.map((s) => {
            const id = s?.id;
            const rect = s?.rect;

            if (!id || !Array.isArray(rect) || rect.length !== 4) return null;
            const [x, y, w, h] = rect.map((n) => Number(n));

            const info = occ.map.get(id);
            const occupied = info?.occupied ?? false;
            const confidence = info?.confidence ?? 0;

            const color = occupied ? "255,0,0" : "0,200,0";
            const border = `rgba(${color}, 0.85)`;
            const fill = `rgba(${color}, 0.18)`;

            return (
              <div
                key={id}
                style={{
                  ...styles.box,
                  left: x,
                  top: y,
                  width: w,
                  height: h,
                  borderColor: border,
                  background: fill,
                }}
                title={`${id} • ${occupied ? "occupied" : "empty"} • conf ${confidence.toFixed(
                  3
                )}`}
              >
                <div style={{ ...styles.label, borderColor: border }}>
                  {id}{" "}
                  <span style={styles.conf}>
                    {confidence ? confidence.toFixed(2) : "—"}
                  </span>
                </div>
              </div>
            );
          })}
        </div>
      </div>

      {/* Debug section */}
      <details style={styles.details}>
        <summary>Debug JSON</summary>
        <pre style={styles.pre}>{JSON.stringify(occRaw, null, 2)}</pre>
      </details>
    </div>
  );
}

const styles = {
  page: {
    fontFamily: "system-ui, -apple-system, Segoe UI, Roboto, Arial, sans-serif",
    padding: 16,
    color: "#eaeaea",
    background: "#0b0f14",
    minHeight: "100vh",
  },
  header: {
    display: "flex",
    gap: 16,
    justifyContent: "space-between",
    alignItems: "flex-start",
    marginBottom: 12,
  },
  title: { fontSize: 22, fontWeight: 800 },
  sub: { opacity: 0.8, fontSize: 13, marginTop: 4 },
  code: {
    background: "#111827",
    padding: "2px 6px",
    borderRadius: 6,
    color: "#93c5fd",
  },
  legend: { display: "flex", gap: 10, alignItems: "center" },
  pill: {
    padding: "6px 10px",
    borderRadius: 999,
    border: "1px solid rgba(255,255,255,0.15)",
    fontSize: 13,
  },
  error: {
    background: "rgba(255,0,0,0.12)",
    border: "1px solid rgba(255,0,0,0.35)",
    padding: 10,
    borderRadius: 10,
    marginBottom: 12,
    color: "#ffd0d0",
  },
  stageWrap: {
    border: "1px solid rgba(255,255,255,0.12)",
    borderRadius: 12,
    overflow: "auto",
    background: "#05070a",
    padding: 12,
  },
  stage: {
    position: "relative",
    display: "inline-block",
    lineHeight: 0,
  },
  bgImg: {
    display: "block",
    userSelect: "none",
    pointerEvents: "none",
    maxWidth: "none", // important so it doesn't shrink inside the scroll area
  },
  box: {
    position: "absolute",
    border: "2px solid",
    boxSizing: "border-box",
  },
  label: {
    position: "absolute",
    left: 6,
    top: 6,
    fontSize: 12,
    padding: "3px 6px",
    borderRadius: 8,
    background: "rgba(0,0,0,0.55)",
    border: "1px solid",
    lineHeight: 1.2,
    display: "inline-flex",
    gap: 8,
    alignItems: "center",
  },
  conf: { opacity: 0.75, fontSize: 11 },
  details: {
    marginTop: 12,
    opacity: 0.95,
  },
  pre: {
    marginTop: 8,
    background: "#0a1220",
    border: "1px solid rgba(255,255,255,0.12)",
    borderRadius: 12,
    padding: 12,
    overflow: "auto",
    color: "#a7f3d0",
  },
};