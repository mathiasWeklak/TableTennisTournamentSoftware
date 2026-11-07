package view;

import controller.BracketController;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.geom.Line2D;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import java.util.List;

/**
 * Modeless dialog showing the knockout bracket (Turnierbaum), drawn with Java2D. The same drawing
 * is used for printing on one landscape page.
 */
public class BracketView extends JDialog {

    private final BracketPanel panel;
    private final String tournamentName;

    /**
     * Creates the dialog.
     *
     * @param owner          the tournament window
     * @param tournamentName the tournament name
     * @param model          the bracket to show
     */
    public BracketView(Window owner, String tournamentName, BracketController.Model model) {
        super(owner, "Turnierbaum – " + tournamentName, ModalityType.MODELESS);
        this.tournamentName = tournamentName;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        UITheme.sizeToScreen(this, 1000, 640);
        setLocationRelativeTo(owner);

        JPanel content = new JPanel(new BorderLayout());
        content.setBackground(UITheme.BACKGROUND);
        content.setBorder(new EmptyBorder(12, 12, 12, 12));
        setContentPane(content);

        panel = new BracketPanel(model);
        JScrollPane scroll = new JScrollPane(panel);
        scroll.getViewport().setBackground(UITheme.SURFACE);
        content.add(scroll, BorderLayout.CENTER);

        JButton print = UITheme.createPrimaryButton("Turnierbaum drucken");
        print.addActionListener(_ -> print());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 6));
        buttons.setBackground(UITheme.BACKGROUND);
        buttons.add(print);
        content.add(buttons, BorderLayout.SOUTH);
    }

    /**
     * Shows a new state of the bracket.
     *
     * @param model the bracket
     */
    public void setModel(BracketController.Model model) {
        panel.setModel(model);
    }

    /**
     * Returns the drawing as printable (used for printing and tests).
     *
     * @return the printable
     */
    public Printable printable() {
        return panel.printable("Turnierbaum: " + tournamentName);
    }

    private void print() {
        try {
            PrinterJob job = PrinterJob.getPrinterJob();
            job.setJobName("Turnierbaum - " + tournamentName);
            PageFormat format = job.defaultPage();
            format.setOrientation(PageFormat.LANDSCAPE);
            job.setPrintable(printable(), job.validatePage(format));
            if (job.printDialog()) job.print();
        } catch (PrinterException e) {
            JOptionPane.showMessageDialog(this, "Fehler beim Drucken.", "Druckfehler", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * The drawing area of the bracket.
     */
    static final class BracketPanel extends JPanel {
        private int boxWidth = 230;
        private static final int ROW_H = 22;
        private static final int BOX_H = 2 * ROW_H;
        private static final int GAP_X = 46;
        private static final int GAP_Y = 18;
        private static final int TOP = 44;
        private static final int LEFT = 16;
        private static final Color WINNER_BG = new Color(220, 237, 221);
        private static final Color LINE = new Color(120, 130, 145);

        private BracketController.Model model;

        BracketPanel(BracketController.Model model) {
            setBackground(UITheme.SURFACE);
            setModel(model);
        }

        void setModel(BracketController.Model model) {
            this.model = model;
            // the boxes are as wide as the longest name (doubles names are long)
            FontMetrics fm = getFontMetrics(UITheme.FONT_BODY.deriveFont(Font.BOLD));
            int longest = 0;
            for (List<BracketController.Box> round : model.rounds()) {
                for (BracketController.Box b : round) longest = Math.max(longest, Math.max(fm.stringWidth(b.top()), fm.stringWidth(b.bottom())));
            }
            if (model.thirdPlace() != null) {
                longest = Math.max(longest, Math.max(fm.stringWidth(model.thirdPlace().top()), fm.stringWidth(model.thirdPlace().bottom())));
            }
            boxWidth = Math.max(200, Math.min(380, longest + 40));
            setPreferredSize(computeSize());
            revalidate();
            repaint();
        }

        private Dimension computeSize() {
            if (model.placeholder() != null) return new Dimension(600, 120);
            int rounds = model.rounds().size();
            int firstRound = model.rounds().isEmpty() ? 0 : model.rounds().getFirst().size();
            int height = TOP + firstRound * (BOX_H + GAP_Y) + 20;
            if (model.thirdPlace() != null) height = Math.max(height, thirdPlaceY() + BOX_H + 30);
            return new Dimension(LEFT * 2 + rounds * boxWidth + (rounds - 1) * GAP_X, height);
        }

        /** Vertical centre of every box, per round. */
        private double[][] centers() {
            List<List<BracketController.Box>> rounds = model.rounds();
            double[][] c = new double[rounds.size()][];
            for (int r = 0; r < rounds.size(); r++) {
                c[r] = new double[rounds.get(r).size()];
                for (int i = 0; i < c[r].length; i++) {
                    c[r][i] = r == 0 ? TOP + i * (BOX_H + GAP_Y) + BOX_H / 2.0 : (c[r - 1][2 * i] + c[r - 1][2 * i + 1]) / 2;
                }
            }
            return c;
        }

        private int thirdPlaceY() {
            double[][] c = centers();
            double finalCenter = c[c.length - 1][0];
            return (int) finalCenter + BOX_H + 40;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            draw((Graphics2D) g);
        }

        private void draw(Graphics2D g2) {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            if (model.placeholder() != null) {
                g2.setColor(UITheme.TEXT_MUTED);
                g2.setFont(UITheme.FONT_BODY);
                drawWrapped(g2, model.placeholder(), LEFT, 40, 560);
                return;
            }
            double[][] c = centers();
            for (int r = 0; r < model.rounds().size(); r++) {
                int x = LEFT + r * (boxWidth + GAP_X);
                g2.setFont(UITheme.FONT_SUBTITLE);
                g2.setColor(UITheme.PRIMARY);
                g2.drawString(model.roundNames().get(r), x, 24);
                List<BracketController.Box> boxes = model.rounds().get(r);
                for (int i = 0; i < boxes.size(); i++) {
                    int y = (int) Math.round(c[r][i] - BOX_H / 2.0);
                    drawBox(g2, boxes.get(i), x, y);
                    if (r + 1 < model.rounds().size()) {
                        // connector to the next round
                        double xr = x + boxWidth;
                        double xm = xr + GAP_X / 2.0;
                        double target = c[r + 1][i / 2];
                        g2.setColor(LINE);
                        g2.setStroke(new BasicStroke(1.2f));
                        g2.draw(new Line2D.Double(xr, c[r][i], xm, c[r][i]));
                        g2.draw(new Line2D.Double(xm, c[r][i], xm, target));
                        if (i % 2 == 0) g2.draw(new Line2D.Double(xm, target, xm + GAP_X / 2.0, target));
                    }
                }
            }
            if (model.thirdPlace() != null) {
                int x = LEFT + (model.rounds().size() - 1) * (boxWidth + GAP_X);
                int y = thirdPlaceY();
                g2.setFont(UITheme.FONT_SUBTITLE);
                g2.setColor(UITheme.PRIMARY);
                g2.drawString("Spiel um Platz 3", x, y - 8);
                drawBox(g2, model.thirdPlace(), x, y);
            }
        }

        private void drawBox(Graphics2D g2, BracketController.Box box, int x, int y) {
            drawRow(g2, box.top(), box.topSets(), box.winner() == 1, box.winner() == 2, x, y);
            drawRow(g2, box.bottom(), box.bottomSets(), box.winner() == 2, box.winner() == 1, x, y + ROW_H);
            g2.setColor(UITheme.BORDER_COLOR.darker());
            g2.setStroke(new BasicStroke(1f));
            g2.drawRect(x, y, boxWidth, BOX_H);
            g2.drawLine(x, y + ROW_H, x + boxWidth, y + ROW_H);
        }

        private void drawRow(Graphics2D g2, String name, String sets, boolean won, boolean lost, int x, int y) {
            g2.setColor(won ? WINNER_BG : UITheme.SURFACE);
            g2.fillRect(x, y, boxWidth, ROW_H);
            g2.setFont(won ? UITheme.FONT_BODY.deriveFont(Font.BOLD) : UITheme.FONT_BODY);
            g2.setColor(lost || "Freilos".equals(name) ? UITheme.TEXT_MUTED : UITheme.TEXT_PRIMARY);
            FontMetrics fm = g2.getFontMetrics();
            int baseline = y + (ROW_H + fm.getAscent() - fm.getDescent()) / 2;
            int setsWidth = 22;
            g2.drawString(truncate(name, fm, boxWidth - setsWidth - 12), x + 6, baseline);
            if (!sets.isEmpty()) {
                g2.drawString(sets, x + boxWidth - setsWidth + (setsWidth - fm.stringWidth(sets)) / 2, baseline);
            }
        }

        private static String truncate(String text, FontMetrics fm, int max) {
            if (fm.stringWidth(text) <= max) return text;
            String t = text;
            while (t.length() > 1 && fm.stringWidth(t + "…") > max) t = t.substring(0, t.length() - 1);
            return t + "…";
        }

        private static void drawWrapped(Graphics2D g2, String text, int x, int y, int width) {
            FontMetrics fm = g2.getFontMetrics();
            StringBuilder line = new StringBuilder();
            for (String word : text.split(" ")) {
                if (fm.stringWidth(line + word) > width && !line.isEmpty()) {
                    g2.drawString(line.toString(), x, y);
                    y += fm.getHeight();
                    line.setLength(0);
                }
                line.append(word).append(' ');
            }
            g2.drawString(line.toString(), x, y);
        }

        Printable printable(String title) {
            return (graphics, format, pageIndex) -> {
                if (pageIndex > 0) return Printable.NO_SUCH_PAGE;
                Graphics2D g2 = (Graphics2D) graphics.create();
                try {
                    g2.translate(format.getImageableX(), format.getImageableY());
                    g2.setFont(UITheme.FONT_SUBTITLE.deriveFont(16f));
                    g2.setColor(Color.BLACK);
                    g2.drawString(title, 0, 18);
                    Dimension size = computeSize();
                    double availableH = format.getImageableHeight() - 30;
                    double scale = Math.min(1.0, Math.min(format.getImageableWidth() / size.width, availableH / size.height));
                    g2.translate(0, 30);
                    g2.scale(scale, scale);
                    draw(g2);
                } finally {
                    g2.dispose();
                }
                return Printable.PAGE_EXISTS;
            };
        }
    }
}
