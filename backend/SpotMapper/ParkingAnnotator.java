import java.awt.*;
import java.awt.event.*;
import java.io.FileWriter;
import java.nio.file.*;
import java.util.*;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

public class ParkingAnnotator extends JPanel {

    private static final String IMAGE_PATH = "frame.jpg";
    private static final String SPOTS_JSON_PATH = "backend/data/spots.json";

    private Image background;

    private ArrayList<Spot> spots = new ArrayList<>();
    private ArrayList<Point> currentPolygon = new ArrayList<>();

    private Spot selectedSpot = null;

    private JTextField rowField = new JTextField(3);
    private JTextField colField = new JTextField(3);

    private int autoColCounter = 1;

    public ParkingAnnotator(String path) {

        background = new ImageIcon(path).getImage();

        setFocusable(true);
        setFocusTraversalKeysEnabled(false);

                
        rowField.getDocument().addDocumentListener(new DocumentListener() {

            private void reset() {
                autoColCounter = 1;
                colField.setText("1");
            }

            public void insertUpdate(DocumentEvent e) { reset(); }
            public void removeUpdate(DocumentEvent e) { reset(); }
            public void changedUpdate(DocumentEvent e) { reset(); }

        });

        MouseAdapter mouse = new MouseAdapter() {

            public void mousePressed(MouseEvent e) {
                requestFocusInWindow();
                Point click = new Point(e.getX(), e.getY());

                for (Spot s : spots) {
                    if (s.poly.contains(click)) {
                        selectedSpot = s;
                        repaint();
                        return;
                    }
                }

                selectedSpot = null;

                currentPolygon.add(click);

                if (currentPolygon.size() == 4) {

                    String row = rowField.getText().trim();
                    String col = colField.getText().trim();

                    if (col.isEmpty()) {
                        col = String.valueOf(autoColCounter);
                    }

                    if (!row.isEmpty()) {

                                            
                    String id = row + col;

                    if (spotIdExists(id)) {

                        JOptionPane.showMessageDialog(
                            ParkingAnnotator.this,
                            "Spot ID already exists: " + id
                        );

                    } else {

                        Polygon poly = sortPolygon(currentPolygon);

                        spots.add(new Spot(id, poly, false));

                        autoColCounter++;
                        colField.setText(String.valueOf(autoColCounter));
                    }
                    }

                    currentPolygon.clear();
                }

                repaint();
            }
        };

        addMouseListener(mouse);

        addKeyListener(new KeyAdapter() {

            public void keyPressed(KeyEvent e) {

                if (e.getKeyCode() == KeyEvent.VK_D && selectedSpot != null) {

                    spots.remove(selectedSpot);
                    selectedSpot = null;
                    repaint();
                }

                if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {

                    currentPolygon.clear();
                    repaint();
                }

                if (e.getKeyCode() == KeyEvent.VK_O && selectedSpot != null) {

                    selectedSpot.occupied = !selectedSpot.occupied;
                    repaint();
                }
            }
        });
    }

    
    private boolean spotIdExists(String id) {

        for (Spot s : spots) {
            if (s.id.equalsIgnoreCase(id)) {
                return true;
            }
        }

        return false;
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

        pts.sort((a,b)->{

            double angleA = Math.atan2(a.y-fcy,a.x-fcx);
            double angleB = Math.atan2(b.y-fcy,b.x-fcx);

            return Double.compare(angleA,angleB);
        });

        Polygon poly = new Polygon();

        for(Point p:pts)
            poly.addPoint(p.x,p.y);

        return poly;
    }

    private static String escapeJsonString(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"':  sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\b': sb.append("\\b");  break;
                case '\f': sb.append("\\f");  break;
                case '\n': sb.append("\\n");  break;
                case '\r': sb.append("\\r");  break;
                case '\t': sb.append("\\t");  break;
                default:
                    if (c < 32) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }
    
    public void exportToDataJson() {

        try {

            Path p = resolveRepoFile(SPOTS_JSON_PATH);

            Files.createDirectories(p.getParent());

            FileWriter fw = new FileWriter(p.toFile());

            fw.write("{\n \"spots\": [\n");

            for (int i=0;i<spots.size();i++) {

                Spot s = spots.get(i);

                fw.write("  { \"id\":\""+escapeJsonString(s.id)+"\", \"polygon\":[");

                for (int j=0;j<s.poly.npoints;j++) {

                    fw.write("["+s.poly.xpoints[j]+","+s.poly.ypoints[j]+"]");

                    if (j<s.poly.npoints-1)
                        fw.write(",");
                }

                fw.write("], \"occupied\": "+s.occupied+" }");

                if (i<spots.size()-1)
                    fw.write(",");

                fw.write("\n");
            }

            fw.write(" ]\n}");

            fw.close();

            JOptionPane.showMessageDialog(this,"Exported!");

        } catch(Exception ex) {

            ex.printStackTrace();
        }
    }

    public void importFromDataJson() {

        try {

            Path p = resolveRepoFile(SPOTS_JSON_PATH);

            if (!Files.exists(p))
                return;

            String txt = Files.readString(p);

            spots.clear();

            String[] entries = txt.split("\\{");

            for (String e : entries) {

                if (!e.contains("polygon"))
                    continue;

                String id = e.replaceAll(".*\"id\"\\s*:\\s*\"([^\"]+)\".*", "$1");

                ArrayList<Point> pts = new ArrayList<>();

                String poly = e.substring(e.indexOf("[[")+2,e.indexOf("]]"));

                String[] pairs = poly.split("\\],\\[");

                for(String pair:pairs){

                    String[] xy=pair.split(",");

                    pts.add(new Point(
                            Integer.parseInt(xy[0]),
                            Integer.parseInt(xy[1])
                    ));
                }

                Polygon polygon=new Polygon();

                for(Point pt:pts)
                    polygon.addPoint(pt.x,pt.y);

                boolean occ=e.contains("\"occupied\": true");

                spots.add(new Spot(id,polygon,occ));
            }

            
            int maxCol = 0;

            String row = rowField.getText().trim();

            for (Spot s : spots) {

                if (s.id.startsWith(row)) {

                    try {

                        int col = Integer.parseInt(s.id.substring(row.length()));

                        if (col > maxCol)
                            maxCol = col;

                    } catch (Exception ignore) {}
                }
            }

            autoColCounter = maxCol + 1;
            colField.setText(String.valueOf(autoColCounter));

            repaint();

        } catch(Exception ex) {

            ex.printStackTrace();
        }
    }

    protected void paintComponent(Graphics g) {

        super.paintComponent(g);

        g.drawImage(background,0,0,this);

        for (Spot s : spots) {

            if (s == selectedSpot)
                g.setColor(Color.YELLOW);
            else if (s.occupied)
                g.setColor(Color.RED);
            else
                g.setColor(Color.GREEN);

            g.drawPolygon(s.poly);

            Rectangle r = s.poly.getBounds();

            g.drawString(s.id,r.x+5,r.y+15);
        }

        g.setColor(Color.CYAN);

        for(int i=0;i<currentPolygon.size();i++){

            Point p=currentPolygon.get(i);

            g.fillOval(p.x-3,p.y-3,6,6);

            if(i>0){

                Point prev=currentPolygon.get(i-1);

                g.drawLine(prev.x,prev.y,p.x,p.y);
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

        Path p=cwd;

        while(p!=null){

            if(Files.exists(p.resolve("backend")))
                return p.resolve(rel);

            p=p.getParent();
        }

        return cwd.resolve(rel);
    }

    public static void main(String[] args) {

        JFrame frame=new JFrame("Parking Annotator");

        ParkingAnnotator panel=new ParkingAnnotator(IMAGE_PATH);

        JButton export=new JButton("Export JSON");
        JButton importBtn=new JButton("Import JSON");

        export.addActionListener(e->panel.exportToDataJson());
        importBtn.addActionListener(e->panel.importFromDataJson());

        JPanel controls=new JPanel();

        controls.add(new JLabel("Row:"));
        controls.add(panel.rowField);

        controls.add(new JLabel("Col:"));
        controls.add(panel.colField);

        controls.add(export);
        controls.add(importBtn);

        frame.setLayout(new BorderLayout());

        frame.add(new JScrollPane(panel),BorderLayout.CENTER);
        frame.add(controls,BorderLayout.SOUTH);

        frame.pack();

        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        frame.setVisible(true);
    }
}

class Spot {

    String id;
    Polygon poly;
    boolean occupied;

    Spot(String id,Polygon poly,boolean occ){

        this.id=id;
        this.poly=poly;
        this.occupied=occ;
    }
}