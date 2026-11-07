package model;

import java.util.ArrayList;
import java.util.List;

/**
 * Table tennis result rules for a best-of-five match.
 *
 * <p>A set is valid when the winner has at least 11 points and leads by at least two points.
 * If the winner has more than 11 points, the lead must be exactly two (deuce). A match is
 * complete when one player has won three sets, the sets were entered without gaps and no set
 * was entered after the match was already decided.</p>
 */
public final class ResultRules {

    /** Number of sets needed to win a match (best of five). */
    public static final int SETS_TO_WIN = 3;
    private static final int MIN_POINTS = 11;
    private static final int MIN_LEAD = 2;

    /** Status of a single set entry. */
    public enum SetStatus {
        /** Both fields are empty. */
        EMPTY,
        /** Exactly one of the two fields is filled. */
        INCOMPLETE,
        /** A valid set result. */
        VALID,
        /** Both fields are filled, but the score is not a possible set result. */
        INVALID
    }

    private ResultRules() {
    }

    /**
     * Checks a single set result.
     *
     * @param first  points of the first player (may be {@code null} or empty)
     * @param second points of the second player (may be {@code null} or empty)
     * @return the status of the set
     */
    public static SetStatus checkSet(String first, String second) {
        boolean firstEmpty = isBlank(first);
        boolean secondEmpty = isBlank(second);
        if (firstEmpty && secondEmpty) return SetStatus.EMPTY;
        if (firstEmpty || secondEmpty) return SetStatus.INCOMPLETE;

        int a;
        int b;
        try {
            a = Integer.parseInt(first.trim());
            b = Integer.parseInt(second.trim());
        } catch (NumberFormatException _) {
            return SetStatus.INVALID;
        }
        if (a < 0 || b < 0) return SetStatus.INVALID;

        int high = Math.max(a, b);
        int low = Math.min(a, b);
        if (high < MIN_POINTS) return SetStatus.INVALID;
        if (high == MIN_POINTS) return low <= MIN_POINTS - MIN_LEAD ? SetStatus.VALID : SetStatus.INVALID;
        return high - low == MIN_LEAD ? SetStatus.VALID : SetStatus.INVALID;
    }

    /**
     * Counts the valid sets won by each player. Empty, incomplete and invalid sets are ignored.
     *
     * @param sets the set results, one {@code String[2]} per set; rows may be {@code null}
     * @return {@code int[]{setsFirstPlayer, setsSecondPlayer}}
     */
    public static int[] countSets(String[][] sets) {
        int[] won = new int[2];
        if (sets == null) return won;
        for (String[] set : sets) {
            if (set == null || checkSet(set[0], set[1]) != SetStatus.VALID) continue;
            if (Integer.parseInt(set[0].trim()) > Integer.parseInt(set[1].trim())) {
                won[0]++;
            } else {
                won[1]++;
            }
        }
        return won;
    }

    /**
     * Builds the overall result string ("3:1") from the set results.
     *
     * @param sets the set results
     * @return the overall result, or an empty string if no set was entered at all
     */
    public static String overallResult(String[][] sets) {
        if (sets == null) return "";
        boolean anyEntry = false;
        for (String[] set : sets) {
            if (set != null && checkSet(set[0], set[1]) != SetStatus.EMPTY) {
                anyEntry = true;
                break;
            }
        }
        if (!anyEntry) return "";
        int[] won = countSets(sets);
        return won[0] + ":" + won[1];
    }

    /**
     * Returns all problems of the entered set results, in German, ready to show to the user.
     * An empty list means the entry is consistent, but not necessarily complete.
     *
     * @param sets the set results
     * @return list of problem descriptions; empty if there are none
     */
    public static List<String> validateSets(String[][] sets) {
        List<String> problems = new ArrayList<>();
        if (sets == null) return problems;
        int won1 = 0;
        int won2 = 0;
        boolean gap = false;
        for (int i = 0; i < sets.length; i++) {
            String[] set = sets[i] == null ? new String[2] : sets[i];
            SetStatus status = checkSet(set[0], set[1]);
            String label = "Satz " + (i + 1);
            if (status == SetStatus.EMPTY) {
                gap = true;
                continue;
            }
            if (won1 == SETS_TO_WIN || won2 == SETS_TO_WIN) {
                problems.add(label + ": Das Spiel war bereits entschieden.");
                continue;
            }
            if (gap) {
                problems.add(label + ": Ein vorheriger Satz fehlt.");
                gap = false; // report each gap once
            }
            switch (status) {
                case INCOMPLETE -> problems.add(label + ": Bitte beide Werte eintragen.");
                case INVALID -> problems.add(label + ": " + set[0].trim() + ":" + set[1].trim()
                        + " ist kein gültiges Satzergebnis.");
                case VALID -> {
                    if (Integer.parseInt(set[0].trim()) > Integer.parseInt(set[1].trim())) {
                        won1++;
                    } else {
                        won2++;
                    }
                }
                default -> {
                }
            }
        }
        return problems;
    }

    /**
     * Returns {@code true} if the set results describe a finished, consistent match:
     * no problems and one player has won three sets.
     *
     * @param sets the set results
     * @return {@code true} if the match is complete
     */
    public static boolean isComplete(String[][] sets) {
        if (!validateSets(sets).isEmpty()) return false;
        int[] won = countSets(sets);
        return Math.max(won[0], won[1]) == SETS_TO_WIN;
    }

    /**
     * Returns {@code true} if the overall result string describes a decided best-of-five match
     * ("3:0", "3:1", "3:2" or the reverse).
     *
     * @param overallResult the overall result, e.g. "3:1"
     * @return {@code true} if the result is a decided match
     */
    public static boolean isDecidedOverallResult(String overallResult) {
        int[] sets = parseOverallResult(overallResult);
        if (sets == null) return false;
        int high = Math.max(sets[0], sets[1]);
        int low = Math.min(sets[0], sets[1]);
        return high == SETS_TO_WIN && low >= 0 && low < SETS_TO_WIN;
    }

    /**
     * Parses an overall result like "3:1".
     *
     * @param overallResult the overall result
     * @return {@code int[]{first, second}}, or {@code null} if it cannot be parsed
     */
    public static int[] parseOverallResult(String overallResult) {
        if (overallResult == null) return null;
        String[] parts = overallResult.split(":");
        if (parts.length != 2) return null;
        try {
            return new int[]{Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim())};
        } catch (NumberFormatException _) {
            return null;
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
