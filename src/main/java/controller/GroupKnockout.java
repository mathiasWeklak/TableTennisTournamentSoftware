package controller;

import model.Match;
import model.Player;

import java.util.*;

/**
 * Logic of a classic tournament with group stage and knockout stage (no UI).
 *
 * <ul>
 *   <li><b>Groups:</b> fixed maximum size; the participants are distributed in seeding order by the
 *       snake system (A1, B1, C1, C2, B2, A2, A3, ...). If the number does not divide evenly,
 *       some groups have one participant less.</li>
 *   <li><b>Group stage:</b> round robin within every group, all groups play their rounds at the same
 *       time (circle method).</li>
 *   <li><b>Qualification:</b> the first {@code n} of every group, plus optionally the {@code x} best
 *       participants on place {@code n+1} across all groups (compared by points per match, then set
 *       and ball difference per match, then TTR).</li>
 *   <li><b>Bracket:</b> group winners are seeded before runners-up, runners-up before the further
 *       qualifiers, by TTR within each level. Standard seed positions (1 and 2 in different halves,
 *       1-4 in different quarters ...); byes go to the best seeds. Participants of the same group
 *       are placed so that they meet as late as possible: the group winners keep their seeded
 *       positions, the other qualifiers are swapped among each other where necessary.</li>
 *   <li><b>Knockout:</b> winners advance; there is a match for third place between the losers of
 *       the semi-finals. A withdrawn participant is replaced by a bye (walkover).</li>
 * </ul>
 */
public final class GroupKnockout {

    private GroupKnockout() {
    }

    // ---------------------------------------------------------------------------------------
    // Groups
    // ---------------------------------------------------------------------------------------

    /**
     * Distributes the participants into groups by the snake system.
     *
     * @param seeded    participants in seeding order (best first)
     * @param groupSize maximum size of a group (at least 2)
     * @return the groups, each in seeding order
     */
    public static List<List<Player>> assignGroups(List<Player> seeded, int groupSize) {
        if (groupSize < 2) throw new IllegalArgumentException("groupSize < 2");
        int groupCount = Math.max(1, (seeded.size() + groupSize - 1) / groupSize);
        List<List<Player>> groups = new ArrayList<>();
        for (int g = 0; g < groupCount; g++) groups.add(new ArrayList<>());
        for (int i = 0; i < seeded.size(); i++) {
            int pass = i / groupCount;
            int pos = i % groupCount;
            int g = pass % 2 == 0 ? pos : groupCount - 1 - pos;
            groups.get(g).add(seeded.get(i));
        }
        return groups;
    }

    /**
     * Number of rounds of a round robin with {@code n} participants.
     *
     * @param n number of participants
     * @return n-1 for even n, n for odd n, 0 for fewer than 2
     */
    public static int roundRobinRounds(int n) {
        return n < 2 ? 0 : n - 1 + (n % 2);
    }

    /**
     * Number of rounds of the group stage (the largest group decides).
     *
     * @param groups the groups
     * @return the number of group rounds
     */
    public static int groupRounds(List<List<Player>> groups) {
        return groups.stream().mapToInt(g -> roundRobinRounds(g.size())).max().orElse(0);
    }

    /**
     * Pairings of one round of a group by the circle method. The schedule only depends on the
     * order of the group list, so it stays the same after saving and loading.
     *
     * @param group the participants of the group in seeding order
     * @param round the round, 1-based
     * @return pairs {@code {a, b}}; {@code b == null} means "a is spielfrei"; empty if the group
     *         has no game in this round
     */
    public static List<Player[]> roundRobinPairs(List<Player> group, int round) {
        int n = group.size();
        List<Player[]> pairs = new ArrayList<>();
        if (round < 1 || round > roundRobinRounds(n)) return pairs;
        List<Player> circle = new ArrayList<>(group);
        if (n % 2 != 0) circle.add(null);
        int size = circle.size();
        Player fixed = circle.getFirst();
        List<Player> rotating = new ArrayList<>(circle.subList(1, size));
        int len = rotating.size();
        int r = round - 1;
        pairs.add(new Player[]{fixed, rotating.get(r % len)});
        for (int k = 1; k < size / 2; k++) {
            pairs.add(new Player[]{rotating.get((r + k) % len), rotating.get(((r - k) % len + len) % len)});
        }
        // a pair with the virtual "null" participant is a bye for the other one
        List<Player[]> result = new ArrayList<>();
        for (Player[] p : pairs) {
            if (p[0] == null && p[1] == null) continue;
            result.add(p[0] == null ? new Player[]{p[1], null} : p);
        }
        return result;
    }

    // ---------------------------------------------------------------------------------------
    // Qualification
    // ---------------------------------------------------------------------------------------

    /**
     * Determines the qualifiers, grouped into seeding levels: level 0 = group winners, level 1 =
     * runners-up, ..., the last level = the best participants on the next place.
     *
     * @param rankedGroups       every group's standings, best first (withdrawn participants removed)
     * @param qualifiersPerGroup the first n of every group qualify
     * @param bestNextPlaced     plus the x best participants on place n+1
     * @return the levels, each sorted by TTR (best first)
     */
    public static List<List<Player>> qualifiers(List<List<Player>> rankedGroups, int qualifiersPerGroup,
                                                int bestNextPlaced) {
        List<List<Player>> levels = new ArrayList<>();
        for (int place = 0; place < qualifiersPerGroup; place++) {
            List<Player> level = new ArrayList<>();
            for (List<Player> g : rankedGroups) {
                if (place < g.size()) level.add(g.get(place));
            }
            if (!level.isEmpty()) levels.add(byTtr(level));
        }
        if (bestNextPlaced > 0) {
            List<Player> next = new ArrayList<>();
            for (List<Player> g : rankedGroups) {
                if (qualifiersPerGroup < g.size()) next.add(g.get(qualifiersPerGroup));
            }
            next.sort(acrossGroups());
            List<Player> best = next.subList(0, Math.min(bestNextPlaced, next.size()));
            if (!best.isEmpty()) levels.add(byTtr(new ArrayList<>(best)));
        }
        return levels;
    }

    private static List<Player> byTtr(List<Player> players) {
        players.sort(Comparator.comparingInt(Player::getTtr).reversed());
        return players;
    }

    /**
     * Compares participants of different groups (possibly of different size): points per match,
     * set difference per match, ball difference per match, TTR. Best first.
     *
     * @return the comparator
     */
    static Comparator<Player> acrossGroups() {
        return (a, b) -> {
            int c = compareRatio(b.getPoints(), played(b), a.getPoints(), played(a));
            if (c != 0) return c;
            c = compareRatio(b.getSetsWon() - b.getSetsLost(), played(b), a.getSetsWon() - a.getSetsLost(), played(a));
            if (c != 0) return c;
            c = compareRatio(b.getBallsWon() - b.getBallsLost(), played(b), a.getBallsWon() - a.getBallsLost(), played(a));
            if (c != 0) return c;
            return Integer.compare(b.getTtr(), a.getTtr());
        };
    }

    private static int played(Player p) {
        return Math.max(1, p.getWins() + p.getLosses());
    }

    /** Compares x1/y1 with x2/y2 without floating point. */
    private static int compareRatio(long x1, long y1, long x2, long y2) {
        return Long.compare(x1 * y2, x2 * y1);
    }

    // ---------------------------------------------------------------------------------------
    // Bracket
    // ---------------------------------------------------------------------------------------

    /**
     * Returns the smallest power of two that is at least {@code n} (at least 2).
     *
     * @param n number of qualifiers
     * @return the bracket size
     */
    public static int bracketSize(int n) {
        int size = 2;
        while (size < n) size *= 2;
        return size;
    }

    /**
     * Number of knockout rounds for a bracket.
     *
     * @param bracketSize the bracket size (power of two)
     * @return log2(size)
     */
    public static int knockoutRounds(int bracketSize) {
        return Integer.numberOfTrailingZeros(bracketSize);
    }

    /**
     * Standard seeding order: for every slot of the bracket the seed (1-based) placed there.
     * Size 8 gives 1, 8, 4, 5, 2, 7, 3, 6: seeds 1 and 2 can only meet in the final.
     *
     * @param size the bracket size (power of two)
     * @return seed number per slot
     */
    public static int[] seedOrder(int size) {
        List<Integer> order = new ArrayList<>(List.of(1));
        while (order.size() < size) {
            int len = order.size() * 2;
            List<Integer> next = new ArrayList<>();
            for (int seed : order) {
                next.add(seed);
                next.add(len + 1 - seed);
            }
            order = next;
        }
        return order.stream().mapToInt(Integer::intValue).toArray();
    }

    /**
     * Round in which the participants on two bracket slots would meet (1 = first round).
     *
     * @param slotA a slot
     * @param slotB another slot
     * @return the knockout round
     */
    public static int meetingRound(int slotA, int slotB) {
        return 32 - Integer.numberOfLeadingZeros(slotA ^ slotB);
    }

    /**
     * Builds the bracket: seeds by level and TTR, standard seed positions, byes for the best
     * seeds, then participants of the same group are moved apart (only within their seeding level,
     * so the seeding stays intact).
     *
     * @param levels  the qualifiers by seeding level (see {@link #qualifiers})
     * @param groupOf the group number of every participant
     * @return the bracket slots, {@code null} = bye
     */
    public static List<Player> buildBracket(List<List<Player>> levels, Map<Player, Integer> groupOf) {
        List<Player> seeds = new ArrayList<>();
        List<Integer> levelOfSeed = new ArrayList<>();
        for (int l = 0; l < levels.size(); l++) {
            for (Player p : levels.get(l)) {
                seeds.add(p);
                levelOfSeed.add(l);
            }
        }
        int size = bracketSize(seeds.size());
        int[] order = seedOrder(size);
        Player[] slots = new Player[size];
        int[] slotLevel = new int[size];
        Arrays.fill(slotLevel, -1);
        for (int slot = 0; slot < size; slot++) {
            int seed = order[slot] - 1;
            if (seed < seeds.size()) {
                slots[slot] = seeds.get(seed);
                slotLevel[slot] = levelOfSeed.get(seed);
            }
        }
        separateGroups(slots, slotLevel, groupOf);
        return new ArrayList<>(Arrays.asList(slots));
    }

    /**
     * Swaps qualifiers that are not group winners until participants of the same group meet as
     * late as possible (local search; the cost of a pair meeting in round r is 4^(rounds - r)).
     */
    private static void separateGroups(Player[] slots, int[] slotLevel, Map<Player, Integer> groupOf) {
        int rounds = knockoutRounds(slots.length);
        boolean improved = true;
        for (int iteration = 0; improved && iteration < 200; iteration++) {
            improved = false;
            long current = separationCost(slots, groupOf, rounds);
            for (int i = 0; i < slots.length; i++) {
                for (int j = i + 1; j < slots.length; j++) {
                    // group winners stay on their seeded positions; all other qualifiers may be swapped
                    if (slots[i] == null || slots[j] == null || slotLevel[i] == 0 || slotLevel[j] == 0) continue;
                    swap(slots, i, j);
                    long cost = separationCost(slots, groupOf, rounds);
                    if (cost < current) {
                        current = cost;
                        improved = true;
                    } else {
                        swap(slots, i, j);
                    }
                }
            }
        }
    }

    private static long separationCost(Player[] slots, Map<Player, Integer> groupOf, int rounds) {
        long cost = 0;
        for (int i = 0; i < slots.length; i++) {
            if (slots[i] == null) continue;
            for (int j = i + 1; j < slots.length; j++) {
                if (slots[j] == null) continue;
                if (Objects.equals(groupOf.get(slots[i]), groupOf.get(slots[j]))) {
                    cost += 1L << (2 * (rounds - meetingRound(i, j)));
                }
            }
        }
        return cost;
    }

    private static void swap(Player[] a, int i, int j) {
        Player t = a[i];
        a[i] = a[j];
        a[j] = t;
    }

    /**
     * Returns the participants entering a knockout round, slot by slot. Round 1 are the bracket
     * slots; later rounds the winners of the previous round. A withdrawn participant becomes
     * {@code null} (his opponent advances).
     *
     * @param bracket   the bracket
     * @param koMatches all knockout matches so far
     * @param koRound   the knockout round, 1-based
     * @param withdrawn withdrawn participants
     * @return participants per slot ({@code null} = nobody)
     */
    public static List<Player> entrants(List<Player> bracket, List<Match> koMatches, int koRound, Set<Player> withdrawn) {
        List<Player> current = new ArrayList<>();
        for (Player p : bracket) current.add(p == null || withdrawn.contains(p) ? null : p);
        for (int r = 1; r < koRound; r++) {
            List<Player> next = new ArrayList<>();
            for (int slot = 0; slot < current.size() / 2; slot++) {
                Match m = find(koMatches, r, slot, false);
                Player w = m == null ? null : m.getWinner();
                next.add(w == null || withdrawn.contains(w) ? null : w);
            }
            current = next;
        }
        return current;
    }

    /**
     * Returns the pairs of a knockout round: {@code {a, b}}, {@code b == null} for a bye. Slots
     * without any participant produce no pair.
     *
     * @param entrants participants per slot (see {@link #entrants})
     * @return pairs with their slot index
     */
    public static List<Player[]> knockoutPairs(List<Player> entrants) {
        List<Player[]> pairs = new ArrayList<>();
        for (int slot = 0; slot < entrants.size() / 2; slot++) {
            Player a = entrants.get(2 * slot);
            Player b = entrants.get(2 * slot + 1);
            if (a == null && b == null) {
                pairs.add(null);
            } else if (a == null) {
                pairs.add(new Player[]{b, null});
            } else {
                pairs.add(new Player[]{a, b});
            }
        }
        return pairs;
    }

    /**
     * Finds a knockout match.
     *
     * @param koMatches  knockout matches
     * @param koRound    the round
     * @param slot       the slot
     * @param thirdPlace {@code true} for the match for third place
     * @return the match or {@code null}
     */
    public static Match find(List<Match> koMatches, int koRound, int slot, boolean thirdPlace) {
        for (Match m : koMatches) {
            if (m.getKoRound() == koRound && m.getKoSlot() == slot && m.isThirdPlace() == thirdPlace) return m;
        }
        return null;
    }

    /**
     * Name of a knockout round, e.g. "Viertelfinale".
     *
     * @param koRound     the round, 1-based
     * @param totalRounds number of knockout rounds
     * @return the name
     */
    public static String roundName(int koRound, int totalRounds) {
        return switch (totalRounds - koRound) {
            case 0 -> "Finale";
            case 1 -> "Halbfinale";
            case 2 -> "Viertelfinale";
            case 3 -> "Achtelfinale";
            default -> koRound + ". KO-Runde";
        };
    }

    /**
     * Final placing after the knockout stage: 1 final winner, 2 final loser, 3 and 4 from the match
     * for third place (or both semi-final losers on 3 if it was not played), then the losers of
     * earlier rounds sharing a place (5, 9, 17, ...).
     *
     * @param koMatches   all knockout matches
     * @param totalRounds number of knockout rounds
     * @return participants with their place, best first
     */
    public static List<Map.Entry<Player, Integer>> finalPlacing(List<Match> koMatches, int totalRounds) {
        List<Map.Entry<Player, Integer>> result = new ArrayList<>();
        Match finalMatch = find(koMatches, totalRounds, 0, false);
        if (finalMatch != null && finalMatch.getWinner() != null) {
            result.add(Map.entry(finalMatch.getWinner(), 1));
            if (finalMatch.getLoser() != null) result.add(Map.entry(finalMatch.getLoser(), 2));
        }
        Match third = find(koMatches, totalRounds, 0, true);
        if (third != null && third.getWinner() != null) {
            result.add(Map.entry(third.getWinner(), 3));
            if (third.getLoser() != null) result.add(Map.entry(third.getLoser(), 4));
        } else if (totalRounds >= 2) {
            for (Match semi : koMatches) {
                if (semi.getKoRound() == totalRounds - 1 && !semi.isThirdPlace() && semi.getLoser() != null) {
                    result.add(Map.entry(semi.getLoser(), 3));
                }
            }
        }
        for (int r = totalRounds - 2; r >= 1; r--) {
            int place = (1 << (totalRounds - r)) + 1; // quarter-final losers 5, round of 16 losers 9, ...
            for (Match m : koMatches) {
                if (m.getKoRound() == r && !m.isThirdPlace() && m.getLoser() != null) {
                    result.add(Map.entry(m.getLoser(), place));
                }
            }
        }
        return result;
    }
}
