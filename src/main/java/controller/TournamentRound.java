package controller;

import model.Match;
import model.Player;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Class for a TournamentRound
 */
public class TournamentRound extends JFrame {
    public final List<Player> playerList;
    private final int tableNumber;
    public final List<Match> matches;
    private final List<Match> allMatches;
    final JTable resultsTable;
    private final JTextArea pairingsTextArea;
    public int currentRound;
    private JLabel currentRoundLabel;
    public List<Player> byeList;
    public boolean finished;
    int attempts;
    private static boolean modus = false;

    /**
     * Constructs a new TournamentRound object with the given list of players, tournament name, and table number.
     * Initializes the tournament round window with title, size, location, layout, and close operation.
     * Sets up panels for displaying tournament information, pairings, results table, and control buttons.
     *
     * @param playerList     The list of players participating in the tournament.
     * @param tournamentName The name of the tournament.
     * @param tableNumber    The table number associated with the tournament round.
     * @param modus          The mode chosen. Either everyone against everyone or swiss system.
     */
    public TournamentRound(List<Player> playerList, String tournamentName, int tableNumber, boolean modus) {
        this.playerList = new ArrayList<>(playerList);
        this.tableNumber = tableNumber;
        this.matches = new ArrayList<>();
        this.allMatches = new ArrayList<>();
        this.pairingsTextArea = new JTextArea(11, 20);
        this.currentRound = 1;
        this.currentRoundLabel = new JLabel("Runde " +currentRound);
        JPanel topPanel = new JPanel(new BorderLayout());
        JLabel titleLabel = new JLabel("Turnier: " + tournamentName);
        this.byeList = new ArrayList<>();
        this.finished = false;
        this.attempts = 0;
        TournamentRound.modus = modus;

        setTitle("Turnierrunde");
        setSize(600, 600);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent windowEvent) {
                if (JOptionPane.showConfirmDialog(null,
                        "Wollen Sie das Turnier wirklich beenden?", "Bestätigung",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.QUESTION_MESSAGE) == JOptionPane.YES_OPTION) {
                    dispose();
                }
            }
        });

        topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.Y_AXIS));
        titleLabel.setFont(new Font("Arial", Font.BOLD, 18));
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        currentRoundLabel = new JLabel("Runde: " + currentRound);
        currentRoundLabel.setFont(new Font("Arial", Font.BOLD, 12));
        currentRoundLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        topPanel.add(titleLabel);
        topPanel.add(currentRoundLabel);
        add(topPanel, BorderLayout.NORTH);

        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.setBorder(BorderFactory.createTitledBorder("Liste der Begegnungen"));
        pairingsTextArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(pairingsTextArea);
        centerPanel.add(scrollPane, BorderLayout.CENTER);
        add(centerPanel, BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel(new BorderLayout());
        JPanel buttonsPanel = getButtonsPanel();

        bottomPanel.add(buttonsPanel, BorderLayout.NORTH);
        resultsTable = new JTable();
        JScrollPane tableScrollPane = new JScrollPane(resultsTable);
        tableScrollPane.setPreferredSize(new Dimension(500, 200));
        bottomPanel.add(tableScrollPane, BorderLayout.CENTER);

        JPanel bottomButtonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        JButton printTableButton = new JButton("Tabelle drucken");
        printTableButton.addActionListener(e -> printPlacementTable());
        bottomButtonPanel.add(printTableButton);
        JButton nextRoundButton = new JButton("Nächste Runde auslosen und starten");
        nextRoundButton.addActionListener(e -> startNextRound());
        bottomButtonPanel.add(nextRoundButton);

        bottomPanel.add(bottomButtonPanel, BorderLayout.SOUTH);
        add(bottomPanel, BorderLayout.SOUTH);

        generatePairings(new HashSet<>());
        updateResultsTable();
    }

    /**
     * Constructs and returns a JPanel containing buttons for various actions related to the tournament.
     * The panel includes buttons for previewing referee sheets, entering match results, and updating the results table.
     *
     * @return JPanel containing the buttons for the tournament actions.
     */
    private JPanel getButtonsPanel() {
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));

        JButton previewRefereeSheetsButton = new JButton("Schiedsrichterzettel anzeigen");
        previewRefereeSheetsButton.addActionListener(e -> previewRefereeSheets());
        bottomPanel.add(previewRefereeSheetsButton);

        JButton resultEntryButton = new JButton("Ergebnisse erfassen");

        resultEntryButton.addActionListener(e -> {
            new ResultEntryController(matches, this);
            updateResultsTable();
        });
        bottomPanel.add(resultEntryButton);

        if(!modus) {
            JButton manipulateSettingButton = getjButton();
            bottomPanel.add(manipulateSettingButton);
        }
        return bottomPanel;
    }

    /**
     * Returns a JButton configured to manipulate match settings.
     *
     * @return A JButton configured for setting manipulation.
     */
    private JButton getjButton() {
        JButton manipulateSettingButton = new JButton("Setzung manipulieren");
        manipulateSettingButton.addActionListener(e -> {
            boolean resultsEntered = matches.stream().anyMatch(match -> (match.getSecondPlayer() != null && !match.getOverallResult().isEmpty()));

            if (!resultsEntered) {
                List<Match> allPossibleOpenMatches = calculateAllPossibleOpenMatches();
                allPossibleOpenMatches.sort(Comparator.comparing(match -> match.getFirstPlayer().getFullName()));
                Set<Match> uniqueAllPossibleOpenMatches = new HashSet<>(allPossibleOpenMatches);

                MatchManagerController manager = new MatchManagerController(uniqueAllPossibleOpenMatches, playerList.size(), this);
                manager.view.setVisible(true);
            } else {
                JOptionPane.showMessageDialog(this, "Setzung kann nicht manipuliert werden, da bereits Ergebnisse eingetragen wurden.", "Ergebnisse vorhanden", JOptionPane.WARNING_MESSAGE);
            }
        });
        return manipulateSettingButton;
    }


    /**
     * Calculates all possible open matches by removing already scheduled matches and adding the original matches.
     *
     * @return A list of all possible open matches.
     */
    private List<Match> calculateAllPossibleOpenMatches() {
        List<Match> allCurrentMatches = allMatches;
        allCurrentMatches.removeAll(matches);

        return calculatePairingDifference(generateAllPairings(playerList), allCurrentMatches, getPlayersWithBye(allCurrentMatches));
    }

    /**
     * Generates pairings for the current round of matches based on the tournament mode.
     * Uses either Round Robin or Swiss System depending on the 'modus' flag.
     *
     * @param attemptedByePlayers A set of players who have been assigned a bye in previous rounds.
     */
    private void generatePairings(Set<Player> attemptedByePlayers) {
        List<Player> sortedList = new ArrayList<>(playerList);
        List<Player> pairedPlayers = new ArrayList<>();
        List<Integer> availableTables = IntStream.rangeClosed(1, tableNumber).boxed().collect(Collectors.toList());

        StringBuilder pairingsText = new StringBuilder();

        if (modus) {
            generateParingsRoundRobin(pairedPlayers, availableTables, pairingsText);
        } else {
            generatePairingsSwissSystem(attemptedByePlayers, sortedList, pairingsText, availableTables, pairedPlayers);
        }
    }

    /**
     * Generates pairings using the Swiss System based on player rankings.
     *
     * @param attemptedByePlayers A set of players who have been assigned a bye in previous rounds.
     * @param sortedList          List of players sorted by performance.
     * @param pairingsText        StringBuilder to accumulate pairing information.
     * @param availableTables     List of available table numbers.
     * @param pairedPlayers       List of players already paired in this round.
     */
    private void generatePairingsSwissSystem(Set<Player> attemptedByePlayers, List<Player> sortedList,
                                             StringBuilder pairingsText, List<Integer> availableTables, List<Player> pairedPlayers) {
        sortPlayers(sortedList);
        Player byePlayer = null;
        if (playerList.size() % 2 != 0) {
            byePlayer = assignByePlayer(sortedList, attemptedByePlayers);
            if (byePlayer == null) {
                forcePairing(pairingsText, availableTables);
                return;
            }
        }

        sortedList = sortedList.stream().sorted(Comparator.comparingInt(Player::getPoints).reversed()).collect(Collectors.toList());
        boolean allPlayersPaired = true;

        for (Player player : sortedList) {
            if (!pairedPlayers.contains(player)) {
                Player opponent = findOpponent(player, sortedList, pairedPlayers, attempts >= 100);
                if (opponent != null) {
                    allPlayersPaired = false;
                    if (availableTables.isEmpty()) {
                        availableTables = IntStream.rangeClosed(1, tableNumber).boxed().collect(Collectors.toList());
                    }
                    Match match = new Match(player, opponent, availableTables.removeFirst());
                    matches.add(match);
                    allMatches.add(match);
                    pairedPlayers.add(player);
                    pairedPlayers.add(opponent);
                    pairingsText.append(player.getFullName()).append(" (").append(player.getClub()).append(")")
                            .append(" vs. ")
                            .append(opponent.getFullName()).append(" (").append(opponent.getClub()).append(")")
                            .append(" - Tisch ").append(match.getTableNumber()).append("\n");
                } else {
                    if (playerList.size() % 2 != 0) {
                        if (attempts >= 100) {
                            if (byePlayer != null) {
                                byeList.remove(byePlayer);
                                sortedList.add(byePlayer);
                                removeLastByeMatch(byePlayer);
                                attemptedByePlayers.add(byePlayer);
                                attempts = 0;
                            }
                        } else {
                            attempts++;
                        }
                    } else {
                        if (attempts >= 100) {
                            forcePairing(pairingsText, availableTables);
                            return;
                        }
                        attempts++;
                    }

                    allMatches.removeAll(matches);
                    matches.clear();
                    generatePairings(attemptedByePlayers);
                    return;
                }
            }
        }
        if (allPlayersPaired || matches.size() < (playerList.size() / 2) + (playerList.size() % 2)) {
            forcePairing(pairingsText, availableTables);
            return;
        }

        attempts = 0;
        pairingsTextArea.setText(pairingsText + pairingsTextArea.getText());
    }

    /**
     * Generates pairings for a Round Robin format based on player TTR (TrueSkill Rating).
     *
     * @param pairedPlayers  List of players already paired in this round.
     * @param availableTables List of available table numbers.
     * @param pairingsText   StringBuilder to accumulate pairing information.
     */
    private void generateParingsRoundRobin(List<Player> pairedPlayers, List<Integer> availableTables, StringBuilder pairingsText) {
        if (currentRound > playerList.size() - 1 + (playerList.size() % 2)) {
            finished = true;
            JOptionPane.showMessageDialog(this, "Es wurden bereits alle möglichen Kombinationen gespielt.", "Keine Paarungen mehr möglich", JOptionPane.INFORMATION_MESSAGE);
            return;
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

            int tableNumber = availableTables.removeFirst();
            Match match = new Match(player1, player2, tableNumber);
            matches.add(match);
            allMatches.add(match);

            pairingsText.append(player1.getFullName()).append(" (").append(player1.getClub()).append(")")
                    .append(" vs. ")
                    .append(player2.getFullName()).append(" (").append(player2.getClub()).append(")")
                    .append(" - Tisch ").append(tableNumber).append("\n");

            pairedPlayers.add(player1);
            pairedPlayers.add(player2);
        }

        if (byePlayer != null) {
            matches.add(new Match(byePlayer, null, -1));
            allMatches.add(new Match(byePlayer, null, -1));
            pairingsText.append(byePlayer.getFullName()).append(" (").append(byePlayer.getClub()).append(")")
                    .append(" - Freilos").append("\n");
        }

        pairingsTextArea.setText(pairingsText.toString());
    }

    /**
     * Forces a unique pairing of players for the next round, updating the GUI display accordingly.
     * If no unique pairing can be found, sets the tournament as finished and shows a message.
     *
     * @param pairingsText the StringBuilder to update with the forced pairings text
     */
    private void forcePairing(StringBuilder pairingsText, List<Integer> availableTables) {
        List<Match> openMatches = calculatePairingDifference(generateAllPairings(playerList), allMatches, getPlayersWithBye(allMatches));
        List<Match> forcedMatches = selectUniquePlayerMatches(openMatches, availableTables);
        if(forcedMatches == null || forcedMatches.isEmpty()) {
            finished = true;
            JOptionPane.showMessageDialog(this, "Es wurden bereits alle möglichen Kombinationen gespielt.", "Keine Paarungen mehr möglich", JOptionPane.INFORMATION_MESSAGE);
        } else {
            matches.clear();
            matches.addAll(forcedMatches);
            allMatches.addAll(forcedMatches);
            matches.forEach(match -> {
                pairingsText.setLength(0);
                if(match.getSecondPlayer() == null) {
                    pairingsText.append(match.getFirstPlayer().getFullName()).append(" (").append(match.getFirstPlayer().getClub()).append(")")
                            .append(" - Freilos").append("\n");
                } else {
                    pairingsText.append(match.getFirstPlayer().getFullName()).append(" (").append(match.getFirstPlayer().getClub()).append(")")
                            .append(" vs. ")
                            .append(match.getSecondPlayer().getFullName()).append(" (").append(match.getSecondPlayer().getClub()).append(")")
                            .append(" - Tisch ").append(match.getTableNumber()).append("\n");
                }
            });
        }
    }

    /**
     * Retrieves a list of players who have received a bye (freilos) based on the provided matches.
     *
     * @param matches the list of matches to analyze
     * @return a list of players who have received a bye
     */
    public List<Player> getPlayersWithBye(List<Match> matches) {
        Set<Player> playersWithBye = new HashSet<>();

        for (Match match : matches) {
            if (match.getSecondPlayer() == null) {
                playersWithBye.add(match.getFirstPlayer());
            }
        }

        return new ArrayList<>(playersWithBye);
    }

    /**
     * Generates a list of all possible unique pairings from a given list of players.
     * If the number of players is odd, a bye match is added for each player.
     *
     * @param players the list of players
     * @return a list of matches representing all possible unique pairings
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
                    Match match = new Match(player1, player2, -1);
                    allPairings.add(match);
                }
            }
        }

        if (oddPlayers) {
            for (Player player : players) {
                Match byeMatch = new Match(player, null, -1);
                allPairings.add(byeMatch);
            }
        }

        return allPairings;
    }

    /**
     * Creates a canonical representation of a pairing.
     * Ensures the pairing is represented consistently as "Player1-Player2" or "Player2-Player1".
     *
     * @param player1 the first player
     * @param player2 the second player
     * @return a canonical representation of the pairing
     */
    private String createCanonicalPairing(Player player1, Player player2) {
        String player1Name = player1.getFullName();
        String player2Name = player2 != null ? player2.getFullName() : "Bye";

        if (player1Name.compareTo(player2Name) < 0) {
            return player1Name + "-" + player2Name;
        } else {
            return player2Name + "-" + player1Name;
        }
    }

    /**
     * Calculates the difference between all possible pairings and already played pairings,
     * excluding bye matches for specified bye players.
     *
     * @param allPairings    the list of all possible pairings
     * @param playedPairings the list of already played pairings
     * @param byePlayers     the list of players who have bye matches
     * @return a list of matches representing the pairings that have not been played yet, excluding bye matches
     */
    public List<Match> calculatePairingDifference(List<Match> allPairings, List<Match> playedPairings, List<Player> byePlayers) {
        Set<String> playedPairingsSet;
        List<Match> remainingPairings = new ArrayList<>();

        playedPairingsSet = playedPairings.stream()
                .map(match -> createCanonicalPairing(match.getFirstPlayer(), match.getSecondPlayer()))
                .collect(Collectors.toSet());

        allPairings.forEach(match -> {
            String pairing = createCanonicalPairing(match.getFirstPlayer(), match.getSecondPlayer());

            if (!playedPairingsSet.contains(pairing) && !isByeMatchForAnyPlayer(match, byePlayers)) {
                remainingPairings.add(match);
            }
        });

        return remainingPairings;
    }

    /**
     * Checks if a match is a bye match for any player in the given list of bye players.
     *
     * @param match      the match to check
     * @param byePlayers the list of players who have bye matches
     * @return true if the match is a bye match for any player, false otherwise
     */
    private boolean isByeMatchForAnyPlayer(Match match, List<Player> byePlayers) {
        return byePlayers.stream().anyMatch(player -> player.equals(match.getSecondPlayer()));
    }

    /**
     * Selects a set of unique player matches from the list of matches,
     * ensuring that if bye matches are present, exactly one bye match is included
     * in the selected matches list. The bye match should be the last match in the list,
     * and no other matches in selectedMatches should include the player who received a bye.
     * Additionally, assigns available tables to each selected match.
     *
     * @param matches         the list of matches to select from
     * @param availableTables the list of available tables to assign to matches
     * @return a list of matches representing the selected unique player matches,
     *         or null if no valid selection could be made or if not every player has a match
     */
    public List<Match> selectUniquePlayerMatches(List<Match> matches, List<Integer> availableTables) {
        List<Match> selectedMatches = new ArrayList<>();
        Set<Player> usedPlayers = new HashSet<>();
        AtomicReference<List<Integer>> availableTablesAtomic = new AtomicReference<>(availableTables);

        if (backtrack(matches, selectedMatches, usedPlayers, 0, false)) {
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
        } else {
            return null;
        }
    }


    /**
     * Backtracks to select a set of unique player matches from the list of matches,
     * ensuring that if bye matches are present, exactly one bye match is included
     * in the selected matches list. The bye match should be the last match in the list,
     * and no other matches in selectedMatches should include the player who received a bye.
     *
     * @param matches         the list of matches to select from
     * @param selectedMatches the list to store selected matches
     * @param usedPlayers     set of players already included in selectedMatches
     * @param start           the starting index in matches to begin selection
     * @param byeMatchSelected flag to track if a bye match has been selected
     * @return true if a valid set of matches was selected; false otherwise
     */
    private boolean backtrack(List<Match> matches, List<Match> selectedMatches, Set<Player> usedPlayers, int start, boolean byeMatchSelected) {
        if (start == matches.size()) {
            return true;
        }

        for (int i = start; i < matches.size(); i++) {
            Match match = matches.get(i);
            Player player1 = match.getFirstPlayer();
            Player player2 = match.getSecondPlayer();

            if (player2 == null) {
                if (!byeMatchSelected) {
                    selectedMatches.add(match);
                    usedPlayers.add(player1);

                    if (backtrack(matches, selectedMatches, usedPlayers, i + 1, true)) {
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

                    if (backtrack(matches, selectedMatches, usedPlayers, i + 1, byeMatchSelected)) {
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
     * Assigns a bye player from the sorted list of players who haven't been assigned a bye yet.
     * A bye match is created with the player and null opponent, added to matches and allMatches lists,
     * and the player is marked as having received a bye.
     *
     * @param sortedList The list of players sorted for pairing.
     * @param attemptedByePlayers Set of players who have already attempted to receive a bye.
     * @return The player assigned a bye, or null if no eligible player is found.
     */
    private Player assignByePlayer(List<Player> sortedList, Set<Player> attemptedByePlayers) {
        for (Player player : sortedList) {
            if (!byeList.contains(player) && !attemptedByePlayers.contains(player)) {
                Match byeMatch = new Match(player, null, -1);
                matches.add(byeMatch);
                allMatches.add(byeMatch);
                byeList = new ArrayList<>(getPlayersWithBye(allMatches));
                sortedList.remove(player);
                pairingsTextArea.setText(player.getFullName() + " (" + player.getClub() + ")" + " - Freilos\n");
                return player;
            }
        }
        return null;
    }

    /**
     * Removes the last bye match associated with the given bye player from the matches and allMatches lists.
     *
     * @param byePlayer The player who received the bye and whose last bye match is to be removed.
     */

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

    /**
     * Finds an opponent for the given player from the sortedList, considering already paired players.
     * Attempts to find opponents first among players with the same points, then among other players.
     *
     * @param player The player for whom an opponent is being sought.
     * @param sortedList The list of players sorted based on some criteria.
     * @param pairedPlayers The list of players who have already been paired in previous matches.
     * @return The opponent player if found; null if no suitable opponent is found.
     */
    private Player findOpponent(Player player, List<Player> sortedList, List<Player> pairedPlayers, boolean forceRandom) {
        List<Player> potentialOpponents = sortedList.stream()
                .filter(p -> !pairedPlayers.contains(p) && !p.equals(player))
                .collect(Collectors.toList());

        if (!forceRandom) {
            List<Player> samePointsPlayers = potentialOpponents.stream()
                    .filter(p -> p.getPoints() == player.getPoints())
                    .collect(Collectors.toList());
            Collections.reverse(samePointsPlayers);

            for (Player opponent : samePointsPlayers) {
                if (neverPlayedBefore(player, opponent)) {
                    return opponent;
                }
            }
        }

        Collections.shuffle(potentialOpponents);
        for (Player opponent : potentialOpponents) {
            if (neverPlayedBefore(player, opponent)) {
                return opponent;
            }
        }

        return null;
    }

    /**
     * Updates the results table by recalculating scores, updating the table model,
     * and refreshing the UI components.
     */

    public void updateResultsTable() {
        calculateSwissSystemScores(playerList);

        DefaultTableModel tableModel = getDefaultTableModel(playerList);
        resultsTable.setModel(tableModel);

        validate();
        repaint();
    }

    /**
     * Constructs a DefaultTableModel for displaying player standings in a Swiss system tournament.
     * The table is sorted by player points in descending order and structured with columns:
     * "Rang", "Name", "Punkte", "Spiele", "BHZ" (if applicable), "fBHZ" (if applicable), "Sätze", "Bälle".
     *
     * @param allPlayers The list of all players participating in the tournament.
     * @return DefaultTableModel representing the standings table.
     */

    private static DefaultTableModel getDefaultTableModel(List<Player> allPlayers) {
        sortPlayers(allPlayers);
        allPlayers = allPlayers.reversed();

        DefaultTableModel tableModel = new DefaultTableModel();
        tableModel.addColumn("Rang");
        tableModel.addColumn("Name");
        tableModel.addColumn("Punkte");
        tableModel.addColumn("Spiele");
        if(!modus) {
            tableModel.addColumn("BHZ");
            tableModel.addColumn("fBHZ");
        }
        tableModel.addColumn("Sätze");
        tableModel.addColumn("Bälle");

        for (int i = 0; i < allPlayers.size(); i++) {
            Player player = allPlayers.get(i);
            Object[] rowData = getRowData(player, i);

            tableModel.addRow(rowData);
        }

        return tableModel;
    }

    /**
     * Retrieves the row data for a player in the standings table.
     *
     * @param player The player for whom the row data is retrieved.
     * @param i The index of the player in the standings.
     * @return Object[] containing the player's ranking, name with club affiliation,
     *         points, wins:losses, Buchholz, FeinBuchholz (if applicable), sets won:lost,
     *         and balls won:lost (depending on the number of players).
     */

    private static Object[] getRowData(Player player, int i) {
        String winsLosses = player.getWins() + ":" + player.getLosses();
        Object[] rowData;
        if(!modus) {
            rowData = new Object[]{
                    i + 1,
                    player.getFullName() + " (" + player.getClub() + ")",
                    player.getPoints(),
                    winsLosses,
                    player.getBuchholz(),
                    player.getFeinBuchholz(),
                    player.getSetsWon() + ":" + player.getSetsLost(),
                    player.getBallsWon() + ":" + player.getBallsLost()
            };
        } else {
            rowData = new Object[]{
                    i + 1,
                    player.getFullName() + " (" + player.getClub() + ")",
                    player.getPoints(),
                    winsLosses,
                    player.getSetsWon() + ":" + player.getSetsLost(),
                    player.getBallsWon() + ":" + player.getBallsLost()
            };
        }
        return rowData;
    }

    /**
     * Sorts the list of players based on tournament-specific criteria.
     * <p>
     * If modus is everyone against everyone, sorts primarily by points in descending order,
     * followed by Buchholz, FeinBuchholz, sets won:lost ratio, balls won:lost ratio,
     * and TTR (Table Tennis Rating) in ascending order.
     * <p>
     * If modus is everyone against everyone, sorts primarily by points in descending order,
     * followed by sets won:lost ratio, balls won:lost ratio, and TTR in ascending order.
     *
     * @param players The list of players to be sorted.
     */
    private static void sortPlayers(List<Player> players) {
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

    /**
     * Calculates the scores and statistics for each player in the Swiss System tournament.
     * Updates each player's points, wins, losses, sets won, sets lost, balls won, balls lost,
     * Buchholz score, and FeinBuchholz score based on their match results.
     *
     * @param players The list of players for whom scores and statistics are to be calculated.
     */

    private void calculateSwissSystemScores(List<Player> players) {
        players.forEach(player -> {
            player.setPoints(calculatePoints(player));
            player.setWins(calculateWins(player));
            player.setLosses(calculateLosses(player));
            player.setSetsWon(calculateSetsWon(player));
            player.setSetsLost(calculateSetsLost(player));
            player.setBallsWon(calculateBallsWon(player));
            player.setBallsLost(calculateBallsLost(player));
        });

        players.forEach(player -> player.setBuchholz(calculateBuchholz(player)));
        players.forEach(player -> player.setFeinBuchholz(calculateFeinBuchholz(player)));
    }

    /**
     * Calculates the total points scored by the player based on all matches played.
     *
     * @param player The player for whom the points are to be calculated.
     * @return The total points scored by the player.
     */

    private int calculatePoints(Player player) {
        return allMatches.stream()
                .filter(match -> isPlayerInMatch(match, player))
                .mapToInt(match -> calculateMatchPoints(match, player))
                .sum();
    }

    /**
     * Calculates the points scored by the player in a specific match.
     *
     * @param match The match in which the points are to be calculated.
     * @param player The player for whom the points are to be calculated.
     * @return The points scored by the player in the match: 1 if the player wins, 0 otherwise.
     */
    private int calculateMatchPoints(Match match, Player player) {
        if (match.getSecondPlayer() == null) {
            return 1;
        }

        String[] result = match.getOverallResult().split(":");
        if (result.length != 2) {
            return 0;
        }

        int firstPlayerSets = Integer.parseInt(result[0]);
        int secondPlayerSets = Integer.parseInt(result[1]);

        if (match.getFirstPlayer().equals(player) && firstPlayerSets > secondPlayerSets) {
            return 1;
        } else if (match.getSecondPlayer().equals(player) && secondPlayerSets > firstPlayerSets) {
            return 1;
        }
        return 0;
    }

    /**
     * Calculates the number of wins for the player based on all matches played.
     *
     * @param player The player for whom wins are to be calculated.
     * @return The number of wins for the player.
     */
    private int calculateWins(Player player) {
        return (int) allMatches.stream()
                .filter(match -> isPlayerInMatch(match, player))
                .filter(match -> match.getSecondPlayer() == null || didPlayerWinMatch(match, player))
                .count();
    }

    /**
     * Determines if the specified player won the given match based on the match result.
     *
     * @param match The match in which to determine the player's win.
     * @param player The player to check if they won the match.
     * @return true if the player won the match, false otherwise.
     */
    private boolean didPlayerWinMatch(Match match, Player player) {
        String[] result = match.getOverallResult().split(":");
        if (result.length != 2) {
            return false;
        }

        int firstPlayerSets = Integer.parseInt(result[0]);
        int secondPlayerSets = Integer.parseInt(result[1]);

        return (match.getFirstPlayer().equals(player) && firstPlayerSets > secondPlayerSets) ||
                (match.getSecondPlayer().equals(player) && secondPlayerSets > firstPlayerSets);
    }

    /**
     * Calculates the number of losses for the specified player in all matches.
     *
     * @param player The player for whom to calculate losses.
     * @return The number of losses for the player.
     */
    private int calculateLosses(Player player) {
        return (int) allMatches.stream()
                .filter(match -> isPlayerInMatch(match, player))
                .filter(match -> !match.getOverallResult().isEmpty() && !Objects.equals(match.getOverallResult(), ":"))
                .filter(match -> match.getSecondPlayer() != null && !didPlayerWinMatch(match, player))
                .count();
    }

    /**
     * Calculates the total number of sets won by the specified player in all matches.
     *
     * @param player The player for whom to calculate sets won.
     * @return The total number of sets won by the player.
     */
    private int calculateSetsWon(Player player) {
        return allMatches.stream()
                .filter(match -> isPlayerInMatch(match, player))
                .mapToInt(match -> calculateMatchSetsWon(match, player))
                .sum();
    }

    /**
     * Calculates the number of sets won by the specified player in a specific match.
     *
     * @param match The match in which to calculate sets won.
     * @param player The player for whom to calculate sets won.
     * @return The number of sets won by the player in the match.
     */
    private int calculateMatchSetsWon(Match match, Player player) {
        if (match.getFirstPlayer().equals(player) && match.getSecondPlayer() == null) {
            return 3;
        }

        String[] result = match.getOverallResult().split(":");
        if (result.length != 2) {
            return 0;
        }

        return match.getFirstPlayer().equals(player) ? Integer.parseInt(result[0]) : Integer.parseInt(result[1]);
    }

    /**
     * Calculates the number of sets lost by the specified player in all matches.
     *
     * @param player The player for whom to calculate sets lost.
     * @return The total number of sets lost by the player in all matches.
     */
    private int calculateSetsLost(Player player) {
        return allMatches.stream()
                .filter(match -> isPlayerInMatch(match, player))
                .mapToInt(match -> calculateMatchSetsLost(match, player))
                .sum();
    }

    /**
     * Calculates the number of sets lost by the specified player in a particular match.
     *
     * @param match The match to calculate sets lost from.
     * @param player The player for whom to calculate sets lost.
     * @return The number of sets lost by the player in the match.
     */
    private int calculateMatchSetsLost(Match match, Player player) {
        String[] result = match.getOverallResult().split(":");
        if (result.length != 2) {
            return 0;
        }

        return match.getFirstPlayer().equals(player) ? Integer.parseInt(result[1]) : Integer.parseInt(result[0]);
    }

    /**
     * Calculates the total number of balls won by the specified player across all matches.
     *
     * @param player The player for whom to calculate balls won.
     * @return The total number of balls won by the player.
     */
    private int calculateBallsWon(Player player) {
        return allMatches.stream()
                .filter(match -> isPlayerInMatch(match, player))
                .mapToInt(match -> calculateMatchBallsWon(match, player))
                .sum();
    }

    /**
     * Calculates the total number of balls won by the specified player in the given match.
     *
     * @param match  The match in which to calculate balls won.
     * @param player The player for whom to calculate balls won.
     * @return The total number of balls won by the player in the match.
     */
    private int calculateMatchBallsWon(Match match, Player player) {
        if (match.getFirstPlayer().equals(player) && match.getSecondPlayer() == null) {
            return 33;
        }

        String[][] result = match.getResults();
        if (match.getFirstPlayer().equals(player)) {
            return Arrays.stream(result)
                    .filter(sets -> sets[0] != null && !sets[0].isEmpty())
                    .mapToInt(sets -> Integer.parseInt(sets[0]))
                    .sum();
        } else {
            return Arrays.stream(result)
                    .filter(sets -> sets[1] != null && !sets[1].isEmpty())
                    .mapToInt(sets -> Integer.parseInt(sets[1]))
                    .sum();
        }
    }

    /**
     * Calculates the total number of balls lost by the specified player in all matches.
     *
     * @param player The player for whom to calculate balls lost.
     * @return The total number of balls lost by the player.
     */
    private int calculateBallsLost(Player player) {
        return allMatches.stream()
                .filter(match -> isPlayerInMatch(match, player))
                .mapToInt(match -> calculateMatchBallsLost(match, player))
                .sum();
    }

    /**
     * Calculates the total number of balls lost by the specified player in the given match.
     *
     * @param match The match in which to calculate balls lost.
     * @param player The player for whom to calculate balls lost.
     * @return The total number of balls lost by the player in the match.
     */
    private int calculateMatchBallsLost(Match match, Player player) {
        String[][] result = match.getResults();
        if (match.getFirstPlayer().equals(player)) {
            return Arrays.stream(result)
                    .filter(sets -> sets[1] != null && !sets[1].isEmpty())
                    .mapToInt(sets -> Integer.parseInt(sets[1]))
                    .sum();
        } else {
            return Arrays.stream(result)
                    .filter(sets -> sets[0] != null && !sets[0].isEmpty())
                    .mapToInt(sets -> Integer.parseInt(sets[0]))
                    .sum();
        }
    }

    /**
     * Calculates the Buchholz score for the specified player.
     *
     * @param player The player for whom to calculate the Buchholz score.
     * @return The Buchholz score of the player.
     */
    private int calculateBuchholz(Player player) {
        return allMatches.stream()
                .filter(match -> isPlayerInMatch(match, player) && match.getSecondPlayer() != null)
                .mapToInt(match -> getOpponentPoints(match, player))
                .sum();
    }

    /**
     * Calculates the FeinBuchholz score for the specified player.
     *
     * @param player The player for whom to calculate the FeinBuchholz score.
     * @return The FeinBuchholz score of the player.
     */
    private int calculateFeinBuchholz(Player player) {
        return allMatches.stream()
                .filter(match -> isPlayerInMatch(match, player) && match.getSecondPlayer() != null)
                .mapToInt(match -> getOpponentBuchholz(match, player))
                .sum();
    }

    /**
     * Checks if the given player is participating in the specified match.
     *
     * @param match The match to check.
     * @param player The player to check against the match.
     * @return {@code true} if the player is in the match, {@code false} otherwise.
     */
    private boolean isPlayerInMatch(Match match, Player player) {
        return match.getFirstPlayer().equals(player) || (match.getSecondPlayer() != null && match.getSecondPlayer().equals(player));
    }

    /**
     * Retrieves the points of the opponent player in the specified match.
     *
     * @param match The match to retrieve opponent points from.
     * @param player The player whose opponent's points are to be retrieved.
     * @return The points of the opponent player in the match.
     */
    private int getOpponentPoints(Match match, Player player) {
        return match.getFirstPlayer().equals(player) ? match.getSecondPlayer().getPoints() : match.getFirstPlayer().getPoints();
    }

    /**
     * Retrieves the Buchholz score of the opponent player in the specified match.
     *
     * @param match The match to retrieve opponent Buchholz score from.
     * @param player The player whose opponent's Buchholz score is to be retrieved.
     * @return The Buchholz score of the opponent player in the match.
     */
    private int getOpponentBuchholz(Match match, Player player) {
        return match.getFirstPlayer().equals(player) ? match.getSecondPlayer().getBuchholz() : match.getFirstPlayer().getBuchholz();
    }

    /**
     * Displays the referee sheets for all matches without bye players.
     */
    private void previewRefereeSheets() {
        List<Match> matchesWithoutBye = matches.stream()
                .filter(match -> match.getSecondPlayer() != null)
                .collect(Collectors.toList());
        new RefereeSheetsController(matchesWithoutBye);
    }

    /**
     * Starts the next round of the tournament if all matches of the current round are completed.
     * Displays a warning message if there are unfinished matches.
     */
    public void startNextRound() {
        if (matches.stream().anyMatch(match -> (match.getSecondPlayer() != null && (match.getOverallResult().isEmpty() || match.getOverallResult().equals(":"))))) {
            JOptionPane.showMessageDialog(this, "Es gibt noch unbeendete Begegnungen. Bitte alle Ergebnisse erfassen, bevor die nächste Runde gestartet werden kann.", "Unbeendete Begegnungen", JOptionPane.WARNING_MESSAGE);
            return;
        }

        currentRound++;
        currentRoundLabel.setText("Runde " + currentRound);
        matches.clear();
        pairingsTextArea.setText("");
        generatePairings(new HashSet<>());
        updateResultsTable();
    }

    /**
     * Prints the placement table using the default printer.
     * Displays a print dialog for user confirmation before printing.
     */
    private void printPlacementTable() {
        PrinterJob job = PrinterJob.getPrinterJob();
        job.setJobName("Platzierungstabelle");

        job.setPrintable((graphics, pageFormat, pageIndex) -> {
            if (pageIndex > 0) {
                return Printable.NO_SUCH_PAGE;
            }

            Graphics2D g2d = (Graphics2D) graphics;
            g2d.translate(pageFormat.getImageableX(), pageFormat.getImageableY());

            resultsTable.print(g2d);

            return Printable.PAGE_EXISTS;
        });

        boolean doPrint = job.printDialog();
        if (doPrint) {
            try {
                job.print();
            } catch (PrinterException ignored) {
            }
        }
    }

    /**
     * Checks if two players have never played against each other in any previous match.
     *
     * @param player1 The first player to check.
     * @param player2 The second player to check.
     * @return {@code true} if the players have never played against each other before, {@code false} otherwise.
     */
    private boolean neverPlayedBefore(Player player1, Player player2) {
        return allMatches.stream()
                .noneMatch(match -> (match.getFirstPlayer().equals(player1) && match.getSecondPlayer() != null && match.getSecondPlayer().equals(player2))
                        || (match.getFirstPlayer().equals(player2) && match.getSecondPlayer() != null && match.getSecondPlayer().equals(player1)));
    }

    public int getCurrentRound() {
        return this.currentRound;
    }

    public boolean isFinished() {
        return this.finished;
    }

    public List<Match> getMatches() {
        return this.matches;
    }

    public List<Player> getPlayerList() {
        return this.playerList;
    }

    /**
     * Sets new matches in the application, updating the match lists and displaying them in the text area.
     *
     * @param selectedMatches The list of matches to set as new matches.
     */
    public void setNewMatches(List<Match> selectedMatches) {
        allMatches.removeAll(matches);
        matches.clear();
        pairingsTextArea.setText("");

        List<Match> byeMatch = new ArrayList<>();
        List<Match> normalMatches = new ArrayList<>();
        for (Match match : selectedMatches) {
            if (match.getSecondPlayer() == null) {
                byeMatch.add(match);
            } else {
                normalMatches.add(match);
            }
        }

        AtomicReference<List<Integer>> availableTablesReference = new AtomicReference<>(IntStream.rangeClosed(1, tableNumber).boxed().collect(Collectors.toList()));

        normalMatches.forEach(match -> {
            if (availableTablesReference.get().isEmpty()) {
                availableTablesReference.set(IntStream.rangeClosed(1, tableNumber).boxed().collect(Collectors.toList()));
            }
            match.setTable(availableTablesReference.get().removeFirst());
        });

        matches.addAll(normalMatches);
        allMatches.addAll(normalMatches);

        matches.addAll(byeMatch);
        allMatches.addAll(byeMatch);

        appendMatchesToTextArea(normalMatches);
        appendByeMatchesToTextArea(byeMatch);

        updateResultsTable();
    }

    /**
     * Appends information about normal matches to the pairingsTextArea.
     *
     * @param matches The list of normal matches to append.
     */
    private void appendMatchesToTextArea(List<Match> matches) {
        matches.forEach(match -> {
            String player1Info = match.getFirstPlayer().getFullName() + " (" + match.getFirstPlayer().getClub() + ")";
            String player2Info = match.getSecondPlayer().getFullName() + " (" + match.getSecondPlayer().getClub() + ")";
            String tableInfo = " - Tisch " + match.getTableNumber();
            pairingsTextArea.append(player1Info + " vs. " + player2Info + tableInfo + "\n");
        });
    }

    /**
     * Appends information about freilos matches to the pairingsTextArea.
     *
     * @param freilosMatches The list of freilos matches to append.
     */
    private void appendByeMatchesToTextArea(List<Match> freilosMatches) {
        freilosMatches.forEach(match -> {
            String playerInfo = match.getFirstPlayer().getFullName() + " - Freilos";
            pairingsTextArea.append(playerInfo + "\n");
        });
    }

}
