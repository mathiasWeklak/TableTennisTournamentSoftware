package controller;

import model.GroupKoState;
import model.Match;
import model.Player;
import model.ResultRules;
import model.TournamentMode;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Handles all pairing logic for a tournament, supporting both the Swiss System
 * and Round Robin (Jeder-gegen-Jeden) modes.
 *
 * <p>In Swiss System mode, each round is paired as a whole by a minimum-cost perfect matching
 * (no rematches; few and small score jumps; float history; upper half against lower half).
 * A bye (Freilos) is assigned to the lowest-ranked player without a previous bye when the
 * number of active players is odd.</p>
 *
 * <p>The order of {@code playerList} is the seeding order (TTR descending, ties decided by lot at
 * the start). It is the last tie-break everywhere, so pairing and standings agree.</p>
 *
 * <p>In Round Robin mode, the circle method (Berger schedule) is used to generate one
 * complete schedule for {@code n-1} rounds, guaranteeing every player meets every other
 * player exactly once.</p>
 */
public class PairingEngine {

    private final List<Player> playerList;
    private final int tableNumber;
    private final TournamentMode mode;
    private final List<Match> matches;
    private final List<Match> allMatches;
    private Set<Player> byeList;
    private boolean finished;
    /** Players who withdrew from the tournament; they are not paired any more. */
    private final Set<Player> withdrawn = new HashSet<>();
    /** Groups and bracket of a group/knockout tournament (mode GROUPS_KO), otherwise null. */
    private GroupKoState groupKo;
    /** Round number of the current round's matches (0 before the first pairing). */
    private int pairedRound;
    /** Players who floated down / up in the previous round; used to avoid identical floats. */
    private Set<Player> previousDownfloaters = Set.of();
    private Set<Player> previousUpfloaters = Set.of();
    /** Players who floated down / up two rounds ago (weaker criterion than the previous round). */
    private Set<Player> twoRoundsAgoDownfloaters = Set.of();
    private Set<Player> twoRoundsAgoUpfloaters = Set.of();

    /**
     * Constructs a new PairingEngine.
     *
     * @param playerList  the list of players participating in the tournament
     * @param tableNumber the number of available tables for match assignment
     * @param mode        the tournament mode (SWISS or ROUND_ROBIN)
     */
    public PairingEngine(List<Player> playerList, int tableNumber, TournamentMode mode) {
        this.playerList = playerList;
        this.tableNumber = tableNumber;
        this.mode = mode;
        this.matches = new ArrayList<>();
        this.allMatches = new ArrayList<>();
        this.byeList = new HashSet<>();
        this.finished = false;
    }

    /**
     * Returns an unmodifiable view of the matches scheduled for the current round.
     *
     * @return the current round's match list
     */
    public List<Match> getMatches() {
        return Collections.unmodifiableList(matches);
    }

    /**
     * Returns an unmodifiable view of the cumulative list of all matches across every round
     * played so far.
     *
     * @return the full match history including the current round
     */
    public List<Match> getAllMatches() {
        return Collections.unmodifiableList(allMatches);
    }

    /**
     * Returns whether the tournament has been marked as finished.
     *
     * @return {@code true} if no further rounds can be generated
     */
    public boolean isFinished() {
        return finished;
    }

    /**
     * Sets the finished state of the tournament.
     *
     * @param finished {@code true} to mark the tournament as finished
     */
    public void setFinished(boolean finished) {
        this.finished = finished;
    }

    /**
     * Marks a player as withdrawn: from the next pairing on he is no longer paired. His matches
     * so far stay in the history and keep counting.
     *
     * @param player the withdrawing player
     */
    public void withdraw(Player player) {
        withdrawn.add(player);
        if (groupKo != null && !groupKo.bracket().isEmpty() && !groupKo.knockoutWithdrawn().contains(player)) {
            groupKo.knockoutWithdrawn().add(player); // knockout stage: the group results stay
        }
    }

    /**
     * Replaces the set of withdrawn players (used when loading a saved tournament).
     *
     * @param players the withdrawn players
     */
    public void setWithdrawn(Collection<Player> players) {
        withdrawn.clear();
        withdrawn.addAll(players);
    }

    /**
     * Returns the withdrawn players.
     *
     * @return an unmodifiable view of the withdrawn players
     */
    public Set<Player> getWithdrawn() {
        return Collections.unmodifiableSet(withdrawn);
    }

    /**
     * Returns the players who are still in the tournament.
     *
     * @return the players without the withdrawn ones, in the original order
     */
    public List<Player> activePlayers() {
        return playerList.stream().filter(p -> !withdrawn.contains(p)).toList();
    }

    /**
     * Returns the players of the current round who are not withdrawn. A player registered late is
     * not part of the running round. Without a current round: all active players.
     *
     * @return the players, in the original order
     */
    public List<Player> currentRoundPlayers() {
        if (matches.isEmpty()) return activePlayers();
        Set<Player> inRound = new HashSet<>();
        for (Match m : matches) {
            inRound.add(m.getFirstPlayer());
            if (m.getSecondPlayer() != null) inRound.add(m.getSecondPlayer());
        }
        return playerList.stream().filter(p -> inRound.contains(p) && !withdrawn.contains(p)).toList();
    }

    /**
     * Returns the players whose matches are struck from the standings: in Round Robin every
     * withdrawn player; in a group/knockout tournament the players who withdrew during the group
     * stage. A withdrawal in the knockout stage keeps the group results (also if the knockout draw
     * is taken back later); the opponent advances by walkover. Not used in the Swiss System.
     *
     * @return the players whose matches are struck
     */
    public Set<Player> annulledPlayers() {
        if (mode != TournamentMode.GROUPS_KO || groupKo == null) {
            return Collections.unmodifiableSet(withdrawn);
        }
        Set<Player> result = new HashSet<>(withdrawn);
        groupKo.knockoutWithdrawn().forEach(result::remove);
        groupKo.bracket().forEach(result::remove); // files of older versions: withdrawn from the bracket
        return result;
    }

    /**
     * Draws the next round. Unlike {@link #clearCurrentRound()} followed by
     * {@link #generatePairings(int)}, the current round stays untouched if no further round is
     * possible.
     *
     * @param round the number of the round to draw
     * @return the pairing text, or {@code null} if no further round is possible
     */
    public String generateNextRound(int round) {
        List<Match> previous = new ArrayList<>(matches);
        matches.clear();
        String text = generatePairings(round);
        if (text == null) {
            matches.clear();
            matches.addAll(previous);
        }
        return text;
    }

    /**
     * Returns {@code true} if any result (also a partial one) is entered in the current round.
     *
     * @return {@code true} with results
     */
    public boolean hasResultsInCurrentRound() {
        return matches.stream().anyMatch(m -> m.getSecondPlayer() != null
                && (!ResultRules.overallResult(m.getResults()).isEmpty() || m.isDecided()));
    }

    /**
     * Returns {@code true} if the current round's draw can be taken back: it is not the first
     * round, no result has been entered in it yet, and the previous round is known.
     *
     * @return {@code true} if {@link #undoLastRound()} is possible
     */
    public boolean canUndoLastRound() {
        if (pairedRound <= 1 || matches.isEmpty()) return false;
        boolean noResults = matches.stream()
                .allMatch(m -> m.getSecondPlayer() == null || ResultRules.overallResult(m.getResults()).isEmpty());
        boolean currentIsLatest = matches.stream().allMatch(m -> m.getRound() == pairedRound);
        boolean previousKnown = allMatches.stream().anyMatch(m -> m.getRound() == pairedRound - 1);
        return noResults && currentIsLatest && previousKnown;
    }

    /**
     * Takes back the current round's draw: its matches are removed and the previous round becomes
     * the current round again.
     *
     * @throws IllegalStateException if {@link #canUndoLastRound()} is {@code false}
     */
    public void undoLastRound() {
        if (!canUndoLastRound()) {
            throw new IllegalStateException("Die letzte Auslosung kann nicht zurückgenommen werden.");
        }
        Set<Match> current = Collections.newSetFromMap(new IdentityHashMap<>());
        current.addAll(matches);
        allMatches.removeIf(current::contains);
        pairedRound--;
        matches.clear();
        allMatches.stream().filter(m -> m.getRound() == pairedRound).forEach(matches::add);
        byeList = new HashSet<>(getPlayersWithBye(allMatches));
        finished = false;
        if (groupKo != null && pairedRound <= groupRounds()) {
            groupKo.bracket().clear(); // the bracket is drawn again from the (possibly corrected) groups
        }
    }

    /**
     * Returns the number of rounds of a Round Robin tournament with the current field.
     *
     * @return n-1 for an even and n for an odd number of players
     */
    public int roundRobinRounds() {
        int n = playerList.size();
        return n - 1 + (n % 2);
    }

    /**
     * Returns the number of the round the current matches belong to.
     *
     * @return the round number, 0 if unknown
     */
    public int getPairedRound() {
        return pairedRound;
    }

    /**
     * Returns the matches of a given round.
     *
     * @param round the round number
     * @return the matches of that round (empty for unknown rounds)
     */
    public List<Match> getMatchesOfRound(int round) {
        return allMatches.stream().filter(m -> m.getRound() == round).toList();
    }

    /**
     * Clears the match list for the current round, preparing for the next round's pairings.
     */
    public void clearCurrentRound() {
        matches.clear();
    }

    /**
     * Restores the engine's state from previously saved match data.
     * Replaces the in-memory match history and current-round matches with the provided lists,
     * and rebuilds the bye list accordingly.
     *
     * @param savedAllMatches the full match history to restore
     * @param savedMatches    the current round's matches to restore
     */
    public void restoreState(List<Match> savedAllMatches, List<Match> savedMatches) {
        allMatches.clear();
        allMatches.addAll(savedAllMatches);
        matches.clear();
        matches.addAll(savedMatches);
        byeList = new HashSet<>(getPlayersWithBye(allMatches));
        pairedRound = allMatches.stream().mapToInt(Match::getRound).max().orElse(0);
    }

    /**
     * Generates pairings for the current round and returns the display text.
     * Returns {@code null} if the tournament is finished (all combinations played).
     *
     * @param currentRound the current round number (used for Round Robin termination check)
     * @return pairing text to display, or {@code null} if no more rounds are possible
     */
    public String generatePairings(int currentRound) {
        List<Integer> availableTables = IntStream.rangeClosed(1, tableNumber).boxed().collect(Collectors.toList());

        String text = switch (mode) {
            case ROUND_ROBIN -> generatePairingsRoundRobin(availableTables, currentRound);
            case GROUPS_KO -> generatePairingsGroupsKo(availableTables, currentRound);
            case SWISS -> generatePairingsSwissSystem(new ArrayList<>(activePlayers()), availableTables, currentRound);
        };
        if (text != null) {
            pairedRound = currentRound;
            stampPairingInfo();
            queueSurplusMatches();
        }
        return text;
    }

    /**
     * With more matches than tables, every table is given once; the other matches wait (table 0)
     * and are called by {@link #assignFreeTables()} when a table becomes free.
     */
    private void queueSurplusMatches() {
        Set<Integer> given = new HashSet<>();
        for (Match m : matches) {
            if (m.getSecondPlayer() != null && !m.isDecided() && m.getTableNumber() > 0 && !given.add(m.getTableNumber())) {
                m.setTable(0);
            }
        }
    }

    /**
     * Calls waiting matches of the current round to free tables: a table is free if no undecided
     * match of the current round is played on it. Matches are called in the order of the pairing.
     * Struck matches (withdrawal in Round Robin or group stage) do not block a table.
     *
     * @return the matches that got a table now
     */
    public List<Match> assignFreeTables() {
        Set<Player> struck = mode == TournamentMode.SWISS ? Set.of() : annulledPlayers();
        Set<Integer> used = new HashSet<>();
        List<Match> waiting = new ArrayList<>();
        for (Match m : matches) {
            if (m.getSecondPlayer() == null || m.isDecided()) continue;
            boolean isStruck = m.getKoRound() == 0
                    && (struck.contains(m.getFirstPlayer()) || struck.contains(m.getSecondPlayer()));
            if (isStruck) continue;
            if (m.getTableNumber() > 0) used.add(m.getTableNumber());
            else waiting.add(m);
        }
        List<Match> called = new ArrayList<>();
        int table = 1;
        for (Match m : waiting) {
            while (table <= tableNumber && used.contains(table)) table++;
            if (table > tableNumber) break;
            m.setTable(table);
            used.add(table);
            called.add(m);
        }
        return called;
    }

    /**
     * Stores the round number and the players' current points in every match of the current round.
     * The points are the standings before the round, because results of this round are not entered yet.
     */
    private void stampPairingInfo() {
        for (Match m : matches) {
            m.setPairingInfo(pairedRound, m.getFirstPlayer().getPoints(),
                    m.getSecondPlayer() == null ? 0 : m.getSecondPlayer().getPoints());
        }
    }

    /**
     * Returns the players who floated down (including a bye) or up in the given round.
     *
     * @param round the round number
     * @param down  {@code true} for downfloaters, {@code false} for upfloaters
     * @return the set of players; empty for unknown rounds
     */
    public Set<Player> floatersOfRound(int round, boolean down) {
        Set<Player> result = new HashSet<>();
        if (round <= 0) return result;
        for (Match m : allMatches) {
            if (m.getRound() != round) continue;
            for (Player p : new Player[]{m.getFirstPlayer(), m.getSecondPlayer()}) {
                if (p != null && (down ? m.isDownfloatFor(p) : m.isUpfloatFor(p))) {
                    result.add(p);
                }
            }
        }
        return result;
    }

    /**
     * Generates Swiss System pairings for the current round.
     * If the player count is odd, the bye goes to the lowest-ranked player without a previous bye
     * for whom the remaining players can still be paired without rematches; the next candidates are
     * tried in ranking order. Pairings are found by backtracking. Falls back to a force-pairing
     * strategy only if no bye candidate leads to a valid result.
     *
     * @param sortedList      the player list to pair, sorted by ranking
     * @param availableTables the pool of table numbers to assign
     * @return formatted pairing text, or {@code null} if no valid pairings exist
     */
    private String generatePairingsSwissSystem(List<Player> sortedList, List<Integer> availableTables, int currentRound) {
        if (sortedList.size() < 2) {
            finished = true;
            return null;
        }
        sortByRanking(sortedList);
        // Rebuild from the match history so manual changes (MatchManager) are always respected
        byeList = new HashSet<>(getPlayersWithBye(allMatches));
        previousDownfloaters = floatersOfRound(currentRound - 1, true);
        previousUpfloaters = floatersOfRound(currentRound - 1, false);
        twoRoundsAgoDownfloaters = floatersOfRound(currentRound - 2, true);
        twoRoundsAgoUpfloaters = floatersOfRound(currentRound - 2, false);

        Set<String> playedPairs = allMatches.stream()
                .filter(m -> m.getSecondPlayer() != null)
                .map(m -> createCanonicalPairing(m.getFirstPlayer(), m.getSecondPlayer()))
                .collect(Collectors.toSet());

        // Odd field: try bye candidates from the lowest-ranked upwards until the rest can be paired.
        // Even field: a single attempt without bye (represented by null).
        List<Player> byeCandidates = sortedList.size() % 2 != 0
                ? sortedList.stream().filter(p -> !byeList.contains(p)).toList()
                : Collections.singletonList(null);

        for (Player byePlayer : byeCandidates) {
            List<Player> orderedPlayers = new ArrayList<>(sortedList);
            if (byePlayer != null) {
                orderedPlayers.remove(byePlayer);
            }
            // best first; in round 1 everybody has 0 points, so this is the seeding order
            Collections.reverse(orderedPlayers);

            List<Match> swissPairings;
            try {
                swissPairings = optimalSwissPairing(orderedPlayers, playedPairs);
            } catch (ArithmeticException tooLargeForExactWeights) {
                swissPairings = greedySwissPairing(orderedPlayers, playedPairs, currentRound);
            }
            if (swissPairings != null) {
                if (byePlayer != null) {
                    addByeMatch(byePlayer);
                }
                for (Match match : swissPairings) {
                    if (availableTables.isEmpty()) {
                        availableTables = IntStream.rangeClosed(1, tableNumber).boxed().collect(Collectors.toList());
                    }
                    match.setTable(availableTables.removeFirst());
                    matches.add(match);
                    allMatches.add(match);
                }
                return formatMatchesAsText(matches);
            }
        }

        // no rematch-free pairing exists (for any allowed bye): the tournament is over
        finished = true;
        return null;
    }

    /**
     * Finds the best pairing of the round as a whole (no rematches), by minimum-cost perfect
     * matching. The cost of a pairing is compared level by level; a lower level only decides
     * between pairings that are equal on all higher levels:
     *
     * <ol>
     *   <li>Floaters: sum of the squared point differences of all pairs. Keeps as many players as
     *       possible in their score group and avoids large jumps.</li>
     *   <li>Float history, previous round: number of players who would float down again (a bye
     *       counts as downfloat) or up again.</li>
     *   <li>Float history, two rounds ago: the same for the round before.</li>
     *   <li>Who floats: the lowest-ranked player of the higher group floats down and meets the
     *       highest-ranked player of the lower group.</li>
     *   <li>Pairing order inside a score group: upper half against lower half (1-5, 2-6, ...);
     *       deviations inside the lower half are cheaper than pairing two players of the same
     *       half.</li>
     * </ol>
     *
     * @param players     the players of the round (without bye) in ranking order, best first
     * @param playedPairs canonical keys of pairs that have already played each other
     * @return the pairings, or {@code null} if no rematch-free pairing exists
     * @throws ArithmeticException if the field is too large for exact integer weights
     */
    private List<Match> optimalSwissPairing(List<Player> players, Set<String> playedPairs) {
        int m = players.size();
        if (m == 0) return new ArrayList<>();

        Map<Integer, Integer> groupSize = new HashMap<>();
        int[] pos = new int[m];
        for (int i = 0; i < m; i++) {
            int pts = players.get(i).getPoints();
            int count = groupSize.getOrDefault(pts, 0);
            pos[i] = count;
            groupSize.put(pts, count + 1);
        }

        // Upper bounds of the level sums, so that a lower level can never outweigh a higher one
        long u2 = m + 1L;
        long u3 = m + 1L;
        long u4 = (long) m * m + 1;
        long u5 = (long) m * m + 1;

        List<int[]> edges = new ArrayList<>();
        List<Long> costs = new ArrayList<>();
        long maxCost = 0;
        for (int i = 0; i < m; i++) {
            for (int j = i + 1; j < m; j++) {
                Player a = players.get(i);
                Player b = players.get(j);
                if (playedPairs.contains(createCanonicalPairing(a, b))) continue;

                long l1 = 0, l2 = 0, l3 = 0, l4 = 0, l5 = 0;
                int hi = a.getPoints() >= b.getPoints() ? i : j;
                int lo = hi == i ? j : i;
                Player high = players.get(hi);
                Player low = players.get(lo);
                int d = high.getPoints() - low.getPoints();
                if (d == 0) {
                    int g = groupSize.get(high.getPoints());
                    int half = g / 2;
                    int pu = Math.min(pos[i], pos[j]);
                    int pv = Math.max(pos[i], pos[j]);
                    l5 = (pu < half && pv >= half) ? Math.abs(pv - (pu + half)) : g + Math.abs(pv - pu - half);
                } else {
                    l1 = (long) d * d;
                    l2 = (previousDownfloaters.contains(high) ? 1 : 0) + (previousUpfloaters.contains(low) ? 1 : 0);
                    l3 = (twoRoundsAgoDownfloaters.contains(high) ? 1 : 0) + (twoRoundsAgoUpfloaters.contains(low) ? 1 : 0);
                    l4 = (groupSize.get(high.getPoints()) - 1 - pos[hi]) + pos[lo];
                }
                long cost = Math.addExact(Math.multiplyExact(l1, u2), l2);
                cost = Math.addExact(Math.multiplyExact(cost, u3), l3);
                cost = Math.addExact(Math.multiplyExact(cost, u4), l4);
                cost = Math.addExact(Math.multiplyExact(cost, u5), l5);
                edges.add(new int[]{i, j});
                costs.add(cost);
                maxCost = Math.max(maxCost, cost);
            }
        }

        // maximum weight among perfect matchings == minimum cost
        long base = Math.addExact(maxCost, 1);
        Math.multiplyExact(base, 4L * m); // dual variables and slacks must stay within long range
        long[] weights = new long[costs.size()];
        for (int k = 0; k < weights.length; k++) weights[k] = base - costs.get(k);

        int[] mate = WeightedMatching.maxWeightMatching(m, edges.toArray(new int[0][]), weights, true);
        List<Match> result = new ArrayList<>();
        for (int i = 0; i < m; i++) {
            if (mate[i] < 0) return null; // no perfect matching without rematches
            if (i < mate[i]) result.add(new Match(players.get(i), players.get(mate[i]), -1));
        }
        return result;
    }

    /**
     * Fallback for fields too large for {@link #optimalSwissPairing}: greedy pairing from the top
     * with look-ahead and backtracking.
     *
     * @return the pairings, or {@code null} if no rematch-free pairing exists
     */
    private List<Match> greedySwissPairing(List<Player> players, Set<String> playedPairs, int currentRound) {
        MatchingOracle oracle = new MatchingOracle(players, playedPairs);
        if (!oracle.hasPerfectMatching(oracle.unpaired(Set.of()))) {
            return null;
        }
        List<Match> result = new ArrayList<>();
        return backtrackSwissPairing(players, new HashSet<>(), result, oracle, currentRound) ? result : null;
    }

    /**
     * Recursively pairs all remaining unpaired players using backtracking.
     * At each step the first unpaired player is selected and matched against the best available
     * opponent (ordered by closeness in points). Only pairings that still allow all remaining
     * players to be fully matched are explored.
     *
     * @param players the complete ordered player list for this round
     * @param paired  the set of players already assigned a match in the current recursion branch
     * @param result  the accumulating list of confirmed match pairings
     * @return {@code true} if a complete valid pairing was found, {@code false} otherwise
     */
    private boolean backtrackSwissPairing(List<Player> players, Set<Player> paired, List<Match> result,
                                           MatchingOracle oracle, int currentRound) {
        Player current = players.stream()
                .filter(p -> !paired.contains(p))
                .findFirst()
                .orElse(null);
        if (current == null) return true;

        for (Player opponent : getSwissOrderedOpponents(current, players, paired, oracle, currentRound)) {
            paired.add(current);
            paired.add(opponent);

            if (oracle.hasPerfectMatching(oracle.unpaired(paired))) {
                result.add(new Match(current, opponent, -1));
                if (backtrackSwissPairing(players, paired, result, oracle, currentRound)) {
                    return true;
                }
                result.removeLast();
            }

            paired.remove(current);
            paired.remove(opponent);
        }
        return false;
    }

    /**
     * Returns the valid opponents for {@code player} in the order they should be tried, following
     * the classic (FIDE Dutch) Swiss System. {@code player} is always the best-ranked player that
     * is still unpaired in this round. A player qualifies as an opponent if they are still unpaired
     * and have never played {@code player} before.
     *
     * <ol>
     *   <li>Closest in tournament points first (own score group before lower groups).</li>
     *   <li>Inside the own score group: top half against bottom half. With the group's unpaired
     *       players in ranking order (S1, S2, ...), the first plays the first of the lower half
     *       (1 vs 5, 2 vs 6, 3 vs 7, 4 vs 8 in a group of eight). If that opponent is not possible,
     *       the next players of the lower half are tried first (5, 6, 7, 8), then the upper half
     *       from the bottom up.</li>
     *   <li>In an odd group one player floats down: the lowest-ranked one who did not float down
     *       (or get a bye) in the previous round. The others pair as above without him.</li>
     *   <li>From a lower score group (a player floating down): players who did not float up in the
     *       previous round first, then the strongest according to the ranking.</li>
     * </ol>
     *
     * <p>In round 1 every player has zero points, so the whole field is one score group ordered by
     * TTR, which gives the usual "upper half against lower half" pairing.</p>
     *
     * @param player  the player to find opponents for
     * @param players the player list for this round in ranking order (best first)
     * @param paired  the set of players already paired in the current backtracking branch
     * @param oracle  knows which players may still play each other
     * @return ordered list of eligible opponents
     */
    private List<Player> getSwissOrderedOpponents(Player player, List<Player> players, Set<Player> paired,
                                                   MatchingOracle oracle, int currentRound) {
        Map<Player, Integer> rankIndex = indexOf(players);

        // the own score group's unpaired players in ranking order; player is at index 0
        List<Player> group = players.stream()
                .filter(p -> !paired.contains(p) && p.getPoints() == player.getPoints())
                .toList();

        // Odd group: one player has to float down. Classic choice: the lowest-ranked player who did
        // not already float down (or get a bye) in the previous round. The group's top player only
        // floats if he is alone.
        Player floater = group.size() > 1 && group.size() % 2 != 0 ? chooseDownfloater(group) : null;
        List<Player> pairingGroup = floater == null ? group
                : group.stream().filter(p -> !p.equals(floater)).toList();

        Map<Player, Integer> groupIndex = indexOf(pairingGroup);
        int groupSize = groupIndex.size();
        int lowerHalfStart = groupSize / 2;

        return players.stream()
                .filter(p -> !paired.contains(p) && !p.equals(player) && oracle.canPlay(player, p))
                .sorted(Comparator
                        .comparingInt((Player p) -> Math.abs(p.getPoints() - player.getPoints()))
                        .thenComparingInt(p -> {
                            Integer idx = groupIndex.get(p);
                            if (idx != null) {
                                // own group: lower half in order, then upper half from the bottom up
                                return idx >= lowerHalfStart ? idx - lowerHalfStart : groupSize + (lowerHalfStart - idx);
                            }
                            if (p.equals(floater)) {
                                return 2 * groupSize + 1; // the designated floater only as last resort
                            }
                            // lower group: prefer players who did not float up last round, then strongest first
                            return (previousUpfloaters.contains(p) ? players.size() : 0) + rankIndex.get(p);
                        })
                        .thenComparing(Comparator.comparingInt(Player::getTtr).reversed()))
                .toList();
    }

    /**
     * Picks the player of an odd score group who floats down: the lowest-ranked player (never the
     * group's top player) who did not float down or receive a bye in the previous round. If all of
     * them did, the lowest-ranked player floats.
     *
     * @param group the group's unpaired players in ranking order (best first)
     * @return the player who should float down
     */
    private Player chooseDownfloater(List<Player> group) {
        for (int i = group.size() - 1; i >= 1; i--) {
            if (!previousDownfloaters.contains(group.get(i))) {
                return group.get(i);
            }
        }
        return group.getLast();
    }

    private static Map<Player, Integer> indexOf(List<Player> players) {
        Map<Player, Integer> index = new HashMap<>();
        for (int i = 0; i < players.size(); i++) {
            index.put(players.get(i), i);
        }
        return index;
    }

    /**
     * Answers "can these players still all be paired without a rematch?" for one pairing attempt.
     *
     * <p>Players are indexed once and the "may play each other" relation is precomputed. Results of
     * the perfect-matching search are cached per set of remaining players, so the backtracking does
     * not repeat the same (potentially exponential) search over and over.</p>
     */
    private final class MatchingOracle {
        private final Map<Player, Integer> index;
        private final boolean[][] allowed;
        private final Map<BitSet, Boolean> cache = new HashMap<>();
        private final int size;

        MatchingOracle(List<Player> players, Set<String> playedPairs) {
            this.size = players.size();
            this.index = PairingEngine.indexOf(players);
            this.allowed = new boolean[size][size];
            for (int i = 0; i < size; i++) {
                for (int j = i + 1; j < size; j++) {
                    boolean ok = !playedPairs.contains(createCanonicalPairing(players.get(i), players.get(j)));
                    allowed[i][j] = ok;
                    allowed[j][i] = ok;
                }
            }
        }

        boolean canPlay(Player a, Player b) {
            Integer i = index.get(a);
            Integer j = index.get(b);
            return i != null && j != null && !i.equals(j) && allowed[i][j];
        }

        BitSet unpaired(Set<Player> paired) {
            BitSet bits = new BitSet(size);
            bits.set(0, size);
            for (Player p : paired) {
                Integer i = index.get(p);
                if (i != null) bits.clear(i);
            }
            return bits;
        }

        boolean hasPerfectMatching(BitSet remaining) {
            if (remaining.isEmpty()) return true;
            if (remaining.cardinality() % 2 != 0) return false;
            Boolean cached = cache.get(remaining);
            if (cached != null) return cached;

            // Fast reject: someone without any possible opponent
            boolean result = true;
            for (int i = remaining.nextSetBit(0); i >= 0 && result; i = remaining.nextSetBit(i + 1)) {
                boolean hasOpponent = false;
                for (int j = remaining.nextSetBit(0); j >= 0; j = remaining.nextSetBit(j + 1)) {
                    if (allowed[i][j]) {
                        hasOpponent = true;
                        break;
                    }
                }
                result = hasOpponent;
            }

            if (result) {
                result = false;
                int first = remaining.nextSetBit(0);
                for (int j = remaining.nextSetBit(first + 1); j >= 0; j = remaining.nextSetBit(j + 1)) {
                    if (!allowed[first][j]) continue;
                    BitSet next = (BitSet) remaining.clone();
                    next.clear(first);
                    next.clear(j);
                    if (hasPerfectMatching(next)) {
                        result = true;
                        break;
                    }
                }
            }
            cache.put((BitSet) remaining.clone(), result);
            return result;
        }
    }

    // ---------------------------------------------------------------------------------------
    // Group stage and knockout stage
    // ---------------------------------------------------------------------------------------

    /**
     * Sets the groups (and possibly the bracket) of a group/knockout tournament.
     *
     * @param state the groups and configuration
     */
    public void setGroupKo(GroupKoState state) {
        List<List<Player>> groups = new ArrayList<>();
        for (List<Player> g : state.groups()) groups.add(new ArrayList<>(g));
        this.groupKo = state.withGroupsAndBracket(groups, new ArrayList<>(state.bracket()));
    }

    /**
     * Returns the groups and bracket of a group/knockout tournament.
     *
     * @return a copy of the state, or {@code null} for other modes
     */
    public GroupKoState getGroupKo() {
        if (groupKo == null) return null;
        List<List<Player>> groups = new ArrayList<>();
        for (List<Player> g : groupKo.groups()) groups.add(new ArrayList<>(g));
        return groupKo.withGroupsAndBracket(groups, new ArrayList<>(groupKo.bracket()));
    }

    /**
     * Number of rounds of the group stage.
     *
     * @return the group rounds, 0 if this is no group/knockout tournament
     */
    public int groupRounds() {
        return groupKo == null ? 0 : GroupKnockout.groupRounds(groupKo.groups());
    }

    /**
     * Number of knockout rounds: from the bracket once it exists, otherwise expected from the
     * configuration (all qualifiers present).
     *
     * @return the knockout rounds, 0 if this is no group/knockout tournament
     */
    public int knockoutRounds() {
        if (groupKo == null) return 0;
        if (!groupKo.bracket().isEmpty()) return GroupKnockout.knockoutRounds(groupKo.bracket().size());
        int expected = 0;
        for (List<Player> g : groupKo.groups()) expected += Math.min(groupKo.qualifiersPerGroup(), g.size());
        int nextPlaced = (int) groupKo.groups().stream().filter(g -> g.size() > groupKo.qualifiersPerGroup()).count();
        expected += Math.min(groupKo.bestNextPlaced(), nextPlaced);
        return GroupKnockout.knockoutRounds(GroupKnockout.bracketSize(expected));
    }

    /**
     * Returns the group standings (best first, withdrawn participants removed). Player statistics
     * must be up to date (the score calculator counts only group matches in this mode).
     *
     * @return one list per group
     */
    public List<List<Player>> rankedGroups() {
        List<List<Player>> ranked = new ArrayList<>();
        List<Match> counting = ScoreCalculator.countingMatches(allMatches, mode, annulledPlayers());
        for (List<Player> g : groupKo.groups()) {
            ranked.add(Standings.compute(g, TournamentMode.ROUND_ROBIN, counting).stream()
                    .map(Standings.Entry::player)
                    .filter(p -> !withdrawn.contains(p))
                    .toList());
        }
        return ranked;
    }

    private String generatePairingsGroupsKo(List<Integer> availableTables, int round) {
        int groupRounds = groupRounds();
        List<Match> created = new ArrayList<>();
        if (round <= groupRounds) {
            for (int g = 0; g < groupKo.groups().size(); g++) {
                for (Player[] pair : GroupKnockout.roundRobinPairs(groupKo.groups().get(g), round)) {
                    Player a = pair[0] != null && withdrawn.contains(pair[0]) ? null : pair[0];
                    Player b = pair[1] != null && withdrawn.contains(pair[1]) ? null : pair[1];
                    if (a == null && b == null) continue;
                    Match m = a == null ? new Match(b, null, -1) : new Match(a, b, -1);
                    m.setGroup(g + 1);
                    created.add(m);
                }
            }
        } else {
            int koRound = round - groupRounds;
            if (koRound == 1 || groupKo.bracket().isEmpty()) {
                List<List<Player>> levels = GroupKnockout.qualifiers(rankedGroups(),
                        groupKo.qualifiersPerGroup(), groupKo.bestNextPlaced());
                Map<Player, Integer> groupOf = new HashMap<>();
                for (int g = 0; g < groupKo.groups().size(); g++) {
                    for (Player p : groupKo.groups().get(g)) groupOf.put(p, g + 1);
                }
                int qualified = levels.stream().mapToInt(List::size).sum();
                // a single qualifier (all others withdrew) wins the final by walkover
                if (qualified < 1) {
                    finished = true;
                    return null;
                }
                groupKo.bracket().clear();
                groupKo.bracket().addAll(GroupKnockout.buildBracket(levels, groupOf));
            }
            int total = GroupKnockout.knockoutRounds(groupKo.bracket().size());
            if (koRound > total) {
                finished = true;
                return null;
            }
            List<Match> koMatches = allMatches.stream().filter(m -> m.getKoRound() > 0).toList();
            List<Player> entrants = GroupKnockout.entrants(groupKo.bracket(), koMatches, koRound, withdrawn);
            List<Player[]> pairs = GroupKnockout.knockoutPairs(entrants);
            for (int slot = 0; slot < pairs.size(); slot++) {
                Player[] pair = pairs.get(slot);
                if (pair == null) continue;
                Match m = new Match(pair[0], pair[1], -1);
                m.setKnockout(koRound, slot, false);
                created.add(m);
            }
            if (koRound == total && total >= 2 && groupKo.thirdPlaceMatch()) {
                // match for third place between the losers of the semi-finals
                List<Player> losers = new ArrayList<>();
                int forfeited = 0;
                for (int slot = 0; slot < 2; slot++) {
                    Match semi = GroupKnockout.find(koMatches, total - 1, slot, false);
                    Player loser = semi == null ? null : GroupKnockout.loserOf(koMatches, semi);
                    if (loser == null) continue;
                    if (withdrawn.contains(loser)) forfeited++;
                    else losers.add(loser);
                }
                if (losers.size() == 2 || (losers.size() == 1 && forfeited == 1)) {
                    // one semi-final loser withdrawn (also one who lost his semi-final by walkover): the
                    // other one is third by walkover; a semi-final bye from the draw has no loser, then
                    // there is no match for third place
                    Match third = new Match(losers.get(0), losers.size() == 2 ? losers.get(1) : null, -1);
                    third.setKnockout(koRound, 0, true);
                    created.add(third);
                }
            }
        }
        // a round may consist of byes only (e.g. after a withdrawal): it is kept, the group stage
        // or the knockout stage goes on; only a round without any participant ends the tournament
        if (created.isEmpty()) {
            finished = true;
            return null;
        }
        for (Match m : created) {
            if (m.getSecondPlayer() != null) {
                if (availableTables.isEmpty()) {
                    availableTables = IntStream.rangeClosed(1, tableNumber).boxed().collect(Collectors.toList());
                }
                m.setTable(availableTables.removeFirst());
            }
            matches.add(m);
            allMatches.add(m);
        }
        return formatMatchesAsText(matches);
    }

    /**
     * Generates Round Robin pairings using the circle method (Berger tables).
     * <p>
     * For n players, one player is fixed and the rest rotate by one position each round.
     * For odd n, a virtual "bye" slot is added to make the count even; the player
     * paired with the bye slot receives a Freilos in that round.
     * </p>
     */
    private String generatePairingsRoundRobin(List<Integer> availableTables, int currentRound) {
        int n = playerList.size();
        int totalRounds = n - 1 + (n % 2);

        if (currentRound > totalRounds || activePlayers().size() < 2) {
            finished = true;
            return null;
        }

        // Must stay exactly like this: saved tournaments rely on the same schedule after loading.
        List<Player> players = new ArrayList<>(playerList);
        players.sort(Comparator.comparing(Player::getTtr));

        boolean oddPlayers = (n % 2 != 0);
        List<Player> circle = new ArrayList<>(players);
        if (oddPlayers) {
            circle.add(null);
        }

        int circleSize = circle.size();
        Player fixedPlayer = circle.get(circleSize - 1);
        List<Player> rotating = new ArrayList<>(circle.subList(0, circleSize - 1));

        int r = currentRound - 1;
        int rotLen = circleSize - 1;

        List<Player[]> roundPairings = new ArrayList<>();
        Player top = rotating.get(r % rotLen);
        roundPairings.add(new Player[]{top, fixedPlayer});

        for (int k = 1; k < circleSize / 2; k++) {
            Player left = rotating.get((r + k) % rotLen);
            Player right = rotating.get(((r - k) % rotLen + rotLen) % rotLen);
            roundPairings.add(new Player[]{left, right});
        }

        StringBuilder pairingsText = new StringBuilder();
        for (Player[] pair : roundPairings) {
            Player p1 = pair[0] != null && withdrawn.contains(pair[0]) ? null : pair[0];
            Player p2 = pair[1] != null && withdrawn.contains(pair[1]) ? null : pair[1];
            if (p1 == null && p2 == null) continue;

            if (p1 == null || p2 == null) {
                Player byePlayer = (p1 == null) ? p2 : p1;
                Match byeMatch = new Match(byePlayer, null, -1);
                matches.add(byeMatch);
                allMatches.add(byeMatch);
                pairingsText.append(byePlayer.getFullName()).append(" (").append(byePlayer.getClub()).append(")")
                        .append(" - Freilos").append("\n");
            } else {
                if (availableTables.isEmpty()) {
                    availableTables = IntStream.rangeClosed(1, tableNumber).boxed().collect(Collectors.toList());
                }
                int table = availableTables.removeFirst();
                Match match = new Match(p1, p2, table);
                matches.add(match);
                allMatches.add(match);
                pairingsText.append(p1.getFullName()).append(" (").append(p1.getClub()).append(")")
                        .append(" vs. ")
                        .append(p2.getFullName()).append(" (").append(p2.getClub()).append(")")
                        .append(" - Tisch ").append(table).append("\n");
            }
        }

        return pairingsText.toString();
    }

    /**
     * Adds a bye (Freilos) match for the given player to the current round and the match history.
     *
     * @param byePlayer the player receiving the bye
     */
    private void addByeMatch(Player byePlayer) {
        Match byeMatch = new Match(byePlayer, null, -1);
        matches.add(byeMatch);
        allMatches.add(byeMatch);
        byeList.add(byePlayer);
    }

    /**
     * Sorts players by their ranking, worst first: points, Buchholz, Fein-Buchholz, set difference,
     * ball difference, TTR, and finally the seeding order (a later seed is weaker).
     *
     * @param players the list to sort (in place)
     */
    void sortByRanking(List<Player> players) {
        Map<Player, Integer> seed = indexOf(playerList);
        Comparator<Player> bySeedWeakerFirst = Comparator.comparingInt((Player p) -> seed.getOrDefault(p, Integer.MAX_VALUE)).reversed();
        if (mode == TournamentMode.SWISS) {
            players.sort(Comparator.comparing(Player::getPoints)
                    .thenComparing(Player::getBuchholz)
                    .thenComparing(Player::getFeinBuchholz)
                    .thenComparing(player -> player.getSetsWon() - player.getSetsLost())
                    .thenComparing(player -> player.getBallsWon() - player.getBallsLost())
                    .thenComparing(Player::getTtr)
                    .thenComparing(bySeedWeakerFirst));
        } else {
            players.sort(Comparator.comparing(Player::getPoints)
                    .thenComparing(player -> player.getSetsWon() - player.getSetsLost())
                    .thenComparing(player -> player.getBallsWon() - player.getBallsLost())
                    .thenComparing(Player::getTtr)
                    .thenComparing(bySeedWeakerFirst));
        }
    }

    /**
     * Calculates all matches that could still be played in upcoming rounds, excluding matches
     * already played (except those in the current round, which are still in progress).
     *
     * @return list of all unplayed possible pairings
     */
    public List<Match> calculateAllPossibleOpenMatches() {
        Set<Match> currentRound = Collections.newSetFromMap(new IdentityHashMap<>());
        currentRound.addAll(matches);
        List<Match> playedMatches = new ArrayList<>(allMatches);
        playedMatches.removeIf(currentRound::contains);
        return calculatePairingDifference(generateAllPairings(currentRoundPlayers()), playedMatches, getPlayersWithBye(playedMatches));
    }

    /**
     * Replaces the current round's matches with the given selection and returns the display text.
     */
    public String setNewMatches(List<Match> selectedMatches) {
        // Remove exactly the current round's match objects (identity), never an earlier match
        // that merely has the same players (Match.equals compares the player pair)
        Set<Match> currentRound = Collections.newSetFromMap(new IdentityHashMap<>());
        currentRound.addAll(matches);
        // points before the round, as stamped at the pairing; the current points may already
        // contain the bye point or results of this round
        Map<Player, Integer> pointsBefore = new HashMap<>();
        for (Match m : matches) {
            if (m.getRound() <= 0) continue;
            pointsBefore.put(m.getFirstPlayer(), m.getPointsBefore(m.getFirstPlayer()));
            if (m.getSecondPlayer() != null) pointsBefore.put(m.getSecondPlayer(), m.getPointsBefore(m.getSecondPlayer()));
        }
        allMatches.removeIf(currentRound::contains);
        matches.clear();

        List<Match> byeMatches = new ArrayList<>();
        List<Match> normalMatches = new ArrayList<>();
        for (Match match : selectedMatches) {
            if (match.getSecondPlayer() == null) {
                byeMatches.add(match);
            } else {
                normalMatches.add(match);
            }
        }

        List<Integer> availableTablesRef = new ArrayList<>(
                IntStream.rangeClosed(1, tableNumber).boxed().toList());

        for (Match match : normalMatches) {
            if (availableTablesRef.isEmpty()) {
                availableTablesRef = new ArrayList<>(IntStream.rangeClosed(1, tableNumber).boxed().toList());
            }
            match.setTable(availableTablesRef.removeFirst());
        }

        matches.addAll(normalMatches);
        allMatches.addAll(normalMatches);
        for (Match bye : byeMatches) {
            bye.setTable(-1);
        }
        matches.addAll(byeMatches);
        allMatches.addAll(byeMatches);
        queueSurplusMatches();
        byeList = new HashSet<>(getPlayersWithBye(allMatches));
        for (Match m : matches) {
            Player a = m.getFirstPlayer();
            Player b = m.getSecondPlayer();
            m.setPairingInfo(pairedRound, pointsBefore.getOrDefault(a, a.getPoints()),
                    b == null ? 0 : pointsBefore.getOrDefault(b, b.getPoints()));
        }

        return formatMatchesAsText(matches);
    }

    /**
     * Generates every possible unique pairing of players in the given list.
     * If the number of players is odd, one bye match per player is also included.
     *
     * @param players the list of players to generate pairings for
     * @return list of all possible matches (without duplicates)
     */
    public List<Match> generateAllPairings(List<Player> players) {
        Set<String> uniquePairings = new HashSet<>();
        List<Match> allPairings = new ArrayList<>();
        int numPlayers = players.size();
        boolean oddPlayers = numPlayers % 2 != 0;

        for (int i = 0; i < numPlayers - 1; i++) {
            for (int j = i + 1; j < numPlayers; j++) {
                Player player1 = players.get(i);
                Player player2 = players.get(j);
                String pairing = createCanonicalPairing(player1, player2);
                if (uniquePairings.add(pairing)) {
                    allPairings.add(new Match(player1, player2, -1));
                }
            }
        }

        if (oddPlayers) {
            for (Player player : players) {
                allPairings.add(new Match(player, null, -1));
            }
        }

        return allPairings;
    }

    /**
     * Returns the subset of {@code allPairings} that have not yet been played and do not represent
     * a bye for a player who has already received one.
     *
     * @param allPairings    the complete set of possible pairings
     * @param playedPairings the matches that have already been played
     * @param byePlayers     players who have already received a bye and should not receive another
     * @return the remaining, unplayed pairings
     */
    public List<Match> calculatePairingDifference(List<Match> allPairings, List<Match> playedPairings, List<Player> byePlayers) {
        Set<String> playedPairingsSet = playedPairings.stream()
                .map(match -> createCanonicalPairing(match.getFirstPlayer(), match.getSecondPlayer()))
                .collect(Collectors.toSet());

        List<Match> remainingPairings = new ArrayList<>();
        allPairings.forEach(match -> {
            String pairing = createCanonicalPairing(match.getFirstPlayer(), match.getSecondPlayer());
            if (!playedPairingsSet.contains(pairing) && !isByeMatchForAnyPlayer(match, byePlayers)) {
                remainingPairings.add(match);
            }
        });

        return remainingPairings;
    }

    /**
     * Returns {@code true} if the match is a bye match and the player has already received a bye.
     *
     * @param match      the match to check
     * @param byePlayers the list of players who have already had a bye
     * @return {@code true} if the match is a duplicate bye for an already-exempted player
     */
    private boolean isByeMatchForAnyPlayer(Match match, List<Player> byePlayers) {
        return match.getSecondPlayer() == null && byePlayers.contains(match.getFirstPlayer());
    }

    /**
     * Creates a collision-resistant canonical key for a player pair, independent of order.
     * Uses null-character delimiters to prevent name-based ambiguity.
     */
    private String createCanonicalPairing(Player player1, Player player2) {
        String id1 = player1.getFullName() + "\0" + player1.getClub() + "\0" + player1.getTtr();
        String id2 = player2 != null
                ? player2.getFullName() + "\0" + player2.getClub() + "\0" + player2.getTtr()
                : "\0BYE\0";
        return id1.compareTo(id2) <= 0 ? id1 + "\1" + id2 : id2 + "\1" + id1;
    }

    /**
     * Returns a list of all players who have received a bye (Freilos) in the given match list.
     *
     * @param matchList the match list to inspect
     * @return list of players with a bye; may be empty
     */
    public List<Player> getPlayersWithBye(List<Match> matchList) {
        return matchList.stream()
                .filter(m -> m.getSecondPlayer() == null)
                .map(Match::getFirstPlayer)
                .distinct()
                .toList();
    }

    /**
     * Formats a list of matches into display text for the pairings area.
     */
    public String formatMatchesAsText(List<Match> matchList) {
        return matchList.stream()
                .map(match -> match.getSecondPlayer() == null
                        ? match.getFirstPlayer().getFullName() + " (" + match.getFirstPlayer().getClub() + ") - Freilos\n"
                        : match.getFirstPlayer().getFullName() + " (" + match.getFirstPlayer().getClub() + ")"
                                + " vs. "
                                + match.getSecondPlayer().getFullName() + " (" + match.getSecondPlayer().getClub() + ")"
                                + " - Tisch " + match.getTableLabel() + "\n")
                .collect(Collectors.joining());
    }
}
