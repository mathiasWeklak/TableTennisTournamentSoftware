package controller;

import model.Match;
import model.Player;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class PairingEngine {

    private final List<Player> playerList;
    private final int tableNumber;
    private final boolean modus;
    private final List<Match> matches;
    private final List<Match> allMatches;
    private List<Player> byeList;
    private boolean finished;

    public PairingEngine(List<Player> playerList, int tableNumber, boolean modus) {
        this.playerList = playerList;
        this.tableNumber = tableNumber;
        this.modus = modus;
        this.matches = new ArrayList<>();
        this.allMatches = new ArrayList<>();
        this.byeList = new ArrayList<>();
        this.finished = false;
    }

    public List<Match> getMatches() {
        return matches;
    }

    public List<Match> getAllMatches() {
        return allMatches;
    }

    public boolean isFinished() {
        return finished;
    }

    public void setFinished(boolean finished) {
        this.finished = finished;
    }

    public void clearCurrentRound() {
        matches.clear();
    }

    public void restoreState(List<Match> savedAllMatches, List<Match> savedMatches) {
        allMatches.clear();
        allMatches.addAll(savedAllMatches);
        matches.clear();
        matches.addAll(savedMatches);
        byeList = new ArrayList<>(getPlayersWithBye(allMatches));
    }

    /**
     * Generates pairings for the current round and returns the display text.
     * Returns {@code null} if the tournament is finished (all combinations played).
     *
     * @param attemptedByePlayers players already tried as bye recipients this round
     * @param currentRound        the current round number (used for Round Robin termination check)
     * @return pairing text to display, or {@code null} if no more rounds are possible
     */
    public String generatePairings(Set<Player> attemptedByePlayers, int currentRound) {
        Set<Player> pairedPlayers = new HashSet<>();
        List<Integer> availableTables = IntStream.rangeClosed(1, tableNumber).boxed().collect(Collectors.toList());

        if (modus) {
            return generateParingsRoundRobin(pairedPlayers, availableTables, currentRound);
        } else {
            return generatePairingsSwissSystem(attemptedByePlayers, new ArrayList<>(playerList), availableTables);
        }
    }

    private String generatePairingsSwissSystem(Set<Player> attemptedByePlayers, List<Player> sortedList,
                                               List<Integer> availableTables) {
        sortPlayers(sortedList);
        Player byePlayer = null;
        String byeText = "";

        if (playerList.size() % 2 != 0) {
            byePlayer = assignByePlayer(sortedList, attemptedByePlayers);
            if (byePlayer == null) {
                return forcePairing(availableTables);
            }
            byeText = byePlayer.getFullName() + " (" + byePlayer.getClub() + ") - Freilos\n";
        }

        List<Player> orderedPlayers = sortedList.stream()
                .sorted(Comparator.comparingInt(Player::getPoints).reversed())
                .collect(Collectors.toList());

        List<Match> swissPairings = new ArrayList<>();
        if (backtrackSwissPairing(orderedPlayers, new HashSet<>(), swissPairings)) {
            StringBuilder pairingsText = new StringBuilder();
            for (Match match : swissPairings) {
                if (availableTables.isEmpty()) {
                    availableTables = IntStream.rangeClosed(1, tableNumber).boxed().collect(Collectors.toList());
                }
                match.setTable(availableTables.removeFirst());
                matches.add(match);
                allMatches.add(match);
                pairingsText.append(match.getFirstPlayer().getFullName()).append(" (").append(match.getFirstPlayer().getClub()).append(")")
                        .append(" vs. ")
                        .append(match.getSecondPlayer().getFullName()).append(" (").append(match.getSecondPlayer().getClub()).append(")")
                        .append(" - Tisch ").append(match.getTableNumber()).append("\n");
            }
            return pairingsText + byeText;
        }

        if (byePlayer != null) {
            byeList.remove(byePlayer);
            removeLastByeMatch(byePlayer);
        }
        return forcePairing(availableTables);
    }

    private boolean backtrackSwissPairing(List<Player> players, Set<Player> paired, List<Match> result) {
        Player current = null;
        for (Player p : players) {
            if (!paired.contains(p)) {
                current = p;
                break;
            }
        }
        if (current == null) return true;

        for (Player opponent : getSwissOrderedOpponents(current, players, paired)) {
            paired.add(current);
            paired.add(opponent);

            if (canBeFullyMatched(players, paired)) {
                result.add(new Match(current, opponent, -1));
                if (backtrackSwissPairing(players, paired, result)) {
                    return true;
                }
                result.removeLast();
            }

            paired.remove(current);
            paired.remove(opponent);
        }
        return false;
    }

    private List<Player> getSwissOrderedOpponents(Player player, List<Player> players, Set<Player> paired) {
        return players.stream()
                .filter(p -> !paired.contains(p) && !p.equals(player) && neverPlayedBefore(player, p))
                .sorted(Comparator.comparingInt((Player p) -> Math.abs(p.getPoints() - player.getPoints())))
                .collect(Collectors.toList());
    }

    private boolean canBeFullyMatched(List<Player> players, Set<Player> paired) {
        List<Player> remaining = players.stream()
                .filter(p -> !paired.contains(p))
                .toList();
        for (Player player : remaining) {
            boolean hasOpponent = remaining.stream()
                    .anyMatch(p -> !p.equals(player) && neverPlayedBefore(player, p));
            if (!hasOpponent) return false;
        }
        return true;
    }

    private String generateParingsRoundRobin(Set<Player> pairedPlayers, List<Integer> availableTables, int currentRound) {
        if (currentRound > playerList.size() - 1 + (playerList.size() % 2)) {
            finished = true;
            return null;
        }

        Player byePlayer = null;
        List<Player> players = new ArrayList<>(playerList);
        players.sort(Comparator.comparing(Player::getTtr));

        if (players.size() % 2 != 0) {
            byePlayer = players.get(byeList.size());
            byeList.add(byePlayer);
        }

        if (byePlayer != null) {
            players.remove(byePlayer);
        }

        StringBuilder pairingsText = new StringBuilder();

        for (int i = 0; i < players.size(); i++) {
            Player player1 = players.get(i);
            Player player2 = null;

            for (int j = i + 1; j < players.size(); j++) {
                Player opponent = players.get(j);
                if (!neverPlayedBefore(player1, opponent)) {
                    continue;
                }
                if (!pairedPlayers.contains(player1) && !pairedPlayers.contains(opponent)) {
                    player2 = opponent;
                }
            }

            if (player2 == null) {
                continue;
            }

            if (availableTables.isEmpty()) {
                availableTables = IntStream.rangeClosed(1, tableNumber).boxed().collect(Collectors.toList());
            }

            int table = availableTables.removeFirst();
            Match match = new Match(player1, player2, table);
            matches.add(match);
            allMatches.add(match);

            pairingsText.append(player1.getFullName()).append(" (").append(player1.getClub()).append(")")
                    .append(" vs. ")
                    .append(player2.getFullName()).append(" (").append(player2.getClub()).append(")")
                    .append(" - Tisch ").append(table).append("\n");

            pairedPlayers.add(player1);
            pairedPlayers.add(player2);
        }

        if (byePlayer != null) {
            matches.add(new Match(byePlayer, null, -1));
            allMatches.add(new Match(byePlayer, null, -1));
            pairingsText.append(byePlayer.getFullName()).append(" (").append(byePlayer.getClub()).append(")")
                    .append(" - Freilos").append("\n");
        }

        return pairingsText.toString();
    }

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

        StringBuilder pairingsText = new StringBuilder();
        for (Match match : matches) {
            if (match.getSecondPlayer() == null) {
                pairingsText.append(match.getFirstPlayer().getFullName())
                        .append(" (").append(match.getFirstPlayer().getClub()).append(")")
                        .append(" - Freilos\n");
            } else {
                pairingsText.append(match.getFirstPlayer().getFullName())
                        .append(" (").append(match.getFirstPlayer().getClub()).append(")")
                        .append(" vs. ")
                        .append(match.getSecondPlayer().getFullName())
                        .append(" (").append(match.getSecondPlayer().getClub()).append(")")
                        .append(" - Tisch ").append(match.getTableNumber()).append("\n");
            }
        }
        return pairingsText.toString();
    }

    private Player assignByePlayer(List<Player> sortedList, Set<Player> attemptedByePlayers) {
        for (Player player : sortedList) {
            if (!byeList.contains(player) && !attemptedByePlayers.contains(player)) {
                Match byeMatch = new Match(player, null, -1);
                matches.add(byeMatch);
                allMatches.add(byeMatch);
                byeList = new ArrayList<>(getPlayersWithBye(allMatches));
                sortedList.remove(player);
                return player;
            }
        }
        return null;
    }

    private void removeLastByeMatch(Player byePlayer) {
        Match lastByeMatch = null;
        for (Match match : matches) {
            if (match.getFirstPlayer().equals(byePlayer) && match.getSecondPlayer() == null) {
                lastByeMatch = match;
                break;
            }
        }
        if (lastByeMatch != null) {
            matches.remove(lastByeMatch);
            allMatches.remove(lastByeMatch);
        }
    }

    private boolean neverPlayedBefore(Player player1, Player player2) {
        return allMatches.stream()
                .noneMatch(match ->
                        (match.getFirstPlayer().equals(player1) && match.getSecondPlayer() != null && match.getSecondPlayer().equals(player2))
                                || (match.getFirstPlayer().equals(player2) && match.getSecondPlayer() != null && match.getSecondPlayer().equals(player1)));
    }

    private void sortPlayers(List<Player> players) {
        if (!modus) {
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

        AtomicReference<List<Integer>> availableTablesRef = new AtomicReference<>(
                IntStream.rangeClosed(1, tableNumber).boxed().collect(Collectors.toList()));

        normalMatches.forEach(match -> {
            if (availableTablesRef.get().isEmpty()) {
                availableTablesRef.set(IntStream.rangeClosed(1, tableNumber).boxed().collect(Collectors.toList()));
            }
            match.setTable(availableTablesRef.get().removeFirst());
        });

        matches.addAll(normalMatches);
        allMatches.addAll(normalMatches);
        matches.addAll(byeMatches);
        allMatches.addAll(byeMatches);

        StringBuilder text = new StringBuilder();
        normalMatches.forEach(match ->
                text.append(match.getFirstPlayer().getFullName()).append(" (").append(match.getFirstPlayer().getClub()).append(")")
                        .append(" vs. ")
                        .append(match.getSecondPlayer().getFullName()).append(" (").append(match.getSecondPlayer().getClub()).append(")")
                        .append(" - Tisch ").append(match.getTableNumber()).append("\n")
        );
        byeMatches.forEach(match ->
                text.append(match.getFirstPlayer().getFullName()).append(" - Freilos\n")
        );

        return text.toString();
    }

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

    private boolean isByeMatchForAnyPlayer(Match match, List<Player> byePlayers) {
        return match.getSecondPlayer() == null && byePlayers.contains(match.getFirstPlayer());
    }

    public List<Match> selectUniquePlayerMatches(List<Match> matchList, List<Integer> availableTables) {
        List<Match> selectedMatches = new ArrayList<>();
        Set<Player> usedPlayers = new HashSet<>();
        AtomicReference<List<Integer>> availableTablesAtomic = new AtomicReference<>(availableTables);

        if (backtrack(matchList, selectedMatches, usedPlayers, 0, false)) {
            selectedMatches.forEach(match -> {
                if (availableTablesAtomic.get().isEmpty()) {
                    availableTablesAtomic.set(IntStream.rangeClosed(1, tableNumber).boxed().collect(Collectors.toList()));
                }
                match.setTable(availableTablesAtomic.get().removeFirst());
            });

            if (!usedPlayers.equals(new HashSet<>(playerList))) {
                return null;
            }

            return selectedMatches;
        }

        return null;
    }

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

    private String createCanonicalPairing(Player player1, Player player2) {
        String player1Name = player1.getFullName();
        String player2Name = player2 != null ? player2.getFullName() : "Bye";

        if (player1Name.compareTo(player2Name) < 0) {
            return player1Name + "-" + player2Name;
        } else {
            return player2Name + "-" + player1Name;
        }
    }

    public List<Player> getPlayersWithBye(List<Match> matchList) {
        Set<Player> playersWithBye = new HashSet<>();
        for (Match match : matchList) {
            if (match.getSecondPlayer() == null) {
                playersWithBye.add(match.getFirstPlayer());
            }
        }
        return new ArrayList<>(playersWithBye);
    }
}
