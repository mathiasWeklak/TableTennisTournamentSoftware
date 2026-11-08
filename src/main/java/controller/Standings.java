package controller;

import model.Match;
import model.Player;
import model.ResultRules;
import model.TournamentMode;

import java.util.*;
import java.util.function.ToIntFunction;

/**
 * Builds the standings (Rangliste) from the player statistics computed by {@link ScoreCalculator}.
 *
 * <p>Swiss System: points, Buchholz, Fein-Buchholz, set difference, ball difference.</p>
 *
 * <p>Round Robin (ITTF procedure): points; players with equal points are separated only by the
 * matches among them, successively by wins, set difference and ball difference. As soon as one
 * step separates some of them, the procedure starts again for every remaining group of equal
 * players, using only the matches within that group. Players who cannot be separated this way are
 * ordered by set difference and ball difference over all matches.</p>
 *
 * <p>Players equal in all criteria share a rank (e.g. 1, 2, 2, 4). Within a shared rank the order
 * is TTR, then seeding order (the order of the given player list).</p>
 */
public final class Standings {

    /**
     * One line of the standings.
     *
     * @param player the player
     * @param rank   the rank (shared by players who are equal in all criteria)
     */
    public record Entry(Player player, int rank) {
    }

    private Standings() {
    }

    /**
     * Computes the standings. Player statistics must already be up to date.
     *
     * @param players all players in seeding order
     * @param mode    the tournament mode
     * @param matches all matches (used for the direct comparison in Round Robin)
     * @return the standings, best first
     */
    public static List<Entry> compute(List<Player> players, TournamentMode mode, List<Match> matches) {
        Map<Player, Integer> seed = new HashMap<>();
        for (int i = 0; i < players.size(); i++) seed.put(players.get(i), i);
        Comparator<Player> withinRank = Comparator.comparingInt(Player::getTtr).reversed()
                .thenComparingInt(p -> seed.getOrDefault(p, Integer.MAX_VALUE));

        List<List<Player>> classes = mode == TournamentMode.ROUND_ROBIN
                ? roundRobinClasses(players, matches)
                : classesBy(new ArrayList<>(players), swissComparator());

        List<Entry> result = new ArrayList<>();
        for (List<Player> cls : classes) {
            int rank = result.size() + 1;
            cls.stream().sorted(withinRank).forEach(p -> result.add(new Entry(p, rank)));
        }
        return result;
    }

    /**
     * Computes the standings without the struck players (withdrawn players whose matches are
     * annulled); they are appended at the end in seeding order and get no real rank (the value
     * is one more than the number of ranked players).
     *
     * @param players all players in seeding order
     * @param mode    the tournament mode
     * @param matches the counting matches
     * @param struck  players without rank
     * @return the standings, best first, struck players last
     */
    public static List<Entry> computeWithout(List<Player> players, TournamentMode mode, List<Match> matches,
                                             Set<Player> struck) {
        if (struck.isEmpty()) return compute(players, mode, matches);
        List<Entry> result = new ArrayList<>(compute(players.stream().filter(p -> !struck.contains(p)).toList(),
                mode, matches));
        int unranked = result.size() + 1;
        players.stream().filter(struck::contains).forEach(p -> result.add(new Entry(p, unranked)));
        return result;
    }

    private static Comparator<Player> swissComparator() {
        return Comparator.comparingInt(Player::getPoints).reversed()
                .thenComparing(Comparator.comparingDouble(Player::getBuchholz).reversed())
                .thenComparing(Comparator.comparingDouble(Player::getFeinBuchholz).reversed())
                .thenComparing(Comparator.comparingInt(Standings::setDiff).reversed())
                .thenComparing(Comparator.comparingInt(Standings::ballDiff).reversed());
    }

    private static int setDiff(Player p) {
        return p.getSetsWon() - p.getSetsLost();
    }

    private static int ballDiff(Player p) {
        return p.getBallsWon() - p.getBallsLost();
    }

    /** Sorts by the comparator (best first) and groups players the comparator considers equal. */
    private static List<List<Player>> classesBy(List<Player> players, Comparator<Player> comparator) {
        players.sort(comparator);
        List<List<Player>> classes = new ArrayList<>();
        for (Player p : players) {
            if (!classes.isEmpty() && comparator.compare(classes.getLast().getFirst(), p) == 0) {
                classes.getLast().add(p);
            } else {
                classes.add(new ArrayList<>(List.of(p)));
            }
        }
        return classes;
    }

    // ---------------------------------------------------------------------------------------
    // Round Robin
    // ---------------------------------------------------------------------------------------

    private static List<List<Player>> roundRobinClasses(List<Player> players, List<Match> matches) {
        Comparator<Player> overall = Comparator.comparingInt(Standings::setDiff).reversed()
                .thenComparing(Comparator.comparingInt(Standings::ballDiff).reversed());
        List<List<Player>> result = new ArrayList<>();
        for (List<Player> pointGroup : classesBy(new ArrayList<>(players),
                Comparator.comparingInt(Player::getPoints).reversed())) {
            for (List<Player> unresolved : resolveDirect(pointGroup, matches)) {
                result.addAll(classesBy(unresolved, overall));
            }
        }
        return result;
    }

    /**
     * ITTF direct comparison for a group of players with equal points. Returns the group split into
     * ordered classes; a class with more than one player could not be separated by the matches among
     * its members.
     */
    static List<List<Player>> resolveDirect(List<Player> tied, List<Match> matches) {
        if (tied.size() <= 1) return List.of(new ArrayList<>(tied));
        Map<Player, int[]> mini = miniTable(tied, matches);
        List<ToIntFunction<Player>> criteria = List.of(p -> mini.get(p)[0], p -> mini.get(p)[1], p -> mini.get(p)[2]);
        for (ToIntFunction<Player> criterion : criteria) {
            List<List<Player>> split = classesBy(new ArrayList<>(tied), Comparator.comparingInt(criterion).reversed());
            if (split.size() > 1) {
                List<List<Player>> result = new ArrayList<>();
                for (List<Player> sub : split) {
                    result.addAll(resolveDirect(sub, matches)); // start again within the subgroup
                }
                return result;
            }
        }
        return List.of(new ArrayList<>(tied));
    }

    /**
     * Mini table of the decided matches among the given players:
     * {@code {wins, set difference, ball difference}} per player.
     */
    private static Map<Player, int[]> miniTable(Collection<Player> group, List<Match> matches) {
        Map<Player, int[]> mini = new HashMap<>();
        for (Player p : group) mini.put(p, new int[3]);
        Set<Match> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Match m : matches) {
            Player a = m.getFirstPlayer();
            Player b = m.getSecondPlayer();
            if (b == null || !seen.add(m) || !mini.containsKey(a) || !mini.containsKey(b)) continue;
            if (m.getKoRound() > 0) continue; // knockout matches are no part of a group or league table
            int[] sets = m.decidedSets();
            if (sets == null) continue;

            int[] ma = mini.get(a);
            int[] mb = mini.get(b);
            if (sets[0] > sets[1]) ma[0]++; else mb[0]++;
            ma[1] += sets[0] - sets[1];
            mb[1] += sets[1] - sets[0];
            for (String[] set : m.getResults()) {
                if (set == null || ResultRules.checkSet(set[0], set[1]) != ResultRules.SetStatus.VALID) continue;
                int diff = Integer.parseInt(set[0].trim()) - Integer.parseInt(set[1].trim());
                ma[2] += diff;
                mb[2] -= diff;
            }
        }
        return mini;
    }

    /**
     * Computes for every player the mini table among all players with the same points:
     * {@code {wins, set difference, ball difference}} from decided matches between them.
     *
     * @param players all players
     * @param matches all matches
     * @return mini table values per player (zeros if the player is alone on his points)
     */
    static Map<Player, int[]> directComparison(List<Player> players, List<Match> matches) {
        Map<Player, int[]> result = new HashMap<>();
        Map<Integer, List<Player>> byPoints = new HashMap<>();
        for (Player p : players) byPoints.computeIfAbsent(p.getPoints(), k -> new ArrayList<>()).add(p);
        for (List<Player> group : byPoints.values()) {
            result.putAll(miniTable(group, matches));
        }
        return result;
    }

    /**
     * Formats a tie-break score: whole numbers without decimals, halves with a comma ("12,5").
     *
     * @param value the score
     * @return the formatted score
     */
    public static String formatScore(double value) {
        if (value == Math.rint(value)) return Long.toString((long) value);
        return String.format(java.util.Locale.GERMANY, "%.1f", value);
    }
}
