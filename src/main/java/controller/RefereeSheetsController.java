package controller;

import model.Match;
import view.RefereeSheetsView;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import java.util.List;

public class RefereeSheetsController {

    private final List<Match> matches;

    public RefereeSheetsController(List<Match> matches) {
        this.matches = matches;
        SwingUtilities.invokeLater(this::initializeUI);
    }

    private void initializeUI() {
        new RefereeSheetsView(this, matches);
    }

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

    public void printRefereeSheets() {
        PrinterJob job = PrinterJob.getPrinterJob();
        job.setJobName("Schiedsrichterzettel");

        job.setPrintable((graphics, pageFormat, pageIndex) -> {
            int sheetsPerPage = 2;
            int totalPages = (int) Math.ceil((double) matches.size() / sheetsPerPage);

            if (pageIndex >= totalPages) {
                return Printable.NO_SUCH_PAGE;
            }

            Graphics2D g2 = (Graphics2D) graphics;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.translate(pageFormat.getImageableX(), pageFormat.getImageableY());

            int pageWidth  = (int) pageFormat.getImageableWidth();
            int pageHeight = (int) pageFormat.getImageableHeight();

            int startIdx = pageIndex * sheetsPerPage;
            int endIdx   = Math.min(startIdx + sheetsPerPage, matches.size());

            int gap = 20;
            int sheetHeight = (pageHeight - gap * (sheetsPerPage - 1)) / sheetsPerPage;

            for (int i = startIdx; i < endIdx; i++) {
                int yOffset = (i - startIdx) * (sheetHeight + gap);
                drawRefereeSheet(g2, matches.get(i), pageWidth, sheetHeight, 0, yOffset);
            }

            return Printable.PAGE_EXISTS;
        });

        if (job.printDialog()) {
            try {
                job.print();
            } catch (PrinterException ignored) {
            }
        }
    }

    private void drawRefereeSheet(Graphics2D g2, Match match, int width, int height, int x, int y) {
        final int margin     = 8;
        final int innerX     = x + margin;
        final int innerWidth = width - margin * 2;

        Font boldFont  = new Font("Arial", Font.BOLD, 11);
        Font plainFont = new Font("Arial", Font.PLAIN, 11);
        Font titleFont = new Font("Arial", Font.BOLD, 13);

        g2.setColor(Color.BLACK);
        g2.setStroke(new BasicStroke(1.5f));
        g2.drawRect(innerX, y, innerWidth, height - 4);

        g2.setColor(new Color(230, 235, 245));
        g2.fillRect(innerX + 1, y + 1, innerWidth - 1, 26);
        g2.setColor(Color.BLACK);

        g2.setFont(titleFont);
        FontMetrics titleFm = g2.getFontMetrics();
        String tableLabel = "Tisch " + match.getTableNumber();
        g2.drawString(tableLabel, innerX + 10, y + 17);

        g2.setStroke(new BasicStroke(1f));
        g2.drawLine(innerX, y + 27, innerX + innerWidth, y + 27);

        int contentY      = y + 28;
        int labelColWidth = 100;
        int playerColWidth = (innerWidth - labelColWidth) / 2;
        int col1X = innerX + labelColWidth;
        int col2X = col1X + playerColWidth;

        int headerHeight = 22;
        g2.setColor(new Color(210, 220, 240));
        g2.fillRect(innerX + 1, contentY, innerWidth - 1, headerHeight);
        g2.setColor(Color.BLACK);

        g2.setFont(boldFont);
        FontMetrics boldFm = g2.getFontMetrics();

        String p1 = match.getFirstPlayer().getFullName();
        String p2 = match.getSecondPlayer().getFullName();
        p1 = truncate(p1, boldFm, playerColWidth - 8);
        p2 = truncate(p2, boldFm, playerColWidth - 8);

        g2.drawString(p1, col1X + 4, contentY + 15);
        g2.drawString(p2, col2X + 4, contentY + 15);

        g2.drawLine(innerX, contentY + headerHeight, innerX + innerWidth, contentY + headerHeight);

        String[] rowLabels = {"Satz 1", "Satz 2", "Satz 3", "Satz 4", "Satz 5", "Gesamtergebnis"};
        int availableHeight = height - 4 - (contentY - y) - headerHeight - 4;
        int rowHeight = Math.max(20, availableHeight / rowLabels.length);

        g2.setFont(plainFont);

        for (int i = 0; i < rowLabels.length; i++) {
            int rowY = contentY + headerHeight + i * rowHeight;
            boolean isTotal = i == rowLabels.length - 1;

            if (isTotal) {
                g2.setColor(new Color(232, 245, 233));
                g2.fillRect(innerX + 1, rowY, innerWidth - 1, rowHeight);
                g2.setColor(Color.BLACK);
                g2.setFont(boldFont);
            } else {
                g2.setColor(i % 2 == 0 ? Color.WHITE : new Color(248, 248, 252));
                g2.fillRect(innerX + 1, rowY, innerWidth - 1, rowHeight);
                g2.setColor(Color.BLACK);
                g2.setFont(plainFont);
            }

            FontMetrics rowFm = g2.getFontMetrics();
            int textY = rowY + (rowHeight + rowFm.getAscent() - rowFm.getDescent()) / 2;
            g2.drawString(rowLabels[i], innerX + 6, textY);

            g2.setStroke(new BasicStroke(0.5f));
            g2.drawLine(innerX, rowY + rowHeight, innerX + innerWidth, rowY + rowHeight);
        }

        g2.setStroke(new BasicStroke(1f));
        g2.drawLine(col1X, contentY, col1X, contentY + headerHeight + rowLabels.length * rowHeight);
        g2.drawLine(col2X, contentY, col2X, contentY + headerHeight + rowLabels.length * rowHeight);

        g2.setStroke(new BasicStroke(1.5f));
        g2.setColor(Color.BLACK);
        g2.drawRect(innerX, y, innerWidth, height - 4);
    }

    private String truncate(String text, FontMetrics fm, int maxWidth) {
        if (fm.stringWidth(text) <= maxWidth) return text;
        String ellipsis = "...";
        while (text.length() > 1 && fm.stringWidth(text + ellipsis) > maxWidth) {
            text = text.substring(0, text.length() - 1);
        }
        return text + ellipsis;
    }
}
