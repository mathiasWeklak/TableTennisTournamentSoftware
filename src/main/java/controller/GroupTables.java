package controller;

import model.GroupKoState;
import model.Match;
import model.Player;
import model.TournamentMode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builds the group tables of a group/knockout tournament in the usual layout: one table per
 * group with number, name, the results against every other group member, games, sets, balls,
 * points and place. Participants who reach the knockout stage are flagged.
 */
public final class GroupTables {

    /** Index of the first result column (after "Nr." and "Name"). */
    public static final int FIRST_RESULT_COLUMN = 2;

    /**
     * One group table.
     *
     * @param title     e.g. "Gruppe A"
     * @param columns   the column names
     * @param rows      the cell values, one array per participant in group order
     * @param qualified per row: reaches the knockout stage
     * @param withdrawn per row: withdrawn (shown greyed)
     */
    public record Table(String title, String[] columns, List<Object[]> rows, boolean[] qualified, boolean[] withdrawn) {

        /**
         * Returns the number of participants (= number of result columns).
         *
         * @return the group size
         */
        public int size() {
            return rows.size();
        }

        /**
         * Returns {@code true} for a cell on the diagonal of the result matrix.
         *
         * @param row    the row
         * @param column the column
         * @return {@code true} on the diagonal
         */
        public boolean isDiagonal(int row, int column) {
            return column - FIRST_RESULT_COLUMN == row;
        }

        /**
         * Returns {@code true} for a column of the result matrix.
         *
         * @param column the column
         * @return {@code true} for a result column
         */
        public boolean isResultColumn(int column) {
            return column >= FIRST_RESULT_COLUMN && column < FIRST_RESULT_COLUMN + size();
        }
    }

    private GroupTables() {
    }

    /**
     * Builds the tables of all groups. The statistics of the players must have been calculated
     * (group matches only, see {@link ScoreCalculator}). The rows keep the fixed group order
     * (no. 1 = seeded first), the place is shown in the last column.
     *
     * @param groupKo   groups and settings
     * @param counting  the counting matches (without struck matches)
     * @param struck    participants whose group matches are struck (withdrawn during the group
     *                  stage); they keep their row but get no place and no results
     * @param withdrawn all withdrawn participants; marked in the name
     * @param qualified participants who reach (or currently would reach) the knockout stage
     * @param compact   {@code true}: short names (printing)
     * @return one table per group
     */
    public static List<Table> build(GroupKoState groupKo, List<Match> counting, Set<Player> struck,
                                    Set<Player> withdrawn, Set<Player> qualified, boolean compact) {
        List<Table> tables = new ArrayList<>();
        for (int g = 0; g < groupKo.groups().size(); g++) {
            List<Player> group = groupKo.groups().get(g);
            Map<Player, Integer> rank = new HashMap<>();
            // struck participants: ranks without them
            for (Standings.Entry e : Standings.computeWithout(group, TournamentMode.ROUND_ROBIN, counting, struck)) {
                rank.put(e.player(), e.rank());
            }
            tables.add(table("Gruppe " + GroupKoState.groupName(g + 1), group, rank, counting, struck, withdrawn,
                    qualified, compact));
        }
        return tables;
    }

    private static Table table(String title, List<Player> group, Map<Player, Integer> rank, List<Match> counting,
                               Set<Player> struck, Set<Player> withdrawn, Set<Player> qualified, boolean compact) {
        Map<Player, String> shortNames = Player.uniqueShortNames(group);
        int n = group.size();
        String[] columns = new String[n + 7];
        columns[0] = "Nr.";
        columns[1] = "Name";
        for (int i = 0; i < n; i++) columns[FIRST_RESULT_COLUMN + i] = String.valueOf(i + 1);
        columns[n + 2] = "Spiele";
        columns[n + 3] = "Sätze";
        columns[n + 4] = "Bälle";
        columns[n + 5] = "Punkte";
        columns[n + 6] = "Platz";

        List<Object[]> rows = new ArrayList<>();
        boolean[] q = new boolean[n];
        boolean[] w = new boolean[n];
        for (int i = 0; i < n; i++) {
            Player p = group.get(i);
            boolean out = struck.contains(p);
            w[i] = withdrawn.contains(p);
            q[i] = !out && qualified.contains(p);
            Object[] row = new Object[columns.length];
            row[0] = i + 1;
            String name = compact ? shortNames.get(p) : p.getFullName() + " (" + p.getClub() + ")";
            row[1] = name + (w[i] ? (out ? " – zurückgezogen" : " – in der KO-Runde zurückgezogen") : "");
            for (int j = 0; j < n; j++) {
                row[FIRST_RESULT_COLUMN + j] = i == j || out || struck.contains(group.get(j))
                        ? "" : CrossTableController.resultFor(counting, p, group.get(j));
            }
            row[n + 2] = p.getWins() + ":" + p.getLosses();
            row[n + 3] = p.getSetsWon() + ":" + p.getSetsLost();
            row[n + 4] = p.getBallsWon() + ":" + p.getBallsLost();
            row[n + 5] = p.getPoints();
            row[n + 6] = out ? "–" : rank.getOrDefault(p, 0) + ".";
            rows.add(row);
        }
        return new Table(title, columns, rows, q, w);
    }
}
