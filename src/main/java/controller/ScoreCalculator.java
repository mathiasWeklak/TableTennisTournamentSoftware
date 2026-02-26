package controller;

import model.Match;
import model.Player;

import java.util.*;

/**
 * Calculates and updates player statistics based on completed matches.
 *
 * <p>Given the full list of matches, this class computes each player's points, wins, losses,
 * sets won/lost, balls won/lost, Buchholz score, and Fein-Buchholz score. Results are written
 * directly to the {@link Player} objects.</p>
 *
 * <p>Bye matches (where the second player is {@code null}) award the bye recipient one point,
 * one win, 3 sets won, and 33 balls won. Duplicate bye matches for the same player are
 * deduplicated before processing to avoid double-counting.</p>
 */
public class ScoreCalculator {

    private static final int BYE_SETS_AWARDED = 3;
    private static final int BYE_BALLS_AWARDED = 33;

    private final List<Match> allMatches;

    /**
     * Constructs a ScoreCalculator bound to the given match history.
     *
     * @param allMatches the cumulative list of all matches played so far
     */
    public ScoreCalculator(List<Match> allMatches) {
        this.allMatches = allMatches;
    }

    /**
     * Resets all player statistics to zero, then recalculates them from scratch
     * based on the current match history. Also computes Buchholz and Fein-Buchholz tiebreakers.
     *
     * @param players the list of players whose statistics should be recalculated
     */
    public void calculate(List<Player> players) {
        players.forEach(player -> {
            player.setPoints(0);
            player.setWins(0);
            player.setLosses(0);
            player.setSetsWon(0);
            player.setSetsLost(0);
            player.setBallsWon(0);
            player.setBallsLost(0);
            player.setBuchholz(0);
            player.setFeinBuchholz(0);
        });

        List<Match> matchesToProcess = deduplicateMatches(allMatches);

        for (Match match : matchesToProcess) {
            Player p1 = match.getFirstPlayer();
            Player p2 = match.getSecondPlayer();

            if (p2 == null) {
                p1.setPoints(p1.getPoints() + 1);
                p1.setWins(p1.getWins() + 1);
                p1.setSetsWon(p1.getSetsWon() + BYE_SETS_AWARDED);
                p1.setBallsWon(p1.getBallsWon() + BYE_BALLS_AWARDED);
            } else {
                String[] result = match.getOverallResult().split(":");
                if (result.length == 2) {
                    try {
                        int s1 = Integer.parseInt(result[0]);
                        int s2 = Integer.parseInt(result[1]);

                        if (s1 > s2) {
                            p1.setPoints(p1.getPoints() + 1);
                            p1.setWins(p1.getWins() + 1);
                            p2.setLosses(p2.getLosses() + 1);
                        } else if (s2 > s1) {
                            p2.setPoints(p2.getPoints() + 1);
                            p2.setWins(p2.getWins() + 1);
                            p1.setLosses(p1.getLosses() + 1);
                        }

                        p1.setSetsWon(p1.getSetsWon() + s1);
                        p1.setSetsLost(p1.getSetsLost() + s2);
                        p2.setSetsWon(p2.getSetsWon() + s2);
                        p2.setSetsLost(p2.getSetsLost() + s1);

                        String[][] results = match.getResults();
                        for (String[] set : results) {
                            if (set[0] != null && !set[0].isEmpty() && set[1] != null && !set[1].isEmpty()) {
                                try {
                                    int b1 = Integer.parseInt(set[0]);
                                    int b2 = Integer.parseInt(set[1]);
                                    p1.setBallsWon(p1.getBallsWon() + b1);
                                    p1.setBallsLost(p1.getBallsLost() + b2);
                                    p2.setBallsWon(p2.getBallsWon() + b2);
                                    p2.setBallsLost(p2.getBallsLost() + b1);
                                } catch (NumberFormatException _) {
                                }
                            }
                        }
                    } catch (NumberFormatException _) {
                    }
                }
            }
        }

        players.forEach(p -> p.setBuchholz(calculateBuchholz(p)));
        players.forEach(p -> p.setFeinBuchholz(calculateFeinBuchholz(p)));
    }

    /**
     * Returns a new list with duplicates removed: bye matches are deduplicated per player,
     * and normal matches are deduplicated by object identity to prevent double-counting the
     * same match instance. Does not mutate the original {@code allMatches} list.
     */
    private List<Match> deduplicateMatches(List<Match> matches) {
        Set<Player> seenByePlayers = new HashSet<>();
        Set<Match> seenNormalMatches = Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        List<Match> result = new ArrayList<>();
        for (Match match : matches) {
            if (match.getSecondPlayer() == null) {
                if (seenByePlayers.add(match.getFirstPlayer())) {
                    result.add(match);
                }
            } else {
                if (seenNormalMatches.add(match)) {
                    result.add(match);
                }
            }
        }
        return result;
    }

    /**
     * Calculates the Buchholz score for the given player.
     * The Buchholz score is the sum of points scored by all opponents the player has faced
     * (excluding bye matches).
     *
     * @param player the player whose Buchholz score is to be calculated
     * @return the Buchholz score
     */
    private int calculateBuchholz(Player player) {
        return allMatches.stream()
                .filter(match -> isPlayerInMatch(match, player) && match.getSecondPlayer() != null)
                .mapToInt(match -> getOpponentPoints(match, player))
                .sum();
    }

    /**
     * Calculates the Fein-Buchholz score for the given player.
     * The Fein-Buchholz score is the sum of Buchholz scores of all opponents the player has faced
     * (excluding bye matches), serving as a secondary tiebreaker after Buchholz.
     *
     * @param player the player whose Fein-Buchholz score is to be calculated
     * @return the Fein-Buchholz score
     */
    private int calculateFeinBuchholz(Player player) {
        return allMatches.stream()
                .filter(match -> isPlayerInMatch(match, player) && match.getSecondPlayer() != null)
                .mapToInt(match -> getOpponentBuchholz(match, player))
                .sum();
    }

    /**
     * Returns {@code true} if the player is one of the two participants in the match.
     *
     * @param match  the match to check
     * @param player the player to look for
     * @return {@code true} if the player participated in the match
     */
    private boolean isPlayerInMatch(Match match, Player player) {
        return match.getFirstPlayer().equals(player)
                || (match.getSecondPlayer() != null && match.getSecondPlayer().equals(player));
    }

    /**
     * Returns the current tournament points of the opponent of {@code player} in the given match.
     *
     * @param match  the match containing the player and their opponent
     * @param player the reference player
     * @return the opponent's points
     */
    private int getOpponentPoints(Match match, Player player) {
        return match.getFirstPlayer().equals(player)
                ? match.getSecondPlayer().getPoints()
                : match.getFirstPlayer().getPoints();
    }

    /**
     * Returns the Buchholz score of the opponent of {@code player} in the given match.
     *
     * @param match  the match containing the player and their opponent
     * @param player the reference player
     * @return the opponent's Buchholz score
     */
    private int getOpponentBuchholz(Match match, Player player) {
        return match.getFirstPlayer().equals(player)
                ? match.getSecondPlayer().getBuchholz()
                : match.getFirstPlayer().getBuchholz();
    }
}
