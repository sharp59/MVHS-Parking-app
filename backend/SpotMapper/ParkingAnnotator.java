import java.awt.*;
import java.awt.event.*;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import javax.swing.*;

/**
 * ParkingAnnotator (single file)
 *
 * - Background image: frame.jpg (in the same folder you run from, or adjust IMAGE_PATH)
 * - Imports/Exports JSON: backend/data/spots.json (no file chooser)
 * - Add spot by Row/Col (A + 1 => A1), prevents duplicates
 * - Delete spot by Row/Col, frees the ID so it can be re-added
 * - Drag to move
 * - Drag bottom-right handle to resize
 * - Occupancy textfield: true/false stored as "occupied" in JSON
 * - Scrollbars + frame.pack() so image isn't cut off
 */
public class ParkingAnnotator extends JPanel {

    
    private static final String IMAGE_PATH = "frame.jpg";
    private static final String SPOTS_JSON_PATH = "backend/data/spots.json";

    private final Image background;
    private final ArrayList<Spot> spots = new ArrayList<>();

    private Spot selected = null;

    private int offsetX, offsetY;
    private boolean resizing = false;
    private final int resizeMargin = 12;

    public ParkingAnnotator(String imagePath) {
        background = new ImageIcon(imagePath).getImage();

        MouseAdapter mouse = new MouseAdapter() {

            @Override
            public void mousePressed(MouseEvent e) {
                for (Spot s : spots) {
                    Rectangle r = new Rectangle(s.x, s.y, s.w, s.h);
                    if (r.contains(e.getPoint())) {
                        selected = s;

                        // bottom-right resize handle
                        boolean nearRight = Math.abs(e.getX() - (s.x + s.w)) < resizeMargin;
                        boolean nearBottom = Math.abs(e.getY() - (s.y + s.h)) < resizeMargin;

                        if (nearRight && nearBottom) {
                            resizing = true;
                        } else {
                            resizing = false;
                            offsetX = e.getX() - s.x;
                            offsetY = e.getY() - s.y;
                        }
                        return;
                    }
                }
                selected = null;
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (selected == null) return;

                if (resizing) {
                    selected.w = e.getX() - selected.x;
                    selected.h = e.getY() - selected.y;

                    // clamp to minimum size
                    if (selected.w < 20) selected.w = 20;
                    if (selected.h < 20) selected.h = 20;
                } else {
                    selected.x = e.getX() - offsetX;
                    selected.y = e.getY() - offsetY;
                }
                repaint();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                resizing = false;
            }
        };

        addMouseListener(mouse);
        addMouseMotionListener(mouse);
    }

    // ---------- Spot CRUD ----------

    private boolean spotExists(String id) {
        for (Spot s : spots) {
            if (s.id.equalsIgnoreCase(id)) return true;
        }
        return false;
    }

    private static boolean parseOccupancy(String text) {
        String t = text.trim().toLowerCase();
        if (t.equals("true") || t.equals("t") || t.equals("yes") || t.equals("y") || t.equals("1")) return true;
        if (t.equals("false") || t.equals("f") || t.equals("no") || t.equals("n") || t.equals("0")) return false;
        // default if blank or unknown
        return false;
    }

    public void addSpot(String id, boolean occupied) {
        if (spotExists(id)) {
            JOptionPane.showMessageDialog(
                    this,
                    "Spot " + id + " already exists!",
                    "Duplicate Spot",
                    JOptionPane.ERROR_MESSAGE
            );
            return;
        }
        spots.add(new Spot(id, 50, 50, 80, 160, occupied));
        repaint();
    }

    public void deleteSpot(String id) {
        for (int i = 0; i < spots.size(); i++) {
            if (spots.get(i).id.equalsIgnoreCase(id)) {
                spots.remove(i);
                selected = null;
                repaint();
                JOptionPane.showMessageDialog(this, "Deleted spot: " + id);
                return;
            }
        }
        JOptionPane.showMessageDialog(
                this,
                "No spot found with ID: " + id,
                "Delete Failed",
                JOptionPane.ERROR_MESSAGE
        );
    }

    // ---------- Import / Export (NO chooser) ----------

    public void importFromDataJson() {
        try {
            Path p = resolveRepoFile(SPOTS_JSON_PATH);
            if (!Files.exists(p)) {
                JOptionPane.showMessageDialog(
                        this,
                        "Could not find: " + p.toAbsolutePath(),
                        "Import Failed",
                        JOptionPane.ERROR_MESSAGE
                );
                return;
            }

            String text = Files.readString(p);

            // reset
            spots.clear();
            selected = null;

            // Minimal parsing for format:
            // { "spots": [ { "id": "A1", "rect": [x,y,w,h], "occupied": true }, ... ] }
            int arrStart = text.indexOf('[');
            int arrEnd = text.lastIndexOf(']');
            if (arrStart < 0 || arrEnd < 0 || arrEnd <= arrStart) {
                throw new RuntimeException("Invalid JSON: spots array not found");
            }

            String arr = text.substring(arrStart + 1, arrEnd).trim();
            if (arr.isEmpty()) {
                repaint();
                JOptionPane.showMessageDialog(this, "Imported 0 spots (empty file).");
                return;
            }

            String[] entries = arr.split("\\},\\s*\\{");

            for (String e : entries) {
                String entry = e.replace("{", "").replace("}", "").trim();

                // id
                String id = entry.replaceAll(".*\"id\"\\s*:\\s*\"([^\"]+)\".*", "$1").trim();
                if (id.equals(entry)) continue; // didn't match

                // rect
                int r0 = entry.indexOf('[');
                int r1 = entry.indexOf(']');
                if (r0 < 0 || r1 < 0 || r1 <= r0) continue;

                String[] nums = entry.substring(r0 + 1, r1).split(",");
                if (nums.length != 4) continue;

                int x = (int) Double.parseDouble(nums[0].trim());
                int y = (int) Double.parseDouble(nums[1].trim());
                int w = (int) Double.parseDouble(nums[2].trim());
                int h = (int) Double.parseDouble(nums[3].trim());

                // occupied (optional; default false)
                boolean occupied = false;
                String lowered = entry.toLowerCase();
                int occIdx = lowered.indexOf("\"occupied\"");
                if (occIdx >= 0) {
                    int colon = lowered.indexOf(":", occIdx);
                    if (colon >= 0) {
                        String tail = lowered.substring(colon + 1).trim();
                        // tail starts with true/false (or something)
                        if (tail.startsWith("true")) occupied = true;
                        else occupied = false;
                    }
                }

                if (!spotExists(id)) {
                    spots.add(new Spot(id, x, y, w, h, occupied));
                }
            }

            repaint();
            JOptionPane.showMessageDialog(this, "Imported " + spots.size() + " spots from data/spots.json!");

        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(
                    this,
                    "Import failed: " + ex.getMessage(),
                    "Error",
                    JOptionPane.ERROR_MESSAGE
            );
        }
    }

    public void exportToDataJson() {
        try {
            Path p = resolveRepoFile(SPOTS_JSON_PATH);

            // Ensure folder exists
            Path parent = p.getParent();
            if (parent != null) Files.createDirectories(parent);

            try (FileWriter fw = new FileWriter(p.toFile())) {
                fw.write("{\n  \"spots\": [\n");

                for (int i = 0; i < spots.size(); i++) {
                    Spot s = spots.get(i);

                    fw.write("    { \"id\": \"" + s.id + "\", \"rect\": [" +
                            s.x + ", " + s.y + ", " + s.w + ", " + s.h + "], " +
                            "\"occupied\": " + (s.occupied ? "true" : "false") + " }");

                    if (i < spots.size() - 1) fw.write(",");
                    fw.write("\n");
                }

                fw.write("  ]\n}\n");
            }

            JOptionPane.showMessageDialog(this, "Exported to: " + p.toAbsolutePath());

        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(
                    this,
                    "Export failed: " + ex.getMessage(),
                    "Error",
                    JOptionPane.ERROR_MESSAGE
            );
        }
    }

    // ---------- Drawing ----------

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(
                background.getWidth(this),
                background.getHeight(this)
        );
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        g.drawImage(background, 0, 0, this);

        for (Spot s : spots) {
            // green if empty, red if occupied (you can flip if you want)
            if (s.occupied) g.setColor(Color.RED);
            else g.setColor(new Color(0, 200, 0));

            g.drawRect(s.x, s.y, s.w, s.h);
            g.drawString(s.id + (s.occupied ? " (occ)" : " (empty)"), s.x + 5, s.y + 15);

            // resize handle (bottom-right)
            g.fillRect(s.x + s.w - 6, s.y + s.h - 6, 12, 12);
        }
    }

    private static Path resolveRepoFile(String relPath) {
        Path cwd = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        Path p = cwd;

        // walk up until we find ".git" OR the backend folder (repo root markers)
        while (p != null) {
            if (Files.exists(p.resolve(".git")) || Files.exists(p.resolve("backend"))) {
                return p.resolve(relPath).normalize();
            }
            p = p.getParent();
        }

        // fallback: just relative to cwd
        return cwd.resolve(relPath).normalize();
    }

    // ---------- Main ----------

    public static void main(String[] args) {
        JFrame frame = new JFrame("Parking Spot Annotator");

        ParkingAnnotator panel = new ParkingAnnotator(IMAGE_PATH);

        JTextField rowField = new JTextField(2);
        JTextField colField = new JTextField(3);

        JTextField occField = new JTextField(5);
        occField.setText("false"); // default

        JButton addBtn = new JButton("New Spot");
        JButton deleteBtn = new JButton("Delete Spot");
        JButton importBtn = new JButton("Import JSON");
        JButton exportBtn = new JButton("Export JSON");

        addBtn.addActionListener(e -> {
            String row = rowField.getText().trim().toUpperCase();
            String col = colField.getText().trim();
            String occText = occField.getText();

            if (row.isEmpty() || col.isEmpty()) {
                JOptionPane.showMessageDialog(panel, "Enter Row + Col first!");
                return;
            }

            boolean occupied = parseOccupancy(occText);
            panel.addSpot(row + col, occupied);
        });

        deleteBtn.addActionListener(e -> {
            String row = rowField.getText().trim().toUpperCase();
            String col = colField.getText().trim();

            if (row.isEmpty() || col.isEmpty()) {
                JOptionPane.showMessageDialog(panel, "Enter Row + Col first!");
                return;
            }
            panel.deleteSpot(row + col);
        });

        importBtn.addActionListener(e -> panel.importFromDataJson());
        exportBtn.addActionListener(e -> panel.exportToDataJson());

        JPanel controls = new JPanel();
        controls.add(new JLabel("Row:"));
        controls.add(rowField);
        controls.add(new JLabel("Col:"));
        controls.add(colField);

        controls.add(new JLabel("Occupied (true/false):"));
        controls.add(occField);

        controls.add(addBtn);
        controls.add(deleteBtn);
        controls.add(importBtn);
        controls.add(exportBtn);

        JScrollPane scroll = new JScrollPane(panel);

        frame.setLayout(new BorderLayout());
        frame.add(scroll, BorderLayout.CENTER);
        frame.add(controls, BorderLayout.SOUTH);

        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setVisible(true);
    }
}

// Spot class NOT public (allowed in same file)
class Spot {
    String id;
    int x, y, w, h;
    boolean occupied;

    Spot(String id, int x, int y, int w, int h, boolean occupied) {
        this.id = id;
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        this.occupied = occupied;
    }
}
