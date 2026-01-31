import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.FileWriter;
import java.util.ArrayList;

public class ParkingAnnotator extends JPanel {

    Image background;
    ArrayList<Spot> spots = new ArrayList<>();

    Spot selected = null;

    int offsetX, offsetY;
    boolean resizing = false;
    int resizeMargin = 12;

    public ParkingAnnotator(String imagePath) {
        background = new ImageIcon(imagePath).getImage();

        MouseAdapter mouse = new MouseAdapter() {

            @Override
            public void mousePressed(MouseEvent e) {

                for (Spot s : spots) {
                    Rectangle r = new Rectangle(s.x, s.y, s.w, s.h);

                    if (r.contains(e.getPoint())) {
                        selected = s;

                        // bottom-right resize handle check
                        if (Math.abs(e.getX() - (s.x + s.w)) < resizeMargin &&
                            Math.abs(e.getY() - (s.y + s.h)) < resizeMargin) {

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

                if (selected != null) {

                    if (resizing) {
                        // Resize
                        selected.w = e.getX() - selected.x;
                        selected.h = e.getY() - selected.y;

                        // Prevent negative or tiny boxes
                        if (selected.w < 20) selected.w = 20;
                        if (selected.h < 20) selected.h = 20;

                    } else {
                        // Drag
                        selected.x = e.getX() - offsetX;
                        selected.y = e.getY() - offsetY;
                    }

                    repaint();
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                resizing = false;
            }
        };

        addMouseListener(mouse);
        addMouseMotionListener(mouse);
    }

    public boolean spotExists(String id) {
        for (Spot s : spots) {
            if (s.id.equalsIgnoreCase(id)) {
                return true;
            }
        }
        return false;
    }

    public void addSpot(String id) {

        if (spotExists(id)) {
            JOptionPane.showMessageDialog(this,
                    "Spot " + id + " already exists!",
                    "Duplicate Spot",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        spots.add(new Spot(id, 50, 50, 80, 160));
        repaint();
    }

    public void deleteSpot(String id) {

        for (int i = 0; i < spots.size(); i++) {
            if (spots.get(i).id.equalsIgnoreCase(id)) {
                spots.remove(i);

                JOptionPane.showMessageDialog(this,
                        "Deleted spot: " + id);

                repaint();
                return;
            }
        }

        JOptionPane.showMessageDialog(this,
                "No spot found with ID: " + id,
                "Delete Failed",
                JOptionPane.ERROR_MESSAGE);
    }

    public void exportJSON() {
        try {
            FileWriter fw = new FileWriter("spots.json");

            fw.write("{\n  \"spots\": [\n");

            for (int i = 0; i < spots.size(); i++) {
                Spot s = spots.get(i);

                fw.write("    { \"id\": \"" + s.id + "\", \"rect\": [" +
                        s.x + ", " + s.y + ", " + s.w + ", " + s.h + "] }");

                if (i < spots.size() - 1) fw.write(",");
                fw.write("\n");
            }

            fw.write("  ]\n}");
            fw.close();

            JOptionPane.showMessageDialog(this, "Exported spots.json!");

        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

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

        // Draw background
        g.drawImage(background, 0, 0, this);

        g.setColor(Color.RED);

        for (Spot s : spots) {

            // Rectangle
            g.drawRect(s.x, s.y, s.w, s.h);

            // Label
            g.drawString(s.id, s.x + 5, s.y + 15);

            // Resize handle square
            g.fillRect(s.x + s.w - 6, s.y + s.h - 6, 12, 12);
        }
    }

    public static void main(String[] args) {

        JFrame frame = new JFrame("Parking Spot Annotator");

        ParkingAnnotator panel = new ParkingAnnotator("frame.jpg");

        JTextField rowField = new JTextField(2);
        JTextField colField = new JTextField(2);

        JButton addBtn = new JButton("New Spot");
        JButton deleteBtn = new JButton("Delete Spot");
        JButton exportBtn = new JButton("Export JSON");

        // ADD spot button
        addBtn.addActionListener(e -> {
            String row = rowField.getText().toUpperCase();
            String col = colField.getText();

            if (row.isEmpty() || col.isEmpty()) {
                JOptionPane.showMessageDialog(panel,
                        "Enter row + col first!");
                return;
            }

            panel.addSpot(row + col);
        });

        // DELETE spot button
        deleteBtn.addActionListener(e -> {
            String row = rowField.getText().toUpperCase();
            String col = colField.getText();

            if (row.isEmpty() || col.isEmpty()) {
                JOptionPane.showMessageDialog(panel,
                        "Enter row + col first!");
                return;
            }

            panel.deleteSpot(row + col);
        });

        // EXPORT button
        exportBtn.addActionListener(e -> panel.exportJSON());

        JPanel controls = new JPanel();
        controls.add(new JLabel("Row:"));
        controls.add(rowField);
        controls.add(new JLabel("Col:"));
        controls.add(colField);

        controls.add(addBtn);
        controls.add(deleteBtn);
        controls.add(exportBtn);

        // Scrollpane fixes cutoff
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

// Spot class NOT public
class Spot {
    String id;
    int x, y, w, h;

    Spot(String id, int x, int y, int w, int h) {
        this.id = id;
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
    }
}
