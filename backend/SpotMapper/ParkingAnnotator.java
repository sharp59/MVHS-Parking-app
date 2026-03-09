import java.awt.*;
import java.awt.event.*;
import java.io.FileWriter;
import java.nio.file.*;
import java.util.*;
import javax.swing.*;

public class ParkingAnnotator extends JPanel {

    private static final String IMAGE_PATH = "frame.jpg";
    private static final String SPOTS_JSON_PATH = "backend/data/spots.json";

    private Image background;

    private ArrayList<Spot> spots = new ArrayList<>();
    private ArrayList<Point> currentPolygon = new ArrayList<>();

    public ParkingAnnotator(String path) {

        background = new ImageIcon(path).getImage();

        MouseAdapter mouse = new MouseAdapter() {

            public void mousePressed(MouseEvent e) {

                // RIGHT CLICK = finish polygon
                if (SwingUtilities.isRightMouseButton(e)) {

                    if (currentPolygon.size() >= 3) {

                        String id = JOptionPane.showInputDialog("Spot ID:");

                        if (id != null && !id.isEmpty()) {

                            Polygon poly = sortPolygon(currentPolygon);

                            spots.add(new Spot(id, poly, false));
                        }
                    }

                    currentPolygon.clear();
                    repaint();
                    return;
                }

                // LEFT CLICK = add point
                currentPolygon.add(e.getPoint());

                repaint();
            }
        };

        addMouseListener(mouse);
    }

    private Polygon sortPolygon(ArrayList<Point> pts) {

        double cx = 0;
        double cy = 0;

        for (Point p : pts) {
            cx += p.x;
            cy += p.y;
        }

        cx /= pts.size();
        cy /= pts.size();

        final double fcx = cx;
        final double fcy = cy;

        pts.sort((a, b) -> {

            double angleA = Math.atan2(a.y - fcy, a.x - fcx);
            double angleB = Math.atan2(b.y - fcy, b.x - fcx);

            return Double.compare(angleA, angleB);
        });

        Polygon poly = new Polygon();

        for (Point p : pts)
            poly.addPoint(p.x, p.y);

        return poly;
    }

    public void exportToDataJson() {

        try {

            Path p = resolveRepoFile(SPOTS_JSON_PATH);
            Files.createDirectories(p.getParent());

            FileWriter fw = new FileWriter(p.toFile());

            fw.write("{\n \"spots\": [\n");

            for (int i = 0; i < spots.size(); i++) {

                Spot s = spots.get(i);

                fw.write("  { \"id\":\"" + s.id + "\", \"polygon\":[");

                for (int j = 0; j < s.poly.npoints; j++) {

                    fw.write("[" + s.poly.xpoints[j] + "," + s.poly.ypoints[j] + "]");

                    if (j < s.poly.npoints - 1)
                        fw.write(",");
                }

                fw.write("], \"occupied\": " + s.occupied + " }");

                if (i < spots.size() - 1)
                    fw.write(",");

                fw.write("\n");
            }

            fw.write(" ]\n}");

            fw.close();

            JOptionPane.showMessageDialog(this, "Exported!");

        } catch (Exception ex) {

            ex.printStackTrace();
        }
    }

    public void importFromDataJson() {

        try {

            Path p = resolveRepoFile(SPOTS_JSON_PATH);

            String txt = Files.readString(p);

            spots.clear();

            String[] entries = txt.split("\\{");

            for (String e : entries) {

                if (!e.contains("polygon"))
                    continue;

                String id = e.replaceAll(".*\"id\"\\s*:\\s*\"([^\"]+)\".*", "$1");

                ArrayList<Point> pts = new ArrayList<>();

                String poly = e.substring(e.indexOf("[[") + 2, e.indexOf("]]"));

                String[] pairs = poly.split("\\],\\[");

                for (String pair : pairs) {

                    String[] xy = pair.split(",");

                    pts.add(new Point(
                            Integer.parseInt(xy[0]),
                            Integer.parseInt(xy[1])
                    ));
                }

                Polygon polygon = new Polygon();

                for (Point pt : pts)
                    polygon.addPoint(pt.x, pt.y);

                boolean occ = e.contains("\"occupied\": true");

                spots.add(new Spot(id, polygon, occ));
            }

            repaint();

        } catch (Exception ex) {

            ex.printStackTrace();
        }
    }

    protected void paintComponent(Graphics g) {

        super.paintComponent(g);

        g.drawImage(background, 0, 0, this);

        for (Spot s : spots) {

            g.setColor(s.occupied ? Color.RED : new Color(0, 200, 0));

            g.drawPolygon(s.poly);

            Rectangle r = s.poly.getBounds();

            g.drawString(s.id, r.x + 5, r.y + 15);
        }

        g.setColor(Color.YELLOW);

        for (int i = 0; i < currentPolygon.size(); i++) {

            Point p = currentPolygon.get(i);

            g.fillOval(p.x - 3, p.y - 3, 6, 6);

            if (i > 0) {

                Point prev = currentPolygon.get(i - 1);

                g.drawLine(prev.x, prev.y, p.x, p.y);
            }
        }
    }

    public Dimension getPreferredSize() {

        return new Dimension(
                background.getWidth(this),
                background.getHeight(this)
        );
    }

    private static Path resolveRepoFile(String rel) {

        Path cwd = Path.of(System.getProperty("user.dir")).toAbsolutePath();

        Path p = cwd;

        while (p != null) {

            if (Files.exists(p.resolve("backend")))
                return p.resolve(rel);

            p = p.getParent();
        }

        return cwd.resolve(rel);
    }

    public static void main(String[] args) {

        JFrame frame = new JFrame("Parking Annotator");

        ParkingAnnotator panel = new ParkingAnnotator(IMAGE_PATH);

        JButton export = new JButton("Export JSON");
        JButton importBtn = new JButton("Import JSON");

        export.addActionListener(e -> panel.exportToDataJson());
        importBtn.addActionListener(e -> panel.importFromDataJson());

        JPanel controls = new JPanel();

        controls.add(export);
        controls.add(importBtn);

        frame.setLayout(new BorderLayout());

        frame.add(new JScrollPane(panel), BorderLayout.CENTER);
        frame.add(controls, BorderLayout.SOUTH);

        frame.pack();

        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        frame.setVisible(true);
    }
}

class Spot {

    String id;
    Polygon poly;
    boolean occupied;

    Spot(String id, Polygon poly, boolean occ) {

        this.id = id;
        this.poly = poly;
        this.occupied = occ;
    }
}