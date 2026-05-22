package controller;

import model.Match;
import model.Player;
import model.TournamentMode;
import view.CrossTableView;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.print.PageFormat;
import java.awt.print.Paper;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import java.util.ArrayList;
import java.util.List;

/**
 * Controller for the cross table (Kreuztabelle) feature.
 *
 * <p>Builds the cross table data from the list of all matches and provides print logic.</p>
 */
public class CrossTableController {

    private final List<Player> players;
    private final List<Match> allMatches;
    private final String tournamentName;
    private final TournamentMode mode;
    private CrossTableView view;

    /**
     * Constructs the controller and initializes the UI on the Event Dispatch Thread.
     *
     * @param players        the list of players, sorted by ranking
     * @param allMatches     all matches played across all rounds
     * @param tournamentName the name of the tournament
     * @param mode           the tournament mode
     */
    public CrossTableController(List<Player> players, List<Match> allMatches,
                                String tournamentName, TournamentMode mode) {
        this.players = new ArrayList<>(players);
        this.allMatches = new ArrayList<>(allMatches);
        this.tournamentName = tournamentName;
        this.mode = mode;
        SwingUtilities.invokeLater(this::initializeUI);
    }

    private void initializeUI() {
        view = new CrossTableView(this, tournamentName);
        view.setVisible(true);
    }

    /**
     * Builds the {@link DefaultTableModel} for the cross table.
     *
     * <p>The first column contains the ranked player names. Columns 1..n correspond to
     * each player by their rank number. Each cell shows the set result from the row
     * player's perspective (e.g. "3:1"), or "-" on the diagonal, or "" if not yet played.</p>
     *
     * @return the populated table model
     */
    public DefaultTableModel buildTableModel() {
        int n = players.size();

        String[] columnNames = new String[n + 1];
        columnNames[0] = "Spieler";
        for (int i = 0; i < n; i++) {
            columnNames[i + 1] = String.valueOf(i + 1);
        }

        DefaultTableModel model = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        for (int i = 0; i < n; i++) {
            Player rowPlayer = players.get(i);
            Object[] row = new Object[n + 1];
            row[0] = (i + 1) + ". " + rowPlayer.getFullName() + " (" + rowPlayer.getClub() + ")";
            for (int j = 0; j < n; j++) {
                if (i == j) {
                    row[j + 1] = "-";
                } else {
                    row[j + 1] = findResult(rowPlayer, players.get(j));
                }
            }
            model.addRow(row);
        }

        return model;
    }

    /**
     * Finds the result of a match between rowPlayer and colPlayer from rowPlayer's perspective.
     *
     * @param rowPlayer the player whose perspective is used
     * @param colPlayer the opponent
     * @return the result string, or an empty string if the match was not played yet
     */
    private String findResult(Player rowPlayer, Player colPlayer) {
        for (Match match : allMatches) {
            if (match.getOverallResult() == null || match.getOverallResult().isEmpty()
                    || match.getOverallResult().equals(":")) {
                continue;
            }
            if (match.getFirstPlayer().equals(rowPlayer) && match.getSecondPlayer() != null
                    && match.getSecondPlayer().equals(colPlayer)) {
                return match.getOverallResult();
            }
            if (match.getSecondPlayer() != null && match.getSecondPlayer().equals(rowPlayer)
                    && match.getFirstPlayer().equals(colPlayer)) {
                return reverseResult(match.getOverallResult());
            }
        }
        return "";
    }

    /**
     * Reverses a result string (e.g. "3:1" becomes "1:3").
     *
     * @param result the result to reverse
     * @return the reversed result, or the original if it cannot be parsed
     */
    private String reverseResult(String result) {
        String[] parts = result.split(":");
        if (parts.length == 2) {
            return parts[1] + ":" + parts[0];
        }
        return result;
    }

    /**
     * Returns the number of players.
     *
     * @return the player count
     */
    public int getPlayerCount() {
        return players.size();
    }

    /**
     * Prints the cross table using the system print dialog.
     */
    public void printCrossTable() {
        if (view == null) {
            return;
        }
        try {
            PrinterJob job = PrinterJob.getPrinterJob();
            job.setJobName("Kreuztabelle - " + tournamentName);

            PageFormat pageFormat = job.defaultPage();
            pageFormat.setOrientation(PageFormat.LANDSCAPE);
            Paper paper = pageFormat.getPaper();
            double margin = 36;
            paper.setImageableArea(margin, margin,
                    paper.getWidth() - margin * 2,
                    paper.getHeight() - margin * 2);
            pageFormat.setPaper(paper);
            PageFormat validated = job.validatePage(pageFormat);

            java.awt.print.Printable printable = view.getCrossTable().getPrintable(
                    JTable.PrintMode.FIT_WIDTH,
                    new java.text.MessageFormat("Kreuztabelle: " + tournamentName),
                    new java.text.MessageFormat("Seite {0}")
            );
            job.setPrintable(printable, validated);

            if (job.printDialog()) {
                job.print();
            }
        } catch (PrinterException e) {
            JOptionPane.showMessageDialog(view, "Fehler beim Drucken.", "Druckfehler", JOptionPane.ERROR_MESSAGE);
        }
    }
}
