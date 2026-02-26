package controller;

import model.Match;
import model.Player;
import model.TournamentMode;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Handles all pairing logic for a tournament, supporting both the Swiss System
 * and Round Robin (Jeder-gegen-Jeden) modes.
 *
 * <p>In Swiss System mode, players are paired each round using backtracking to find
 * valid pairings where no two players have already faced each other. Buchholz tiebreakers
 * and points are used to order candidates. A bye (Freilos) is assigned when the player
 * count is odd.</p>
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

        if (mode == TournamentMode.ROUND_ROBIN) {
            return generatePairingsRoundRobin(availableTables, currentRound);
        } else {
            return generatePairingsSwissSystem(new ArrayList<>(playerList), availableTables);
        }
    }

    /**
     * Generates Swiss System pairings for the current round.
     * Assigns a bye if the player count is odd, then uses backtracking to find valid pairings.
     * Falls back to a force-pairing strategy if backtracking yields no valid result.
     *
     * @param sortedList      the player list to pair, sorted by ranking
     * @param availableTables the pool of table numbers to assign
     * @return formatted pairing text, or {@code null} if no valid pairings exist
     */
    private String generatePairingsSwissSystem(List<Player> sortedList, List<Integer> availableTables) {
        sortPlayersByRanking(sortedList, mode);
        Player byePlayer = null;

        if (playerList.size() % 2 != 0) {
            byePlayer = assignByePlayer(sortedList);
            if (byePlayer == null) {
                return forcePairing(availableTables);
            }
        }

        Set<String> playedPairs = allMatches.stream()
                .filter(m -> m.getSecondPlayer() != null)
                .map(m -> createCanonicalPairing(m.getFirstPlayer(), m.getSecondPlayer()))
                .collect(Collectors.toSet());

        List<Player> orderedPlayers = new ArrayList<>(sortedList);
        Collections.reverse(orderedPlayers);

        List<Match> swissPairings = new ArrayList<>();
        if (backtrackSwissPairing(orderedPlayers, new HashSet<>(), swissPairings, playedPairs)) {
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

        if (byePlayer != null) {
            byeList.remove(byePlayer);
            removeLastByeMatch(byePlayer);
        }
        return forcePairing(availableTables);
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
                                           Set<String> playedPairs) {
        Player current = players.stream()
                .filter(p -> !paired.contains(p))
                .findFirst()
                .orElse(null);
        if (current == null) return true;

        for (Player opponent : getSwissOrderedOpponents(current, players, paired, playedPairs)) {
            paired.add(current);
            paired.add(opponent);

            if (canBeFullyMatched(players, paired, playedPairs)) {
                result.add(new Match(current, opponent, -1));
                if (backtrackSwissPairing(players, paired, result, playedPairs)) {
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
     * Returns a list of valid opponents for {@code player} in the Swiss System, ordered by
     * closeness in tournament points, then by TTR descending for deterministic tie-breaking.
     * A player qualifies as a valid opponent if they have not yet been paired this round
     * and have never played {@code player} before.
     *
     * @param player  the player to find opponents for
     * @param players the full player list for this round
     * @param paired  the set of players already paired in the current backtracking branch
     * @return ordered list of eligible opponents
     */
    private List<Player> getSwissOrderedOpponents(Player player, List<Player> players, Set<Player> paired,
                                                   Set<String> playedPairs) {
        return players.stream()
                .filter(p -> !paired.contains(p) && !p.equals(player)
                        && !playedPairs.contains(createCanonicalPairing(player, p)))
                .sorted(Comparator.comparingInt((Player p) -> Math.abs(p.getPoints() - player.getPoints()))
                        .thenComparing(Comparator.comparingInt(Player::getTtr).reversed()))
                .toList();
    }

    /**
     * Checks whether all currently unpaired players can still be matched given the set of already
     * paired players. A player is considered matchable if at least one other unpaired player exists
     * that they have not yet faced.
     *
     * @param players the full player list for this round
     * @param paired  the set of players already assigned to matches in the current branch
     * @return {@code true} if every remaining player has at least one valid opponent
     */
    private boolean canBeFullyMatched(List<Player> players, Set<Player> paired, Set<String> playedPairs) {
        List<Player> remaining = players.stream()
                .filter(p -> !paired.contains(p))
                .collect(Collectors.toCollection(ArrayList::new));
        for (Player player : remaining) {
            boolean hasOpponent = remaining.stream()
                    .anyMatch(p -> !p.equals(player) && !playedPairs.contains(createCanonicalPairing(player, p)));
            if (!hasOpponent) return false;
        }
        return hasCompleteMatching(remaining, playedPairs);
    }

    private boolean hasCompleteMatching(List<Player> remaining, Set<String> playedPairs) {
        if (remaining.isEmpty()) return true;
        Player first = remaining.removeFirst();
        for (int i = 0; i < remaining.size(); i++) {
            Player candidate = remaining.get(i);
            if (!playedPairs.contains(createCanonicalPairing(first, candidate))) {
                remaining.remove(i);
                if (hasCompleteMatching(remaining, playedPairs)) {
                    remaining.add(i, candidate);
                    remaining.addFirst(first);
                    return true;
                }
                remaining.add(i, candidate);
            }
        }
        remaining.addFirst(first);
        return false;
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

        if (currentRound > totalRounds) {
            finished = true;
            return null;
        }

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
            Player p1 = pair[0];
            Player p2 = pair[1];

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
     * Fallback pairing strategy used when the standard Swiss backtracking finds no valid solution.
     * Computes the set of all remaining unplayed pairings and selects a consistent assignment
     * using a separate backtracking pass. Marks the tournament as finished if no valid assignment
     * can be found.
     *
     * @param availableTables the pool of table numbers to assign to matches
     * @return formatted pairing text, or {@code null} if the tournament is finished
     */
    private String forcePairing(List<Integer> availableTables) {
        List<Match> openMatches = calculatePairingDifference(
                generateAllPairings(playerList), allMatches, getPlayersWithBye(allMatches));
        List<Match> forcedMatches = selectUniquePlayerMatches(openMatches, availableTables);

        if (forcedMatches == null || forcedMatches.isEmpty()) {
            finished = true;
            return null;
        }

        matches.clear();
        matches.addAll(forcedMatches);
        allMatches.addAll(forcedMatches);
        byeList = new HashSet<>(getPlayersWithBye(allMatches));

        return formatMatchesAsText(matches);
    }

    /**
     * Selects and assigns a bye (Freilos) player for this round.
     * Picks the lowest-ranked player who has not yet had a bye.
     * The selected player is removed from {@code sortedList} and a bye match is added to both
     * the current-round and full match lists.
     *
     * @param sortedList the ranked player list; the bye player is removed in-place
     * @return the chosen bye player, or {@code null} if none is eligible
     */
    private Player assignByePlayer(List<Player> sortedList) {
        Player byePlayer = sortedList.stream()
                .filter(p -> !byeList.contains(p))
                .findFirst()
                .orElse(null);
        if (byePlayer == null) {
            return null;
        }
        Match byeMatch = new Match(byePlayer, null, -1);
        matches.add(byeMatch);
        allMatches.add(byeMatch);
        byeList = new HashSet<>(getPlayersWithBye(allMatches));
        sortedList.remove(byePlayer);
        return byePlayer;
    }

    /**
     * Removes the most recently added bye match for the given player from both the current-round
     * and full match lists. Used when backtracking requires a different bye assignment.
     *
     * @param byePlayer the player whose bye match should be removed
     */
    private void removeLastByeMatch(Player byePlayer) {
        matches.stream()
                .filter(m -> m.getFirstPlayer().equals(byePlayer) && m.getSecondPlayer() == null)
                .findFirst()
                .ifPresent(m -> {
                    matches.remove(m);
                    allMatches.remove(m);
                });
    }

    /**
     * Sorts players by their ranking criteria. Used for both standings display and pairing order.
     *
     * @param players the list to sort (in place)
     * @param mode    the tournament mode
     */
    static void sortPlayersByRanking(List<Player> players, TournamentMode mode) {
        if (mode == TournamentMode.SWISS) {
            players.sort(Comparator.comparing(Player::getPoints)
                    .thenComparing(Player::getBuchholz)
                    .thenComparing(Player::getFeinBuchholz)
                    .thenComparing(player -> player.getSetsWon() - player.getSetsLost())
                    .thenComparing(player -> player.getBallsWon() - player.getBallsLost())
                    .thenComparing(Player::getTtr));
        } else {
            players.sort(Comparator.comparing(Player::getPoints)
                    .thenComparing(player -> player.getSetsWon() - player.getSetsLost())
                    .thenComparing(player -> player.getBallsWon() - player.getBallsLost())
                    .thenComparing(Player::getTtr));
        }
    }

    /**
     * Calculates all matches that could still be played in upcoming rounds, excluding matches
     * already played (except those in the current round, which are still in progress).
     *
     * @return list of all unplayed possible pairings
     */
    public List<Match> calculateAllPossibleOpenMatches() {
        List<Match> playedMatches = new ArrayList<>(allMatches);
        playedMatches.removeAll(matches);
        return calculatePairingDifference(generateAllPairings(playerList), playedMatches, getPlayersWithBye(playedMatches));
    }

    /**
     * Replaces the current round's matches with the given selection and returns the display text.
     */
    public String setNewMatches(List<Match> selectedMatches) {
        allMatches.removeAll(matches);
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
        matches.addAll(byeMatches);
        allMatches.addAll(byeMatches);

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
     * Selects a set of matches from {@code matchList} such that every player appears in exactly
     * one match. Table numbers are assigned to normal matches from {@code availableTables}.
     * Returns {@code null} if no valid complete assignment covering all players is found.
     *
     * @param matchList       the pool of candidate matches to select from
     * @param availableTables the pool of table numbers to assign
     * @return a valid match assignment covering all players, or {@code null} if impossible
     */
    public List<Match> selectUniquePlayerMatches(List<Match> matchList, List<Integer> availableTables) {
        List<Match> selectedMatches = new ArrayList<>();
        Set<Player> usedPlayers = new HashSet<>();

        if (backtrack(matchList, selectedMatches, usedPlayers, 0, false)) {
            List<Integer> tables = new ArrayList<>(availableTables);
            for (Match match : selectedMatches) {
                if (tables.isEmpty()) {
                    tables = new ArrayList<>(IntStream.rangeClosed(1, tableNumber).boxed().toList());
                }
                match.setTable(tables.removeFirst());
            }

            if (!usedPlayers.equals(new HashSet<>(playerList))) {
                return null;
            }

            return selectedMatches;
        }

        return null;
    }

    /**
     * Backtracking helper for {@link #selectUniquePlayerMatches}. Tries to build a valid
     * complete match assignment from {@code matchList} starting at index {@code start}.
     *
     * @param matchList        the pool of candidate matches
     * @param selectedMatches  accumulating list of selected matches
     * @param usedPlayers      set of players already assigned to a match
     * @param start            the current index in {@code matchList} to try
     * @param byeMatchSelected whether a bye match has already been selected in this branch
     * @return {@code true} if a complete valid assignment was found
     */
    private boolean backtrack(List<Match> matchList, List<Match> selectedMatches, Set<Player> usedPlayers,
                               int start, boolean byeMatchSelected) {
        if (usedPlayers.size() == playerList.size()) {
            return true;
        }

        if (start == matchList.size()) {
            return false;
        }

        for (int i = start; i < matchList.size(); i++) {
            Match match = matchList.get(i);
            Player player1 = match.getFirstPlayer();
            Player player2 = match.getSecondPlayer();

            if (player2 == null) {
                if (!byeMatchSelected) {
                    selectedMatches.add(match);
                    usedPlayers.add(player1);

                    if (backtrack(matchList, selectedMatches, usedPlayers, i + 1, true)) {
                        return true;
                    }

                    selectedMatches.remove(match);
                    usedPlayers.remove(player1);
                }
            } else {
                if (!usedPlayers.contains(player1) && !usedPlayers.contains(player2)) {
                    selectedMatches.add(match);
                    usedPlayers.add(player1);
                    usedPlayers.add(player2);

                    if (backtrack(matchList, selectedMatches, usedPlayers, i + 1, byeMatchSelected)) {
                        return true;
                    }

                    selectedMatches.remove(match);
                    usedPlayers.remove(player1);
                    usedPlayers.remove(player2);
                }
            }
        }

        return false;
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
                                + " - Tisch " + match.getTableNumber() + "\n")
                .collect(Collectors.joining());
    }
}
