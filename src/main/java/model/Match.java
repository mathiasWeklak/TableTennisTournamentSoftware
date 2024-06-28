package model;

/**
 * Represents a match between two players in a tournament.
 */
public class Match {

    private final Player firstPlayer;
    private final Player secondPlayer;
    private int tableNumber;
    private final Result result;
    private String overallResult;

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
        return result.getResults();
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
     * Represents the set results of a match.
     */
    public static class Result {
        private final String[][] setResults;

        /**
         * Constructs a Result object initialized with empty set results.
         */
        public Result() {
            this.setResults = new String[5][2];
        }

        /**
         * Retrieves the set results.
         *
         * @return the set results as a 2D array of strings
         */
        public String[][] getResults() {
            return setResults;
        }

        /**
         * Sets the set results at a specific index.
         *
         * @param index     the index of the set result (0 to 4)
         * @param setResult the result of the set as an array of two strings
         * @throws IllegalArgumentException if the index is out of range or setResult array is invalid
         */
        public void setResults(int index, String[] setResult) {
            if (index < 0 || index >= 5 || setResult == null || setResult.length != 2) {
                throw new IllegalArgumentException("Invalid index or result data.");
            }
            this.setResults[index] = setResult;
        }
    }

}
