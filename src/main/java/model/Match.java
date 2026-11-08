package model;

import java.io.Serial;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Objects;

/**
 * Represents a match between two players in a tournament.
 */
public class Match implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    public static final int MAX_SETS = 5;
    private final Player firstPlayer;
    private final Player secondPlayer;
    private int tableNumber;
    private final Result result;
    private String overallResult;
    // Pairing information, needed for the float history. 0 in files saved by older versions.
    private int round;
    private int firstPointsBefore;
    private int secondPointsBefore;
    // Group/knockout tournaments (0 = not used; also in files of older versions)
    private int groupNo;      // 1 = group A, 2 = group B, ...
    private int koRound;      // 1 = first knockout round, ...
    private int koSlot;       // position of the match within its knockout round
    private boolean thirdPlace;

    /**
     * Marks the match as a group match.
     *
     * @param groupNo the group number, 1 = group A
     */
    public void setGroup(int groupNo) {
        this.groupNo = groupNo;
    }

    /**
     * Returns the group number of a group match.
     *
     * @return 1 = group A, ...; 0 if this is no group match
     */
    public int getGroupNo() {
        return groupNo;
    }

    /**
     * Marks the match as a knockout match.
     *
     * @param koRound    the knockout round, 1 = first knockout round
     * @param koSlot     the position within the round (0-based)
     * @param thirdPlace {@code true} for the match for third place
     */
    public void setKnockout(int koRound, int koSlot, boolean thirdPlace) {
        this.koRound = koRound;
        this.koSlot = koSlot;
        this.thirdPlace = thirdPlace;
    }

    /**
     * Returns the knockout round.
     *
     * @return 1 = first knockout round, ...; 0 if this is no knockout match
     */
    public int getKoRound() {
        return koRound;
    }

    /**
     * Returns the position within the knockout round.
     *
     * @return the 0-based position
     */
    public int getKoSlot() {
        return koSlot;
    }

    /**
     * Returns {@code true} for the match for third place.
     *
     * @return {@code true} for the third place match
     */
    public boolean isThirdPlace() {
        return thirdPlace;
    }

    /**
     * Returns the winner of a decided match; for a bye the player himself.
     *
     * @return the winner, or {@code null} if not decided yet
     */
    public Player getWinner() {
        if (secondPlayer == null) return firstPlayer;
        int[] sets = decidedSets();
        if (sets == null) return null;
        return sets[0] > sets[1] ? firstPlayer : secondPlayer;
    }

    /**
     * Returns the loser of a decided match.
     *
     * @return the loser, or {@code null} for a bye or if not decided yet
     */
    public Player getLoser() {
        Player winner = getWinner();
        if (winner == null || secondPlayer == null) return null;
        return winner == firstPlayer ? secondPlayer : firstPlayer;
    }

    /**
     * Constructs a match between two players at a specific table.
     *
     * @param firstPlayer  the first player
     * @param secondPlayer the second player
     * @param tableNumber  the table number where the match takes place
     */
    public Match(Player firstPlayer, Player secondPlayer, int tableNumber) {
        this.firstPlayer = firstPlayer;
        this.secondPlayer = secondPlayer;
        this.tableNumber = tableNumber;
        this.result = new Result();
        this.overallResult = "";
    }

    /**
     * Retrieves the first player in the match.
     *
     * @return the first player
     */
    public Player getFirstPlayer() {
        return firstPlayer;
    }

    /**
     * Retrieves the second player in the match.
     *
     * @return the second player
     */
    public Player getSecondPlayer() {
        return secondPlayer;
    }

    /**
     * Retrieves the table number where the match takes place.
     *
     * @return the table number
     */
    public int getTableNumber() {
        return tableNumber;
    }

    /**
     * Retrieves the set results of the match.
     *
     * @return the set results as a 2D array of strings
     */
    public String[][] getResults() {
        return result.getResultsCopy();
    }

    /**
     * Sets the set results for a specific index in the match.
     *
     * @param index     the index of the set result (0 to 4)
     * @param setResult the result of the set as an array of two strings
     * @throws IllegalArgumentException if the index is out of range or setResult array is invalid
     */
    public void setResults(int index, String[] setResult) {
        result.setResults(index, setResult);
    }

    /**
     * Retrieves the overall result of the match.
     *
     * @return the overall result string
     */
    public String getOverallResult() {
        return overallResult;
    }

    /**
     * Sets the overall result of the match.
     *
     * @param overallResult the overall result string to set
     */
    public void setOverallResult(String overallResult) {
        this.overallResult = overallResult;
    }

    /**
     * Sets the table number for this match.
     *
     * @param tableNumber the table number to set
     */
    public void setTable(Integer tableNumber) {
        this.tableNumber = tableNumber;
    }

    /**
     * Returns {@code true} if the match has a decided result that counts. This is the single rule
     * used by the standings, the cross table, the pairing status and the round check:
     * if any set was entered, the sets must form a complete, valid match
     * ({@link ResultRules#isComplete}); without set entries a decided overall result ("3:1") counts.
     * A bye is never "decided" in this sense.
     *
     * @return {@code true} if the result counts
     */
    public boolean isDecided() {
        if (secondPlayer == null) return false;
        String[][] sets = getResults();
        boolean anySetEntered = Arrays.stream(sets)
                .anyMatch(row -> row != null && ResultRules.checkSet(row[0], row[1]) != ResultRules.SetStatus.EMPTY);
        return anySetEntered ? ResultRules.isComplete(sets) : ResultRules.isDecidedOverallResult(overallResult);
    }

    /**
     * Returns {@code true} for a walkover ("kampflos"): the match is decided by exactly three sets
     * 11:0 for the same player and nothing else was entered. This is what the "Kampflos" button
     * enters; a real 3 x 11:0 is practically impossible. For the tie-breaks a walkover is an
     * unplayed game (FIDE C.07).
     *
     * @return {@code true} for a walkover
     */
    public boolean isWalkover() {
        if (!isDecided()) return false;
        String[][] sets = getResults();
        int first = 0;
        int second = 0;
        for (String[] row : sets) {
            if (row == null || ResultRules.checkSet(row[0], row[1]) == ResultRules.SetStatus.EMPTY) continue;
            String a = row[0] == null ? "" : row[0].trim();
            String b = row[1] == null ? "" : row[1].trim();
            if (a.equals("11") && b.equals("0")) first++;
            else if (a.equals("0") && b.equals("11")) second++;
            else return false;
        }
        return (first == ResultRules.SETS_TO_WIN && second == 0)
                || (second == ResultRules.SETS_TO_WIN && first == 0);
    }

    /**
     * Returns the sets won by both players if the match is decided.
     *
     * @return {@code int[]{setsFirstPlayer, setsSecondPlayer}}, or {@code null} if not decided
     */
    public int[] decidedSets() {
        if (!isDecided()) return null;
        String[][] sets = getResults();
        boolean anySetEntered = Arrays.stream(sets)
                .anyMatch(row -> row != null && ResultRules.checkSet(row[0], row[1]) != ResultRules.SetStatus.EMPTY);
        return anySetEntered ? ResultRules.countSets(sets) : ResultRules.parseOverallResult(overallResult);
    }

    /**
     * Records in which round the match was paired and the players' points at pairing time.
     *
     * @param round        the round number (1-based)
     * @param firstPoints  points of the first player before the round
     * @param secondPoints points of the second player before the round (ignored for a bye)
     */
    public void setPairingInfo(int round, int firstPoints, int secondPoints) {
        this.round = round;
        this.firstPointsBefore = firstPoints;
        this.secondPointsBefore = secondPoints;
    }

    /**
     * Returns the round in which the match was paired.
     *
     * @return the round number, or 0 if unknown (files saved by older versions)
     */
    public int getRound() {
        return round;
    }

    /**
     * Returns {@code true} if {@code player} floated down in this match: they had a bye, or they
     * played an opponent who had fewer points before the round.
     *
     * @param player a player
     * @return {@code true} for a downfloat
     */
    public boolean isDownfloatFor(Player player) {
        if (secondPlayer == null) return firstPlayer.equals(player);
        if (firstPlayer.equals(player)) return firstPointsBefore > secondPointsBefore;
        if (secondPlayer.equals(player)) return secondPointsBefore > firstPointsBefore;
        return false;
    }

    /**
     * Returns {@code true} if {@code player} floated up in this match: they played an opponent
     * who had more points before the round.
     *
     * @param player a player
     * @return {@code true} for an upfloat
     */
    public boolean isUpfloatFor(Player player) {
        if (secondPlayer == null) return false;
        if (firstPlayer.equals(player)) return firstPointsBefore < secondPointsBefore;
        if (secondPlayer.equals(player)) return secondPointsBefore < firstPointsBefore;
        return false;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Match other)) return false;
        return (Objects.equals(firstPlayer, other.firstPlayer)
                && Objects.equals(secondPlayer, other.secondPlayer))
                || (Objects.equals(firstPlayer, other.secondPlayer)
                && Objects.equals(secondPlayer, other.firstPlayer));
    }

    @Override
    public int hashCode() {
        int h1 = Objects.hashCode(firstPlayer);
        int h2 = Objects.hashCode(secondPlayer);
        return h1 <= h2 ? Objects.hash(firstPlayer, secondPlayer) : Objects.hash(secondPlayer, firstPlayer);
    }

    /**
     * Represents the set results of a match.
     */
    public static class Result implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        private final String[][] setResults;

        /**
         * Constructs a Result object initialized with empty set results.
         */
        public Result() {
            this.setResults = new String[MAX_SETS][2];
        }

        /**
         * Returns a deep copy of the set results.
         *
         * @return copy of the set results as a 2D array of strings
         */
        public String[][] getResultsCopy() {
            return Arrays.stream(setResults)
                    .map(row -> row == null ? null : row.clone())
                    .toArray(String[][]::new);
        }

        /**
         * Sets the set results at a specific index.
         *
         * @param index     the index of the set result (0 to 4)
         * @param setResult the result of the set as an array of two strings
         * @throws IllegalArgumentException if the index is out of range or setResult array is invalid
         */
        public void setResults(int index, String[] setResult) {
            if (index < 0 || index >= MAX_SETS || setResult == null || setResult.length != 2) {
                throw new IllegalArgumentException("Invalid index or result data.");
            }
            this.setResults[index] = setResult.clone(); // defensive copy: caller keeps no reference
        }
    }

}
