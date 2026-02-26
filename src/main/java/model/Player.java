package model;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

/**
 * Represents a player participating in a table tennis tournament.
 */
public class Player implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
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
        if (firstName == null || firstName.isBlank()) throw new IllegalArgumentException("firstName must not be blank");
        if (lastName == null || lastName.isBlank()) throw new IllegalArgumentException("lastName must not be blank");
        if (club == null || club.isBlank()) throw new IllegalArgumentException("club must not be blank");
        if (ttr < 0) throw new IllegalArgumentException("ttr must not be negative");
        this.firstName = firstName;
        this.lastName = lastName;
        this.club = club;
        this.ttr = ttr;
        this.points = 0;
        this.buchholz = 0;
        this.feinBuchholz = 0;
        this.wins = 0;
        this.losses = 0;
        this.setsWon = 0;
        this.setsLost = 0;
        this.ballsWon = 0;
        this.ballsLost = 0;
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
     * Retrieves the first name of the player.
     *
     * @return the first name of the player
     */
    public String getFirstName() {
        return firstName;
    }

    /**
     * Retrieves the last name of the player.
     *
     * @return the last name of the player
     */
    public String getLastName() {
        return lastName;
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

    /**
     * Retrieves the number of sets won by the player.
     *
     * @return the number of sets won
     */
    public int getSetsWon() {
        return setsWon;
    }

    /**
     * Sets the number of sets won by the player.
     *
     * @param setsWon the number of sets won to set
     */
    public void setSetsWon(int setsWon) {
        this.setsWon = setsWon;
    }

    /**
     * Retrieves the number of sets lost by the player.
     *
     * @return the number of sets lost
     */
    public int getSetsLost() {
        return setsLost;
    }

    /**
     * Sets the number of sets lost by the player.
     *
     * @param setsLost the number of sets lost to set
     */
    public void setSetsLost(int setsLost) {
        this.setsLost = setsLost;
    }

    /**
     * Retrieves the number of balls (points) won by the player across all sets.
     *
     * @return the number of balls won
     */
    public int getBallsWon() {
        return ballsWon;
    }

    /**
     * Sets the number of balls (points) won by the player across all sets.
     *
     * @param ballsWon the number of balls won to set
     */
    public void setBallsWon(int ballsWon) {
        this.ballsWon = ballsWon;
    }

    /**
     * Retrieves the number of balls (points) lost by the player across all sets.
     *
     * @return the number of balls lost
     */
    public int getBallsLost() {
        return ballsLost;
    }

    /**
     * Sets the number of balls (points) lost by the player across all sets.
     *
     * @param ballsLost the number of balls lost to set
     */
    public void setBallsLost(int ballsLost) {
        this.ballsLost = ballsLost;
    }

    /**
     * Two players are considered equal if they share the same first name, last name, and club.
     *
     * @param o the object to compare with
     * @return {@code true} if the players are equal, {@code false} otherwise
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Player other)) return false;
        return Objects.equals(firstName, other.firstName)
                && Objects.equals(lastName, other.lastName)
                && Objects.equals(club, other.club)
                && ttr == other.ttr;
    }

    /**
     * Returns a hash code based on the player's first name, last name, and club.
     *
     * @return the hash code
     */
    @Override
    public int hashCode() {
        return Objects.hash(firstName, lastName, club, ttr);
    }

    @Override
    public String toString() {
        return getFullName() + " (" + club + ")";
    }
}
