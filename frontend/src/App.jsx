import { useEffect, useState } from "react";

const SPOTS_JSON_URL = "/spots.json";

function parseId(id) {
  const m = id.match(/^([A-Za-z]+)(\d+)$/);
  if (!m) return null;
  return { row: m[1].toUpperCase(), col: parseInt(m[2], 10) };
}

export default function App() {
  const [spots, setSpots] = useState([]);
  const [err, setErr] = useState("");

  useEffect(() => {
    fetch(SPOTS_JSON_URL, { cache: "no-store" })
      .then((r) => {
        if (!r.ok) throw new Error(`HTTP ${r.status}`);
        return r.json();
      })
      .then((json) => setSpots(Array.isArray(json.spots) ? json.spots : []))
      .catch((e) => setErr(String(e)));
  }, []);

  // Build grid dimensions from spot IDs
  const parsed = spots
    .map((s) => ({ ...parseId(s.id), occupied: s.occupied, id: s.id }))
    .filter(Boolean);

  const rows = [...new Set(parsed.map((s) => s.row))].sort();
  const cols = [...new Set(parsed.map((s) => s.col))].sort((a, b) => a - b);

  // Map "A1" -> spot data
  const spotMap = new Map(parsed.map((s) => [`${s.row}${s.col}`, s]));

  const occupied = parsed.filter((s) => s.occupied).length;
  const empty = parsed.filter((s) => !s.occupied).length;

  return (
    <div style={styles.page}>
      <header style={styles.header}>
        <div style={styles.title}>MVHS Parking</div>
        <div style={styles.stats}>
          <span style={{ ...styles.badge, background: "rgba(0,200,80,0.18)", border: "1px solid rgba(0,200,80,0.5)" }}>
            {empty} open
          </span>
          <span style={{ ...styles.badge, background: "rgba(220,40,40,0.18)", border: "1px solid rgba(220,40,40,0.5)" }}>
            {occupied} occupied
          </span>
        </div>
      </header>

      {err && <div style={styles.error}>{err}</div>}

      <div style={styles.gridWrap}>
        {/* Column headers */}
        <div style={{ ...styles.row, marginBottom: 4 }}>
          <div style={styles.rowLabel} />
          {cols.map((col) => (
            <div key={col} style={styles.colHeader}>{col}</div>
          ))}
        </div>

        {/* Spot rows */}
        {rows.map((row) => (
          <div key={row} style={styles.row}>
            <div style={styles.rowLabel}>{row}</div>
            {cols.map((col) => {
              const spot = spotMap.get(`${row}${col}`);
              if (!spot) return <div key={col} style={styles.cellEmpty} />;
              return (
                <div
                  key={col}
                  style={{
                    ...styles.cell,
                    background: spot.occupied
                      ? "rgba(220,40,40,0.25)"
                      : "rgba(0,200,80,0.2)",
                    borderColor: spot.occupied
                      ? "rgba(220,40,40,0.7)"
                      : "rgba(0,200,80,0.6)",
                  }}
                  title={`${spot.id} — ${spot.occupied ? "occupied" : "open"}`}
                >
                  <span style={styles.spotId}>{col}</span>
                </div>
              );
            })}
          </div>
        ))}
      </div>

      <div style={styles.legend}>
        <span style={{ ...styles.legendDot, background: "rgba(0,200,80,0.3)", border: "1px solid rgba(0,200,80,0.7)" }} />
        <span style={styles.legendLabel}>Open</span>
        <span style={{ ...styles.legendDot, background: "rgba(220,40,40,0.3)", border: "1px solid rgba(220,40,40,0.7)", marginLeft: 16 }} />
        <span style={styles.legendLabel}>Occupied</span>
        <span style={{ ...styles.legendDot, background: "#1a1f2a", border: "1px dashed rgba(255,255,255,0.1)", marginLeft: 16 }} />
        <span style={styles.legendLabel}>No spot</span>
      </div>
    </div>
  );
}

const styles = {
  page: {
    fontFamily: "system-ui, -apple-system, Segoe UI, Roboto, Arial, sans-serif",
    padding: "24px 28px",
    color: "#eaeaea",
    background: "#0b0f14",
    minHeight: "100vh",
  },
  header: {
    display: "flex",
    alignItems: "center",
    justifyContent: "space-between",
    marginBottom: 28,
  },
  title: {
    fontSize: 26,
    fontWeight: 800,
    letterSpacing: "-0.5px",
  },
  stats: {
    display: "flex",
    gap: 10,
  },
  badge: {
    padding: "6px 14px",
    borderRadius: 999,
    fontSize: 14,
    fontWeight: 600,
  },
  error: {
    background: "rgba(255,0,0,0.12)",
    border: "1px solid rgba(255,0,0,0.35)",
    padding: 10,
    borderRadius: 10,
    marginBottom: 16,
    color: "#ffd0d0",
  },
  gridWrap: {
    display: "inline-flex",
    flexDirection: "column",
    gap: 6,
    background: "#0d1117",
    border: "1px solid rgba(255,255,255,0.08)",
    borderRadius: 14,
    padding: 20,
  },
  row: {
    display: "flex",
    alignItems: "center",
    gap: 6,
  },
  rowLabel: {
    width: 28,
    fontSize: 15,
    fontWeight: 700,
    color: "#8899bb",
    textAlign: "center",
    flexShrink: 0,
  },
  colHeader: {
    width: 52,
    height: 20,
    fontSize: 12,
    color: "#667799",
    textAlign: "center",
    fontWeight: 600,
    flexShrink: 0,
  },
  cell: {
    width: 52,
    height: 40,
    border: "1.5px solid",
    borderRadius: 6,
    display: "flex",
    alignItems: "center",
    justifyContent: "center",
    flexShrink: 0,
    cursor: "default",
    transition: "filter 0.15s",
  },
  cellEmpty: {
    width: 52,
    height: 40,
    borderRadius: 6,
    background: "#1a1f2a",
    border: "1px dashed rgba(255,255,255,0.07)",
    flexShrink: 0,
  },
  spotId: {
    fontSize: 11,
    opacity: 0.6,
    fontWeight: 600,
  },
  legend: {
    display: "flex",
    alignItems: "center",
    gap: 8,
    marginTop: 20,
    fontSize: 13,
    color: "#8899bb",
  },
  legendDot: {
    width: 18,
    height: 18,
    borderRadius: 4,
    display: "inline-block",
  },
  legendLabel: {
    fontSize: 13,
  },
};
