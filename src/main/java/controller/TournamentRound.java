package controller;

import model.Match;
import model.Player;
import model.TournamentState;

import view.UITheme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.print.PrinterException;
import java.io.*;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

/**
 * UI controller for a tournament round. Delegates all pairing logic to {@link PairingEngine}
 * and score calculation to {@link ScoreCalculator}.
 */
public class TournamentRound extends JFrame {

    private final List<Player> playerList;
    private final int tableNumber;
    private final boolean modus;
    private final String tournamentName;

    private final PairingEngine pairingEngine;
    private final ScoreCalculator scoreCalculator;

    private final JTable resultsTable;
    private final JTextArea pairingsTextArea;
    private int currentRound;
    private JLabel currentRoundLabel;

    /**
     * Constructs a new TournamentRound, sets up the UI, and generates the first round's pairings.
     *
     * @param playerList     the list of players participating in the tournament
     * @param tournamentName the name of the tournament
     * @param tableNumber    the number of available tables
     * @param modus          {@code true} for Round Robin, {@code false} for Swiss System
     */
    public TournamentRound(List<Player> playerList, String tournamentName, int tableNumber, boolean modus) {
        this.playerList = new ArrayList<>(playerList);
        this.tableNumber = tableNumber;
        this.modus = modus;
        this.tournamentName = tournamentName;
        this.currentRound = 1;
        this.pairingsTextArea = new JTextArea(11, 20);

        this.pairingEngine = new PairingEngine(this.playerList, tableNumber, modus);
        this.scoreCalculator = new ScoreCalculator(pairingEngine.getAllMatches());

        setTitle("Turnierrunde");
        setSize(800, 680);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
        getContentPane().setBackground(UITheme.BACKGROUND);

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

        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(UITheme.PRIMARY);
        headerPanel.setBorder(new EmptyBorder(10, 20, 10, 20));

        JLabel titleLabel = new JLabel("Turnier: " + this.tournamentName);
        titleLabel.setFont(UITheme.FONT_TITLE);
        titleLabel.setForeground(Color.WHITE);

        currentRoundLabel = new JLabel("Runde " + currentRound);
        currentRoundLabel.setFont(UITheme.FONT_SUBTITLE);
        currentRoundLabel.setForeground(new Color(200, 220, 255));

        JPanel headerTextPanel = new JPanel();
        headerTextPanel.setLayout(new BoxLayout(headerTextPanel, BoxLayout.Y_AXIS));
        headerTextPanel.setBackground(UITheme.PRIMARY);
        headerTextPanel.add(titleLabel);
        headerTextPanel.add(Box.createVerticalStrut(2));
        headerTextPanel.add(currentRoundLabel);
        headerPanel.add(headerTextPanel, BorderLayout.WEST);
        add(headerPanel, BorderLayout.NORTH);

        JPanel centerCard = new JPanel(new BorderLayout(0, 0));
        centerCard.setBackground(UITheme.BACKGROUND);
        centerCard.setBorder(new EmptyBorder(10, 12, 4, 12));

        JPanel pairingsInner = new JPanel(new BorderLayout());
        pairingsInner.setBackground(UITheme.SURFACE);
        pairingsInner.setBorder(UITheme.cardBorder("Aktuelle Begegnungen"));
        pairingsTextArea.setEditable(false);
        pairingsTextArea.setFont(UITheme.FONT_MONO);
        pairingsTextArea.setMargin(new Insets(8, 10, 8, 10));
        pairingsTextArea.setBackground(UITheme.SURFACE);
        pairingsInner.add(new JScrollPane(pairingsTextArea), BorderLayout.CENTER);
        centerCard.add(pairingsInner, BorderLayout.CENTER);
        add(centerCard, BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.setBackground(UITheme.BACKGROUND);
        bottomPanel.add(getButtonsPanel(), BorderLayout.NORTH);

        resultsTable = new JTable();
        UITheme.applyTableStyling(resultsTable);
        JScrollPane tableScrollPane = new JScrollPane(resultsTable);
        tableScrollPane.setPreferredSize(new Dimension(500, 220));
        tableScrollPane.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, UITheme.BORDER_COLOR));

        JPanel tableCard = new JPanel(new BorderLayout(0, 0));
        tableCard.setBackground(UITheme.BACKGROUND);
        tableCard.setBorder(new EmptyBorder(4, 12, 4, 12));

        JPanel tableInner = new JPanel(new BorderLayout());
        tableInner.setBackground(UITheme.SURFACE);
        tableInner.setBorder(UITheme.cardBorder("Rangliste"));
        tableInner.add(resultsTable.getTableHeader(), BorderLayout.NORTH);
        tableInner.add(tableScrollPane, BorderLayout.CENTER);
        tableCard.add(tableInner, BorderLayout.CENTER);
        bottomPanel.add(tableCard, BorderLayout.CENTER);

        JPanel bottomButtonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
        bottomButtonPanel.setBackground(UITheme.BACKGROUND);
        JButton printTableButton = UITheme.createSecondaryButton("Tabelle drucken");
        printTableButton.addActionListener(e -> printPlacementTable());
        bottomButtonPanel.add(printTableButton);
        JButton nextRoundButton = UITheme.createPrimaryButton("Nächste Runde auslosen und starten");
        nextRoundButton.addActionListener(e -> startNextRound());
        bottomButtonPanel.add(nextRoundButton);

        bottomPanel.add(bottomButtonPanel, BorderLayout.SOUTH);
        add(bottomPanel, BorderLayout.SOUTH);

        String pairingsText = pairingEngine.generatePairings(new HashSet<>(), currentRound);
        if (pairingsText != null) {
            pairingsTextArea.setText(pairingsText);
        } else {
            JOptionPane.showMessageDialog(this, "Es wurden bereits alle möglichen Kombinationen gespielt.",
                    "Keine Paarungen mehr möglich", JOptionPane.INFORMATION_MESSAGE);
        }
        updateResultsTable();
    }

    private JPanel getButtonsPanel() {
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 8));
        bottomPanel.setBackground(UITheme.BACKGROUND);

        JButton previewRefereeSheetsButton = UITheme.createSecondaryButton("Schiedsrichterzettel anzeigen");
        previewRefereeSheetsButton.addActionListener(e -> previewRefereeSheets());
        bottomPanel.add(previewRefereeSheetsButton);

        JButton resultEntryButton = UITheme.createPrimaryButton("Ergebnisse erfassen");
        resultEntryButton.addActionListener(e -> {
            new ResultEntryController(pairingEngine.getMatches(), this);
            updateResultsTable();
        });
        bottomPanel.add(resultEntryButton);

        if (!modus) {
            bottomPanel.add(getManipulateButton());
        }
        return bottomPanel;
    }

    private JButton getManipulateButton() {
        JButton manipulateSettingButton = UITheme.createSecondaryButton("Setzung manipulieren");
        manipulateSettingButton.addActionListener(e -> {
            boolean resultsEntered = pairingEngine.getMatches().stream()
                    .anyMatch(match -> match.getSecondPlayer() != null && !match.getOverallResult().isEmpty());

            if (!resultsEntered) {
                List<Match> allPossibleOpenMatches = pairingEngine.calculateAllPossibleOpenMatches();
                allPossibleOpenMatches.sort(Comparator.comparing(match -> match.getFirstPlayer().getFullName()));
                Set<Match> uniqueMatches = new HashSet<>(allPossibleOpenMatches);
                new MatchManagerController(uniqueMatches, playerList.size(), this).view.setVisible(true);
            } else {
                JOptionPane.showMessageDialog(this,
                        "Setzung kann nicht manipuliert werden, da bereits Ergebnisse eingetragen wurden.",
                        "Ergebnisse vorhanden", JOptionPane.WARNING_MESSAGE);
            }
        });
        return manipulateSettingButton;
    }

    public void updateResultsTable() {
        scoreCalculator.calculate(playerList);

        DefaultTableModel tableModel = getDefaultTableModel(playerList);
        resultsTable.setModel(tableModel);
        UITheme.applyTableStyling(resultsTable);
        UITheme.setNameColumnWide(resultsTable, 1);

        validate();
        repaint();
    }

    private DefaultTableModel getDefaultTableModel(List<Player> allPlayers) {
        List<Player> sorted = new ArrayList<>(allPlayers);
        sortPlayers(sorted);
        sorted = sorted.reversed();

        DefaultTableModel tableModel = new DefaultTableModel() {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        tableModel.addColumn("Rang");
        tableModel.addColumn("Name");
        tableModel.addColumn("Punkte");
        tableModel.addColumn("Spiele");
        if (!modus) {
            tableModel.addColumn("BHZ");
            tableModel.addColumn("fBHZ");
        }
        tableModel.addColumn("Sätze");
        tableModel.addColumn("Bälle");

        for (int i = 0; i < sorted.size(); i++) {
            tableModel.addRow(getRowData(sorted.get(i), i));
        }

        return tableModel;
    }

    private Object[] getRowData(Player player, int i) {
        String winsLosses = player.getWins() + ":" + player.getLosses();
        if (!modus) {
            return new Object[]{
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
            return new Object[]{
                    i + 1,
                    player.getFullName() + " (" + player.getClub() + ")",
                    player.getPoints(),
                    winsLosses,
                    player.getSetsWon() + ":" + player.getSetsLost(),
                    player.getBallsWon() + ":" + player.getBallsLost()
            };
        }
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

    private void previewRefereeSheets() {
        List<Match> matchesWithoutBye = pairingEngine.getMatches().stream()
                .filter(match -> match.getSecondPlayer() != null)
                .collect(Collectors.toList());
        new RefereeSheetsController(matchesWithoutBye);
    }

    public void startNextRound() {
        boolean unfinished = pairingEngine.getMatches().stream()
                .anyMatch(match -> match.getSecondPlayer() != null
                        && (match.getOverallResult().isEmpty() || match.getOverallResult().equals(":")));
        if (unfinished) {
            JOptionPane.showMessageDialog(this,
                    "Es gibt noch unbeendete Begegnungen. Bitte alle Ergebnisse erfassen, bevor die nächste Runde gestartet werden kann.",
                    "Unbeendete Begegnungen", JOptionPane.WARNING_MESSAGE);
            return;
        }

        currentRound++;
        currentRoundLabel.setText("Runde " + currentRound);
        pairingEngine.clearCurrentRound();
        pairingsTextArea.setText("");

        String pairingsText = pairingEngine.generatePairings(new HashSet<>(), currentRound);
        if (pairingsText != null) {
            pairingsTextArea.setText(pairingsText);
        } else {
            JOptionPane.showMessageDialog(this, "Es wurden bereits alle möglichen Kombinationen gespielt.",
                    "Keine Paarungen mehr möglich", JOptionPane.INFORMATION_MESSAGE);
        }

        saveTournamentState();
        updateResultsTable();
    }

    public void setNewMatches(List<Match> selectedMatches) {
        String text = pairingEngine.setNewMatches(selectedMatches);
        pairingsTextArea.setText(text);
        updateResultsTable();
    }

    private void printPlacementTable() {
        try {
            resultsTable.print(
                JTable.PrintMode.FIT_WIDTH,
                new java.text.MessageFormat("Turnier: " + tournamentName + "  —  Runde " + currentRound),
                new java.text.MessageFormat("Seite {0}"),
                true,
                null,
                true
            );
        } catch (PrinterException e) {
            JOptionPane.showMessageDialog(this, "Fehler beim Drucken.", "Druckfehler", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void saveTournamentState() {
        String sanitizedName = tournamentName.replaceAll("[^a-zA-Z0-9-_.]", "_");
        String fileName = sanitizedName + "_Runde_" + currentRound + ".ser";
        File file = new File(new File(System.getProperty("user.home"), "Documents"), fileName);

        TournamentState state = new TournamentState(
                playerList,
                pairingEngine.getAllMatches(),
                new ArrayList<>(pairingEngine.getMatches()),
                currentRound,
                pairingEngine.isFinished(),
                tournamentName,
                tableNumber,
                modus
        );

        try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(file))) {
            out.writeObject(state);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Fehler beim Speichern.", "Fehler", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Reconstructs a {@link TournamentRound} from a previously saved {@link TournamentState}.
     * Uses {@link PairingEngine#restoreState} to replace any freshly-generated pairings with
     * the saved match data, preventing duplicate entries in the match history.
     */
    public static TournamentRound fromSavedState(TournamentState state) {
        TournamentRound round = new TournamentRound(
                new ArrayList<>(state.playerList()),
                state.tournamentName(),
                state.tableCount(),
                state.modus()
        );
        round.currentRound = state.currentRound();
        round.currentRoundLabel.setText("Runde " + state.currentRound());
        round.pairingEngine.restoreState(state.allMatches(), state.matches());
        round.pairingEngine.setFinished(state.finished());
        round.updateResultsTable();
        return round;
    }

    public int getCurrentRound() {
        return this.currentRound;
    }

    public boolean isFinished() {
        return pairingEngine.isFinished();
    }

    public List<Match> getMatches() {
        return pairingEngine.getMatches();
    }

    public List<Player> getPlayerList() {
        return this.playerList;
    }
}
