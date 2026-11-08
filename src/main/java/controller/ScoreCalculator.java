package controller;

import model.Match;
import model.Player;
import model.ResultRules;
import model.TournamentMode;

import java.util.*;

/**
 * Calculates and updates player statistics based on completed matches.
 *
 * <p>Given the full list of matches, this class computes each player's points, wins, losses,
 * sets won/lost, balls won/lost, Buchholz score, and Fein-Buchholz score. Results are written
 * directly to the {@link Player} objects.</p>
 *
 * <p>Only decided matches (3:0, 3:1, 3:2 or the reverse) are counted; partially entered results
 * are ignored. Balls are counted only for valid sets.</p>
 *
 * <p>Bye matches (where the second player is {@code null}) are scored as in the classic Swiss
 * System: the bye recipient gets one point, but the bye is not a played match, so it adds no win,
 * no sets and no balls. For Buchholz and Fein-Buchholz the bye counts as a game against a virtual
 * opponent with the player's own points, but at most half the number of rounds ("Mr. 50%"), and
 * the player's own Buchholz. A walkover ("kampflos", see {@link Match#isWalkover()}) is scored
 * normally, but for the tie-breaks it is unplayed: both players count a virtual opponent with
 * the lower score (and the lower Buchholz) of the two. This follows the direction of the FIDE
 * tie-break revision 2026 (C.07, unplayed games). In a group/knockout tournament only group matches count for the statistics, byes
 * are worth nothing. In Round Robin mode all matches of a withdrawn player are annulled (see
 * {@link #setAnnulledPlayers}). The same applies to completed rounds in which a player was not paired at all
 * (late registration, withdrawal). In Round Robin mode a bye ("spielfrei") is worth nothing. Duplicate bye matches for the same player are deduplicated before processing to
 * avoid double-counting.</p>
 */
public class ScoreCalculator {

    private final List<Match> allMatches;
    private final TournamentMode mode;
    private Set<Player> annulledPlayers = Set.of();

    /**
     * Constructs a ScoreCalculator for the Swiss System bound to the given match history.
     *
     * @param allMatches the cumulative list of all matches played so far
     */
    public ScoreCalculator(List<Match> allMatches) {
        this(allMatches, TournamentMode.SWISS);
    }

    /**
     * Constructs a ScoreCalculator bound to the given match history.
     *
     * @param allMatches the cumulative list of all matches played so far
     * @param mode       the tournament mode; decides how a bye is scored
     */
    public ScoreCalculator(List<Match> allMatches, TournamentMode mode) {
        this.allMatches = allMatches;
        this.mode = mode;
    }

    /**
     * Sets the players whose matches are annulled (Round Robin: all matches of a withdrawn player
     * are struck from the standings). The set may be a live view. Ignored in the Swiss System,
     * where the results of a withdrawn player keep counting.
     *
     * @param players the withdrawn players
     */
    public void setAnnulledPlayers(Set<Player> players) {
        this.annulledPlayers = players;
    }

    /**
     * Returns the matches that count for the standings: in Round Robin without the matches of
     * annulled (withdrawn) players, otherwise all matches.
     *
     * @param matches  all matches
     * @param mode     the tournament mode
     * @param annulled withdrawn players
     * @return the counting matches
     */
    public static List<Match> countingMatches(List<Match> matches, TournamentMode mode, Set<Player> annulled) {
        if (mode == TournamentMode.SWISS || annulled.isEmpty()) return matches;
        // Round Robin and group stage: struck; knockout matches stay (a withdrawal there is a walkover)
        return matches.stream()
                .filter(m -> m.getKoRound() > 0
                        || (!annulled.contains(m.getFirstPlayer())
                        && (m.getSecondPlayer() == null || !annulled.contains(m.getSecondPlayer()))))
                .toList();
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

        List<Match> matchesToProcess = deduplicateMatches(countingMatches(allMatches, mode, annulledPlayers));

        for (Match match : matchesToProcess) {
            if (match.getKoRound() > 0) {
                continue; // group/knockout: the table shows the group stage, knockout results are in the bracket
            }
            Player p1 = match.getFirstPlayer();
            Player p2 = match.getSecondPlayer();

            if (p2 == null) {
                // Swiss: a bye is worth one point, but it is not a played match.
                // Round Robin: "spielfrei" is worth nothing.
                if (mode == TournamentMode.SWISS) {
                    p1.setPoints(p1.getPoints() + 1);
                }
            } else {
                // Only decided matches count (see Match#isDecided); partial entries are ignored
                int[] sets = match.decidedSets();
                if (sets == null) {
                    continue;
                }
                int s1 = sets[0];
                int s2 = sets[1];

                Player winner = s1 > s2 ? p1 : p2;
                Player loser = s1 > s2 ? p2 : p1;
                winner.setPoints(winner.getPoints() + 1);
                winner.setWins(winner.getWins() + 1);
                loser.setLosses(loser.getLosses() + 1);

                p1.setSetsWon(p1.getSetsWon() + s1);
                p1.setSetsLost(p1.getSetsLost() + s2);
                p2.setSetsWon(p2.getSetsWon() + s2);
                p2.setSetsLost(p2.getSetsLost() + s1);

                for (String[] set : match.getResults()) {
                    if (set == null || ResultRules.checkSet(set[0], set[1]) != ResultRules.SetStatus.VALID) {
                        continue;
                    }
                    int b1 = Integer.parseInt(set[0].trim());
                    int b2 = Integer.parseInt(set[1].trim());
                    p1.setBallsWon(p1.getBallsWon() + b1);
                    p1.setBallsLost(p1.getBallsLost() + b2);
                    p2.setBallsWon(p2.getBallsWon() + b2);
                    p2.setBallsLost(p2.getBallsLost() + b1);
                }
            }
        }

        // Buchholz first for everyone, Fein-Buchholz needs the final Buchholz values
        Map<Player, Integer> unpaired = unpairedCompletedRounds(players, matchesToProcess);
        int rounds = countedRounds(matchesToProcess);
        players.forEach(p -> {
            int playerRounds = rounds > 0 ? rounds : roundsOf(p, matchesToProcess, unpaired);
            double dummy = dummyScore(p, playerRounds);
            p.setBuchholz(calculateBuchholz(p, matchesToProcess, dummy)
                    + unpaired.getOrDefault(p, 0) * dummy);
        });
        players.forEach(p -> p.setFeinBuchholz(calculateFeinBuchholz(p, matchesToProcess)
                + unpaired.getOrDefault(p, 0) * p.getBuchholz()));
    }

    /**
     * Score of the virtual opponent for a bye or an unpaired round: the player's own points, but
     * at most half the number of rounds ("Mr. 50%", direction of the FIDE tie-break revision 2026).
     */
    private static double dummyScore(Player player, int rounds) {
        return Math.min(player.getPoints(), rounds / 2.0);
    }

    /**
     * Number of rounds that count for the tie-breaks: the highest round with a bye or a decided
     * match. 0 if the matches carry no round number (files of older versions).
     */
    private static int countedRounds(List<Match> matches) {
        return matches.stream()
                .filter(m -> m.getKoRound() == 0 && countsForBuchholz(m))
                .mapToInt(Match::getRound)
                .max().orElse(0);
    }

    /** Fallback without round numbers: the player's counted games plus unpaired rounds. */
    private int roundsOf(Player player, List<Match> matches, Map<Player, Integer> unpaired) {
        long games = matches.stream()
                .filter(m -> m.getKoRound() == 0 && isPlayerInMatch(m, player) && countsForBuchholz(m))
                .count();
        return (int) games + unpaired.getOrDefault(player, 0);
    }

    /**
     * Counts for every player the completed rounds in which he was not paired at all (late
     * registration, withdrawal). Like a bye, each such round counts as a game against a virtual
     * opponent like a bye (own points, at most half the number of rounds). A round is completed when all
     * its matches are decided. Only relevant for the Swiss System; matches without round number
     * (files of older versions) are ignored.
     *
     * @param players all players
     * @param matches the deduplicated matches
     * @return number of unpaired completed rounds per player (missing = 0)
     */
    private Map<Player, Integer> unpairedCompletedRounds(List<Player> players, List<Match> matches) {
        Map<Player, Integer> result = new HashMap<>();
        if (mode != TournamentMode.SWISS) return result;
        Map<Integer, List<Match>> byRound = new HashMap<>();
        for (Match m : allMatches) {
            if (m.getRound() > 0) byRound.computeIfAbsent(m.getRound(), r -> new ArrayList<>()).add(m);
        }
        for (List<Match> round : byRound.values()) {
            boolean completed = round.stream().allMatch(m -> m.getSecondPlayer() == null || m.isDecided());
            if (!completed) continue;
            Set<Player> paired = new HashSet<>();
            for (Match m : round) {
                paired.add(m.getFirstPlayer());
                if (m.getSecondPlayer() != null) paired.add(m.getSecondPlayer());
            }
            for (Player p : players) {
                if (!paired.contains(p)) result.merge(p, 1, Integer::sum);
            }
        }
        return result;
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
     * Calculates the Buchholz score for the given player from the deduplicated match list.
     * The Buchholz score is the sum of points scored by all opponents the player has faced.
     * Each bye counts as a virtual opponent with {@code dummy} points, a walkover as one with the
     * lower score of both players.
     *
     * @param player  the player whose Buchholz score is to be calculated
     * @param matches the deduplicated list of matches to evaluate
     * @param dummy   the score of the virtual opponent for a bye
     * @return the Buchholz score
     */
    private double calculateBuchholz(Player player, List<Match> matches, double dummy) {
        return matches.stream()
                .filter(match -> isPlayerInMatch(match, player) && countsForBuchholz(match))
                .mapToDouble(match -> {
                    if (match.getSecondPlayer() == null) return dummy;
                    int opponent = getOpponentPoints(match, player);
                    // walkover: unplayed, virtual opponent with the lower score of both players
                    return match.isWalkover() ? Math.min(player.getPoints(), opponent) : opponent;
                })
                .sum();
    }

    /**
     * Calculates the Fein-Buchholz score for the given player from the deduplicated match list.
     * The Fein-Buchholz score is the sum of Buchholz scores of all opponents the player has faced,
     * serving as a secondary tiebreaker after Buchholz. Each bye counts as a virtual opponent with
     * the player's own Buchholz, a walkover as one with the lower Buchholz of both players.
     *
     * @param player  the player whose Fein-Buchholz score is to be calculated
     * @param matches the deduplicated list of matches to evaluate
     * @return the Fein-Buchholz score
     */
    private double calculateFeinBuchholz(Player player, List<Match> matches) {
        return matches.stream()
                .filter(match -> isPlayerInMatch(match, player) && countsForBuchholz(match))
                .mapToDouble(match -> {
                    if (match.getSecondPlayer() == null) return player.getBuchholz();
                    double opponent = getOpponentBuchholz(match, player);
                    return match.isWalkover() ? Math.min(player.getBuchholz(), opponent) : opponent;
                })
                .sum();
    }

    /**
     * Only played (decided) matches and byes count for Buchholz; an opponent of a match without
     * result yet (e.g. the running round) does not.
     */
    private static boolean countsForBuchholz(Match match) {
        return match.getSecondPlayer() == null || match.isDecided();
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
    private double getOpponentBuchholz(Match match, Player player) {
        return match.getFirstPlayer().equals(player)
                ? match.getSecondPlayer().getBuchholz()
                : match.getFirstPlayer().getBuchholz();
    }
}
