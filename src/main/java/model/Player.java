package model;

/**
 * Represents a player participating in a table tennis tournament.
 */
public class Player {
    private final String firstName;
    private final String lastName;
    private final String club;
    private final int ttr;
    private int points;
    private int buchholz;
    private int feinBuchholz;
    private int wins;
    private int losses;
    private int setsWon;
    private int setsLost;
    private int ballsWon;
    private int ballsLost;

    /**
     * Constructs a Player object with the specified details.
     *
     * @param firstName the first name of the player
     * @param lastName  the last name of the player
     * @param club      the club affiliation of the player
     * @param ttr       the TTR (Table Tennis Rating) value of the player
     */
    public Player(String firstName, String lastName, String club, int ttr) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.club = club;
        this.ttr = ttr;
        this.points = 0;
        this.buchholz = 0;
        this.feinBuchholz = 0;
        this.wins = 0;
        this.losses = 0;
    }

    /**
     * Retrieves the full name of the player.
     *
     * @return the full name of the player
     */
    public String getFullName() {
        return firstName + " " + lastName;
    }

    /**
     * Retrieves the club affiliation of the player.
     *
     * @return the club affiliation
     */
    public String getClub() {
        return club;
    }

    /**
     * Retrieves the TTR (Table Tennis Rating) value of the player.
     *
     * @return the TTR value
     */
    public int getTtr() {
        return ttr;
    }

    /**
     * Retrieves the points scored by the player.
     *
     * @return the points scored
     */
    public int getPoints() {
        return points;
    }

    /**
     * Sets the points scored by the player.
     *
     * @param points the points to set
     */
    public void setPoints(int points) {
        this.points = points;
    }

    /**
     * Retrieves the Buchholz score of the player.
     *
     * @return the Buchholz score
     */
    public int getBuchholz() {
        return buchholz;
    }

    /**
     * Sets the Buchholz score of the player.
     *
     * @param buchholz the Buchholz score to set
     */
    public void setBuchholz(int buchholz) {
        this.buchholz = buchholz;
    }

    /**
     * Retrieves the Fein-Buchholz score of the player.
     *
     * @return the Fein-Buchholz score
     */
    public int getFeinBuchholz() {
        return feinBuchholz;
    }

    /**
     * Sets the Fein-Buchholz score of the player.
     *
     * @param feinBuchholz the Fein-Buchholz score to set
     */
    public void setFeinBuchholz(int feinBuchholz) {
        this.feinBuchholz = feinBuchholz;
    }

    /**
     * Retrieves the number of wins of the player.
     *
     * @return the number of wins
     */
    public int getWins() {
        return wins;
    }

    /**
     * Sets the number of wins of the player.
     *
     * @param wins the number of wins to set
     */
    public void setWins(int wins) {
        this.wins = wins;
    }

    /**
     * Retrieves the number of losses of the player.
     *
     * @return the number of losses
     */
    public int getLosses() {
        return losses;
    }

    /**
     * Sets the number of losses of the player.
     *
     * @param losses the number of losses to set
     */
    public void setLosses(int losses) {
        this.losses = losses;
    }

    public int getSetsWon() {
        return setsWon;
    }

    public void setSetsWon(int setsWon) {
        this.setsWon = setsWon;
    }

    public int getSetsLost() {
        return setsLost;
    }

    public void setSetsLost(int setsLost) {
        this.setsLost = setsLost;
    }

    public int getBallsWon() {
        return ballsWon;
    }

    public void setBallsWon(int ballsWon) {
        this.ballsWon = ballsWon;
    }

    public int getBallsLost() {
        return ballsLost;
    }

    public void setBallsLost(int ballsLost) {
        this.ballsLost = ballsLost;
    }
}
