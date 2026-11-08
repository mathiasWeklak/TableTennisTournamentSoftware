package model;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

/**
 * Represents a participant of a table tennis tournament: a single player or, in a doubles
 * competition, a pair of two players.
 *
 * <p>For a pair, the first person is stored in the regular fields and the partner in the
 * {@code partner*} fields. Names and clubs are then displayed as "A / B", the TTR value used for
 * seeding is the sum of both. All tournament logic works on participants and does not need to
 * know whether it is a single player or a pair.</p>
 */
public class Player implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    private final String firstName;
    private final String lastName;
    private final String club;
    private final int ttr;
    // partner of a doubles pair; null for a single player (also in files of older versions)
    private final String partnerFirstName;
    private final String partnerLastName;
    private final String partnerClub;
    private final int partnerTtr;
    private int points;
    // double: a virtual opponent may count with half a point (FIDE "Mr. 50%"); renamed from the
    // former int fields so that files of older versions still load (values are recalculated)
    private double buchholzScore;
    private double feinBuchholzScore;
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
        this(firstName, lastName, club, ttr, null, null, null, 0);
    }

    /**
     * Creates a doubles pair.
     *
     * @param first  the first person of the pair (stats are ignored)
     * @param second the partner (stats are ignored)
     * @return the pair as one participant
     */
    public static Player pair(Player first, Player second) {
        if (first.isDoubles() || second.isDoubles()) throw new IllegalArgumentException("pair of pairs");
        return new Player(first.firstName, first.lastName, first.club, first.ttr,
                second.firstName, second.lastName, second.club, second.ttr);
    }

    private Player(String firstName, String lastName, String club, int ttr,
                   String partnerFirstName, String partnerLastName, String partnerClub, int partnerTtr) {
        if (firstName == null || firstName.isBlank()) throw new IllegalArgumentException("firstName must not be blank");
        if (lastName == null || lastName.isBlank()) throw new IllegalArgumentException("lastName must not be blank");
        if (club == null || club.isBlank()) throw new IllegalArgumentException("club must not be blank");
        if (ttr < 0) throw new IllegalArgumentException("ttr must not be negative");
        this.firstName = firstName;
        this.lastName = lastName;
        this.club = club;
        this.ttr = ttr;
        if (partnerFirstName != null) {
            if (partnerFirstName.isBlank() || partnerLastName == null || partnerLastName.isBlank()
                    || partnerClub == null || partnerClub.isBlank() || partnerTtr < 0) {
                throw new IllegalArgumentException("partner data incomplete");
            }
        }
        this.partnerFirstName = partnerFirstName;
        this.partnerLastName = partnerLastName;
        this.partnerClub = partnerClub;
        this.partnerTtr = partnerTtr;
        this.points = 0;
        this.buchholzScore = 0;
        this.feinBuchholzScore = 0;
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
        String own = firstName + " " + lastName;
        return isDoubles() ? own + " / " + partnerFirstName + " " + partnerLastName : own;
    }

    /**
     * Returns {@code true} if this participant is a doubles pair.
     *
     * @return {@code true} for a pair
     */
    public boolean isDoubles() {
        return partnerFirstName != null;
    }

    /**
     * Returns a short name for tabs, sheets and printouts: the last name, for a pair
     * "Becker/Krause".
     *
     * @return the short name
     */
    public String getShortName() {
        return isDoubles() ? lastName + "/" + partnerLastName : lastName;
    }

    /**
     * Returns the club of the first person (for a single player: the club).
     *
     * @return the own club
     */
    public String getOwnClub() {
        return club;
    }

    /**
     * Returns the TTR of the first person (for a single player: the TTR).
     *
     * @return the own TTR
     */
    public int getOwnTtr() {
        return ttr;
    }

    /**
     * Returns the partner's first name.
     *
     * @return the first name, or {@code null} for a single player
     */
    public String getPartnerFirstName() {
        return partnerFirstName;
    }

    /**
     * Returns the partner's last name.
     *
     * @return the last name, or {@code null} for a single player
     */
    public String getPartnerLastName() {
        return partnerLastName;
    }

    /**
     * Returns the partner's club.
     *
     * @return the club, or {@code null} for a single player
     */
    public String getPartnerClub() {
        return partnerClub;
    }

    /**
     * Returns the partner's TTR.
     *
     * @return the TTR, 0 for a single player
     */
    public int getPartnerTtr() {
        return partnerTtr;
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
     * Retrieves the club affiliation; for a pair from different clubs "Club A / Club B".
     *
     * @return the club affiliation
     */
    public String getClub() {
        if (!isDoubles() || partnerClub.equals(club)) return club;
        return club + " / " + partnerClub;
    }

    /**
     * Retrieves the TTR value used for seeding; for a pair the sum of both players.
     *
     * @return the TTR value
     */
    public int getTtr() {
        return ttr + partnerTtr;
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
    public double getBuchholz() {
        return buchholzScore;
    }

    /**
     * Sets the Buchholz score of the player.
     *
     * @param buchholz the Buchholz score to set
     */
    public void setBuchholz(double buchholz) {
        this.buchholzScore = buchholz;
    }

    /**
     * Retrieves the Fein-Buchholz score of the player.
     *
     * @return the Fein-Buchholz score
     */
    public double getFeinBuchholz() {
        return feinBuchholzScore;
    }

    /**
     * Sets the Fein-Buchholz score of the player.
     *
     * @param feinBuchholz the Fein-Buchholz score to set
     */
    public void setFeinBuchholz(double feinBuchholz) {
        this.feinBuchholzScore = feinBuchholz;
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
     * Two players are considered equal if they share the same first name, last name, club, and TTR value.
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
                && ttr == other.ttr
                && Objects.equals(partnerFirstName, other.partnerFirstName)
                && Objects.equals(partnerLastName, other.partnerLastName)
                && Objects.equals(partnerClub, other.partnerClub)
                && partnerTtr == other.partnerTtr;
    }

    /**
     * Returns a hash code based on the player's first name, last name, and club.
     *
     * @return the hash code
     */
    @Override
    public int hashCode() {
        return Objects.hash(firstName, lastName, club, ttr, partnerFirstName, partnerLastName, partnerClub, partnerTtr);
    }

    @Override
    public String toString() {
        return getFullName() + " (" + getClub() + ")";
    }
}
