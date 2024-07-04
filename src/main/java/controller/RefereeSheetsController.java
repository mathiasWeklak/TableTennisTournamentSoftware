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
 * This class represents the controller for managing the referee sheets for matches.
 */
public class RefereeSheetsController {

    private final List<Match> matches;

    /**
     * Constructs a new RefereeSheetsController.
     *
     * @param matches the list of matches to display
     */
    public RefereeSheetsController(List<Match> matches) {
        this.matches = matches;
        SwingUtilities.invokeLater(this::initializeUI);
    }

    /**
     * Initializes the user interface.
     */
    private void initializeUI() {
        new RefereeSheetsView(this, matches);
    }

    /**
     * Creates a table model for the referee sheet of a given match.
     *
     * @param match the match for which to create the table model
     * @return the table model for the referee sheet
     */
    public DefaultTableModel createRefereeSheetTableModel(Match match) {
        String[] columnNames = {"", "", ""};
        Object[][] data = {
                {"", match.getFirstPlayer().getFullName(), match.getSecondPlayer().getFullName()},
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
     * Prints the referee sheets.
     */
    public void printRefereeSheets() {
        PrinterJob job = PrinterJob.getPrinterJob();
        job.setJobName("Schiedsrichterzettel");

        job.setPrintable((pg, pf, pageNum) -> {
            int matchesPerPage = 5;
            int totalMatches = matches.size();
            int totalPages = (int) Math.ceil((double) totalMatches / matchesPerPage);

            if (pageNum >= totalPages) {
                return Printable.NO_SUCH_PAGE;
            }

            Graphics2D g2 = (Graphics2D) pg;
            g2.translate(pf.getImageableX(), pf.getImageableY());
            g2.scale(0.8, 0.8);

            int startIndex = pageNum * matchesPerPage;
            int endIndex = Math.min(startIndex + matchesPerPage, totalMatches);

            int yPosition = 20;
            int tableHeight = 120;

            for (int i = startIndex; i < endIndex; i++) {
                Match match = matches.get(i);

                JTable table = new JTable(createRefereeSheetTableModel(match));
                table.setSize(new Dimension((int) pf.getImageableWidth() - 40, tableHeight));
                table.doLayout();

                g2.drawRect(20, yPosition - 10, (int) pf.getImageableWidth() - 40, tableHeight + 40);
                g2.drawString("Tisch " + match.getTableNumber(), 30, yPosition);

                g2.translate(20, yPosition + 20);
                table.getTableHeader().paint(g2);
                g2.translate(0, table.getTableHeader().getHeight());

                table.paint(g2);
                g2.translate(-20, -(yPosition + 20 + table.getTableHeader().getHeight()));

                yPosition += tableHeight + 40;
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
}
