package model;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Configuration and state of a tournament with group stage and knockout stage.
 *
 * @param groupSize          maximum number of participants per group
 * @param qualifiersPerGroup the first n of every group qualify (e.g. 2 = winner and runner-up)
 * @param bestNextPlaced     additionally the x best participants on the next place (e.g. the best
 *                           group thirds), compared across groups
 * @param groups             the participants of every group in seeding order (group A first)
 * @param bracket            the knockout bracket in drawing order, {@code null} = bye; empty as long
 *                           as the group stage is running
 * @param noThirdPlaceMatch  {@code true}: no match for third place, both semi-final losers share
 *                           place 3. Stored negated, so files of older versions (field missing =
 *                           {@code false}) keep the match for third place.
 * @param knockoutWithdrawn  participants who withdrew in the knockout stage; their group results
 *                           keep counting (also after the knockout draw is taken back). Empty in
 *                           files of older versions.
 */
public record GroupKoState(int groupSize, int qualifiersPerGroup, int bestNextPlaced,
                           List<List<Player>> groups, List<Player> bracket,
                           boolean noThirdPlaceMatch, List<Player> knockoutWithdrawn) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Canonical constructor; a missing list (files of older versions) becomes an empty list.
     */
    public GroupKoState {
        if (knockoutWithdrawn == null) knockoutWithdrawn = new ArrayList<>();
    }

    /**
     * Creates a state without knockout withdrawals.
     *
     * @param groupSize          maximum participants per group
     * @param qualifiersPerGroup qualifiers per group
     * @param bestNextPlaced     best participants on the next place
     * @param groups             the groups
     * @param bracket            the bracket
     * @param noThirdPlaceMatch  {@code true} without match for third place
     */
    public GroupKoState(int groupSize, int qualifiersPerGroup, int bestNextPlaced,
                        List<List<Player>> groups, List<Player> bracket, boolean noThirdPlaceMatch) {
        this(groupSize, qualifiersPerGroup, bestNextPlaced, groups, bracket, noThirdPlaceMatch, new ArrayList<>());
    }

    /**
     * Creates a configuration with match for third place, without groups and bracket.
     *
     * @param groupSize          maximum participants per group
     * @param qualifiersPerGroup qualifiers per group
     * @param bestNextPlaced     best participants on the next place
     */
    public GroupKoState(int groupSize, int qualifiersPerGroup, int bestNextPlaced) {
        this(groupSize, qualifiersPerGroup, bestNextPlaced, true);
    }

    /**
     * Creates a configuration without groups and bracket (before the tournament starts).
     *
     * @param groupSize          maximum participants per group
     * @param qualifiersPerGroup qualifiers per group
     * @param bestNextPlaced     best participants on the next place
     * @param thirdPlaceMatch    {@code true} if the match for third place is played
     */
    public GroupKoState(int groupSize, int qualifiersPerGroup, int bestNextPlaced, boolean thirdPlaceMatch) {
        this(groupSize, qualifiersPerGroup, bestNextPlaced, new ArrayList<>(), new ArrayList<>(), !thirdPlaceMatch);
    }

    /**
     * Creates a state with match for third place.
     *
     * @param groupSize          maximum participants per group
     * @param qualifiersPerGroup qualifiers per group
     * @param bestNextPlaced     best participants on the next place
     * @param groups             the groups
     * @param bracket            the bracket
     */
    public GroupKoState(int groupSize, int qualifiersPerGroup, int bestNextPlaced,
                        List<List<Player>> groups, List<Player> bracket) {
        this(groupSize, qualifiersPerGroup, bestNextPlaced, groups, bracket, false);
    }

    /**
     * Returns {@code true} if the match for third place is played.
     *
     * @return {@code true} for a match for third place
     */
    public boolean thirdPlaceMatch() {
        return !noThirdPlaceMatch;
    }

    /**
     * Returns a copy with the same configuration and the given groups and bracket.
     *
     * @param newGroups  the groups
     * @param newBracket the bracket
     * @return the new state
     */
    public GroupKoState withGroupsAndBracket(List<List<Player>> newGroups, List<Player> newBracket) {
        return new GroupKoState(groupSize, qualifiersPerGroup, bestNextPlaced, newGroups, newBracket, noThirdPlaceMatch,
                new ArrayList<>(knockoutWithdrawn));
    }

    /**
     * Returns the letter of a group, "A" for group 1.
     *
     * @param groupNo the group number, 1-based
     * @return the group letter
     */
    public static String groupName(int groupNo) {
        return groupNo <= 26 ? String.valueOf((char) ('A' + groupNo - 1)) : String.valueOf(groupNo);
    }
}
