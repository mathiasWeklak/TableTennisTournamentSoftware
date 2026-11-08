package controller;

import model.Match;
import model.Player;
import view.CrossTableView;

import javax.swing.table.DefaultTableModel;
import java.awt.Window;
import java.util.ArrayList;
import java.util.List;

/**
 * Controller for the cross table (Kreuztabelle).
 *
 * <p>Builds the cross table data from the matches and provides print logic. A group/knockout
 * tournament has one section (tab) per group, the other formats one section with all players.
 * The dialog can be refreshed while it is open, so results entered later appear immediately.</p>
 */
public class CrossTableController {

    /**
     * One cross table, e.g. one group.
     *
     * @param title   the title, e.g. "Gruppe A"
     * @param players the players in ranking order
     */
    public record Section(String title, List<Player> players) {
    }

    private List<Section> sections;
    private List<Match> allMatches;
    private final String tournamentName;
    private final CrossTableView view;

    /**
     * Constructs the controller and shows the dialog. Must be called on the EDT.
     *
     * @param owner          the tournament window owning the dialog
     * @param sections       the sections (one per group, or one with all players)
     * @param allMatches     all matches
     * @param tournamentName the name of the tournament
     */
    public CrossTableController(Window owner, List<Section> sections, List<Match> allMatches, String tournamentName) {
        this.sections = new ArrayList<>(sections);
        this.allMatches = new ArrayList<>(allMatches);
        this.tournamentName = tournamentName;
        this.view = new CrossTableView(owner, this, tournamentName);
        this.view.setVisible(true);
    }

    /**
     * Updates the cross table with new data, if the dialog is still open.
     *
     * @param sections   the sections
     * @param allMatches all matches
     */
    public void refresh(List<Section> sections, List<Match> allMatches) {
        this.sections = new ArrayList<>(sections);
        this.allMatches = new ArrayList<>(allMatches);
        if (isOpen()) {
            view.rebuild();
        }
    }

    /**
     * Returns {@code true} while the dialog has not been closed.
     *
     * @return {@code true} if the dialog is open
     */
    public boolean isOpen() {
        return view.isDisplayable();
    }

    /**
     * Brings the open dialog to the front.
     */
    public void toFront() {
        view.toFront();
    }

    /**
     * Returns the number of sections.
     *
     * @return the number of sections
     */
    public int getSectionCount() {
        return sections.size();
    }

    /**
     * Returns the title of a section.
     *
     * @param section the section index
     * @return the title
     */
    public String getSectionTitle(int section) {
        return sections.get(section).title();
    }

    /**
     * Builds the model of the first section.
     *
     * @param compact {@code true}: short names for printing
     * @return the table model
     */
    public DefaultTableModel buildTableModel(boolean compact) {
        return buildTableModel(0, compact);
    }

    /**
     * Builds the {@link DefaultTableModel} of a section.
     *
     * <p>The first column contains the ranked player names. Columns 1..n correspond to each player
     * by their rank number. Each cell shows the result from the row player's perspective
     * (e.g. "3:1"), "-" on the diagonal, or "" if not played yet.</p>
     *
     * @param section the section index
     * @param compact {@code true}: short names ("1. Müller") for printing
     * @return the populated table model
     */
    public DefaultTableModel buildTableModel(int section, boolean compact) {
        List<Player> players = sections.get(section).players();
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

        java.util.Map<Player, String> shortNames = Player.uniqueShortNames(players);
        for (int i = 0; i < n; i++) {
            Player rowPlayer = players.get(i);
            Object[] row = new Object[n + 1];
            row[0] = compact
                    ? (i + 1) + ". " + shortNames.get(rowPlayer)
                    : (i + 1) + ". " + rowPlayer.getFullName() + " (" + rowPlayer.getClub() + ")";
            for (int j = 0; j < n; j++) {
                row[j + 1] = i == j ? "-" : resultFor(allMatches, rowPlayer, players.get(j));
            }
            model.addRow(row);
        }

        return model;
    }

    /**
     * Finds the result of a group or league match between rowPlayer and colPlayer from rowPlayer's
     * perspective. Knockout matches are not part of the cross table.
     *
     * @param allMatches all matches
     * @param rowPlayer  the player whose perspective is used
     * @param colPlayer  the opponent
     * @return the result string, or an empty string if the match was not played yet
     */
    static String resultFor(List<Match> allMatches, Player rowPlayer, Player colPlayer) {
        for (Match match : allMatches) {
            String result = match.getOverallResult();
            if (result == null || result.isEmpty() || result.equals(":") || match.getSecondPlayer() == null
                    || match.getKoRound() > 0) {
                continue;
            }
            // decided results as "3:1"; entries that do not count yet in brackets, e.g. "(1:0)"
            int[] sets = match.decidedSets();
            String shown = sets != null ? sets[0] + ":" + sets[1] : result;
            if (match.getFirstPlayer().equals(rowPlayer) && match.getSecondPlayer().equals(colPlayer)) {
                return sets != null ? shown : "(" + shown + ")";
            }
            if (match.getSecondPlayer().equals(rowPlayer) && match.getFirstPlayer().equals(colPlayer)) {
                return sets != null ? reverseResult(shown) : "(" + reverseResult(shown) + ")";
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
    static String reverseResult(String result) {
        String[] parts = result.split(":");
        if (parts.length == 2) {
            return parts[1] + ":" + parts[0];
        }
        return result;
    }

    /**
     * Prints the first section in a compact layout.
     */
    public void printCrossTable() {
        printCrossTable(0);
    }

    /**
     * Prints a section in a compact layout using the system print dialog.
     *
     * @param section the section index
     */
    public void printCrossTable(int section) {
        String title = sections.size() > 1 ? " – " + getSectionTitle(section) : "";
        PrintUtil.printTable(view, CrossTableView.createPrintTable(buildTableModel(section, true)),
                "Kreuztabelle - " + tournamentName, "Kreuztabelle: " + tournamentName + title);
    }
}
