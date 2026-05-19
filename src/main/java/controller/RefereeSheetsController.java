package controller;

import model.Match;
import view.RefereeSheetsView;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import java.util.List;

/**
 * Controller for the referee sheet (Schiedsrichterzettel) feature.
 *
 * <p>Builds the UI via {@link RefereeSheetsView} and provides the data and print logic
 * for generating one referee sheet per match. Each sheet displays the two player names,
 * five set rows, and a total-result row.</p>
 */
public record RefereeSheetsController(List<Match> matches) {

    /**
     * Constructs the controller for the given list of matches and schedules UI initialization
     * on the Event Dispatch Thread.
     *
     * @param matches the list of matches (must not contain bye matches, i.e. second player != null)
     */
    public RefereeSheetsController(List<Match> matches) {
        this.matches = matches;
        SwingUtilities.invokeLater(this::initializeUI);
    }

    /**
     * Initializes and displays the {@link RefereeSheetsView}.
     */
    private void initializeUI() {
        new RefereeSheetsView(this, matches);
    }

    /**
     * Creates a read-only {@link DefaultTableModel} for the given match's referee sheet.
     * The model contains a header row with player names and rows for each set plus the total.
     *
     * @param match the match to create the sheet for
     * @return the table model for the referee sheet
     */
    public DefaultTableModel createRefereeSheetTableModel(Match match) {
        String[] columnNames = {"", match.getFirstPlayer().getFullName(), match.getSecondPlayer().getFullName()};
        Object[][] data = {
                {"Spieler", match.getFirstPlayer().getFullName(), match.getSecondPlayer().getFullName()},
                {"Satz 1", "", ""},
                {"Satz 2", "", ""},
                {"Satz 3", "", ""},
                {"Satz 4", "", ""},
                {"Satz 5", "", ""},
                {"Gesamtergebnis", "", ""}
        };
        return new DefaultTableModel(data, columnNames) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
    }

    /**
     * Opens the system print dialog and prints all referee sheets, fitting five sheets per page.
     * Shows an error dialog if printing fails.
     */
    public void printRefereeSheets() {
        PrinterJob job = PrinterJob.getPrinterJob();
        job.setJobName("Schiedsrichterzettel");

        job.setPrintable((graphics, pageFormat, pageIndex) -> {
            int columns = 2;
            int rows = 3;
            int sheetsPerPage = columns * rows;
            int totalPages = (int) Math.ceil((double) matches.size() / sheetsPerPage);

            if (pageIndex >= totalPages) {
                return Printable.NO_SUCH_PAGE;
            }

            Graphics2D g2 = (Graphics2D) graphics;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.translate(pageFormat.getImageableX(), pageFormat.getImageableY());

            int pageWidth = (int) pageFormat.getImageableWidth();
            int pageHeight = (int) pageFormat.getImageableHeight();

            int startIdx = pageIndex * sheetsPerPage;
            int endIdx = Math.min(startIdx + sheetsPerPage, matches.size());

            int hGap = 16;
            int vGap = 16;
            int cellWidth = (pageWidth - hGap * (columns - 1)) / columns;
            int cellHeight = (pageHeight - vGap * (rows - 1)) / rows;

            for (int i = startIdx; i < endIdx; i++) {
                int slot = i - startIdx;
                int col = slot % columns;
                int row = slot / columns;
                int xOffset = col * (cellWidth + hGap);
                int yOffset = row * (cellHeight + vGap);
                drawRefereeSheet(g2, matches.get(i), cellWidth, cellHeight, xOffset, yOffset);
            }

            return Printable.PAGE_EXISTS;
        });

        if (job.printDialog()) {
            try {
                job.print();
            } catch (PrinterException e) {
                JOptionPane.showMessageDialog(null, "Fehler beim Drucken der Schiedsrichterzettel.", "Druckfehler", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    /**
     * Draws a single referee sheet for the given match at the specified vertical offset.
     * The sheet includes a header with table number and player names, five set rows,
     * and a highlighted total-result row.
     *
     * @param g2     the graphics context to draw into
     * @param match  the match whose data is rendered
     * @param width  the available printable width in points
     * @param height the allocated height for this sheet in points
     * @param y      the vertical offset (top edge) at which to draw the sheet
     */
    private void drawRefereeSheet(Graphics2D g2, Match match, int width, int height, int x, int y) {
        final int innerPadding = 4;
        final int left = x + innerPadding;
        final int innerWidth = width - innerPadding * 2;
        final int boxHeight = height - innerPadding * 2;
        final int top = y + innerPadding;

        Font boldFont = new Font("Arial", Font.BOLD, 10);
        Font plainFont = new Font("Arial", Font.PLAIN, 10);
        Font titleFont = new Font("Arial", Font.BOLD, 12);

        g2.setColor(Color.BLACK);
        g2.setStroke(new BasicStroke(1.5f));
        g2.drawRect(left, top, innerWidth, boxHeight);

        int titleBarHeight = 24;
        g2.setColor(new Color(230, 235, 245));
        g2.fillRect(left + 1, top + 1, innerWidth - 1, titleBarHeight - 1);
        g2.setColor(Color.BLACK);

        g2.setFont(titleFont);
        String tableLabel = "Tisch " + match.getTableNumber();
        g2.drawString(tableLabel, left + 8, top + 16);

        g2.setStroke(new BasicStroke(1f));
        g2.drawLine(left, top + titleBarHeight, left + innerWidth, top + titleBarHeight);

        int contentY = top + titleBarHeight + 1;
        int labelColWidth = Math.min(90, innerWidth / 4);
        int playerColWidth = (innerWidth - labelColWidth) / 2;
        int col1X = left + labelColWidth;
        int col2X = col1X + playerColWidth;

        int headerHeight = 20;
        g2.setColor(new Color(210, 220, 240));
        g2.fillRect(left + 1, contentY, innerWidth - 1, headerHeight);
        g2.setColor(Color.BLACK);

        g2.setFont(boldFont);
        FontMetrics boldFm = g2.getFontMetrics();

        String p1 = match.getFirstPlayer().getFullName();
        String p2 = match.getSecondPlayer().getFullName();
        p1 = truncate(p1, boldFm, playerColWidth - 8);
        p2 = truncate(p2, boldFm, playerColWidth - 8);

        g2.drawString(p1, col1X + 4, contentY + 14);
        g2.drawString(p2, col2X + 4, contentY + 14);

        g2.drawLine(left, contentY + headerHeight, left + innerWidth, contentY + headerHeight);

        String[] rowLabels = {"Satz 1", "Satz 2", "Satz 3", "Satz 4", "Satz 5", "Gesamt"};
        int rowsStartY = contentY + headerHeight;
        int availableHeight = top + boxHeight - rowsStartY - 2;
        int rowHeight = Math.max(16, availableHeight / rowLabels.length);

        g2.setFont(plainFont);

        for (int i = 0; i < rowLabels.length; i++) {
            int rowY = rowsStartY + i * rowHeight;
            boolean isTotal = i == rowLabels.length - 1;

            if (isTotal) {
                g2.setColor(new Color(232, 245, 233));
                g2.fillRect(left + 1, rowY, innerWidth - 1, rowHeight);
                g2.setColor(Color.BLACK);
                g2.setFont(boldFont);
            } else {
                g2.setColor(i % 2 == 0 ? Color.WHITE : new Color(248, 248, 252));
                g2.fillRect(left + 1, rowY, innerWidth - 1, rowHeight);
                g2.setColor(Color.BLACK);
                g2.setFont(plainFont);
            }

            FontMetrics rowFm = g2.getFontMetrics();
            int textY = rowY + (rowHeight + rowFm.getAscent() - rowFm.getDescent()) / 2;
            g2.drawString(rowLabels[i], left + 6, textY);

            g2.setStroke(new BasicStroke(0.5f));
            g2.drawLine(left, rowY + rowHeight, left + innerWidth, rowY + rowHeight);
        }

        int gridBottom = rowsStartY + rowLabels.length * rowHeight;
        g2.setStroke(new BasicStroke(1f));
        g2.drawLine(col1X, contentY, col1X, gridBottom);
        g2.drawLine(col2X, contentY, col2X, gridBottom);

        g2.setStroke(new BasicStroke(1.5f));
        g2.setColor(Color.BLACK);
        g2.drawRect(left, top, innerWidth, boxHeight);
    }

    /**
     * Truncates {@code text} to fit within {@code maxWidth} pixels as measured by {@code fm},
     * appending an ellipsis ({@code ...}) if truncation is necessary.
     *
     * @param text     the text to potentially truncate
     * @param fm       the font metrics used to measure string width
     * @param maxWidth the maximum allowed width in pixels
     * @return the original text if it fits, or a truncated version with {@code ...} appended
     */
    private String truncate(String text, FontMetrics fm, int maxWidth) {
        if (fm.stringWidth(text) <= maxWidth) return text;
        String ellipsis = "...";
        while (text.length() > 1 && fm.stringWidth(text + ellipsis) > maxWidth) {
            text = text.substring(0, text.length() - 1);
        }
        return text + ellipsis;
    }
}
