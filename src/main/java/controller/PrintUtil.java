package controller;

import javax.swing.JOptionPane;
import javax.swing.JTable;
import java.awt.Component;
import java.awt.print.PageFormat;
import java.awt.print.Paper;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import java.text.MessageFormat;

/**
 * Shared printing helpers for tables (standings, cross table).
 */
public final class PrintUtil {

    private static final double MARGIN = 36;

    private PrintUtil() {
    }

    /**
     * Escapes text so that {@link MessageFormat} prints it literally; otherwise "{" throws and
     * "'" disappears. Apostrophes are doubled; from the first curly brace on, the text is quoted.
     * The quote starts right before a brace, so the opening quote is never directly followed by an
     * apostrophe (MessageFormat would read that as an escaped apostrophe instead).
     *
     * @param text arbitrary text, e.g. a tournament name
     * @return the escaped pattern part
     */
    public static String escapeForMessageFormat(String text) {
        if (text == null || text.isEmpty()) return "";
        int firstBrace = -1;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '{' || c == '}') {
                firstBrace = i;
                break;
            }
        }
        if (firstBrace < 0) return text.replace("'", "''");
        return text.substring(0, firstBrace).replace("'", "''")
                + "'" + text.substring(firstBrace).replace("'", "''") + "'";
    }

    /**
     * Prints a table in landscape, fitted to the page width, after showing the print dialog.
     *
     * @param parent  parent component for error dialogs
     * @param table   the table to print
     * @param jobName the print job name
     * @param header  the header text, printed literally
     */
    public static void printTable(Component parent, JTable table, String jobName, String header) {
        try {
            PrinterJob job = PrinterJob.getPrinterJob();
            job.setJobName(jobName);

            PageFormat pageFormat = job.defaultPage();
            pageFormat.setOrientation(PageFormat.LANDSCAPE);
            Paper paper = pageFormat.getPaper();
            paper.setImageableArea(MARGIN, MARGIN,
                    paper.getWidth() - MARGIN * 2,
                    paper.getHeight() - MARGIN * 2);
            pageFormat.setPaper(paper);
            PageFormat validated = job.validatePage(pageFormat);

            Printable printable = table.getPrintable(
                    JTable.PrintMode.FIT_WIDTH,
                    new MessageFormat(escapeForMessageFormat(header)),
                    new MessageFormat("Seite {0}"));
            job.setPrintable(printable, validated);

            if (job.printDialog()) {
                job.print();
            }
        } catch (PrinterException e) {
            JOptionPane.showMessageDialog(parent, "Fehler beim Drucken.", "Druckfehler", JOptionPane.ERROR_MESSAGE);
        }
    }
}
