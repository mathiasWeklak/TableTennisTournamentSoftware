package controller;

import model.Match;
import model.Player;
import model.TournamentMode;
import model.TournamentState;
import view.TournamentRoundView;
import view.UITheme;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.print.PrinterException;
import java.io.*;
import java.util.*;

/**
 * Controller for a tournament round. Delegates all pairing logic to {@link PairingEngine},
 * score calculation to {@link ScoreCalculator}, and all UI rendering to {@link TournamentRoundView}.
 */
public class TournamentRound {

    private final List<Player> playerList;
    private final int tableNumber;
    private final TournamentMode mode;
    private final String tournamentName;

    private final PairingEngine pairingEngine;
    private final ScoreCalculator scoreCalculator;
    private final TournamentRoundView view;

    private int currentRound;

    /**
     * Constructs a new TournamentRound, initializes the view, and generates the first round's pairings.
     *
     * @param playerList     the list of players participating in the tournament
     * @param tournamentName the name of the tournament
     * @param tableNumber    the number of available tables
     * @param mode           the tournament mode (SWISS or ROUND_ROBIN)
     */
    public TournamentRound(List<Player> playerList, String tournamentName, int tableNumber, TournamentMode mode) {
        this(playerList, tournamentName, tableNumber, mode, false);
    }

    private TournamentRound(List<Player> playerList, String tournamentName, int tableNumber, TournamentMode mode,
                             boolean skipInitialPairing) {
        this.playerList = new ArrayList<>(playerList);
        this.tableNumber = tableNumber;
        this.mode = mode;
        this.tournamentName = tournamentName;
        this.currentRound = 1;

        this.pairingEngine = new PairingEngine(this.playerList, tableNumber, mode);
        this.scoreCalculator = new ScoreCalculator(pairingEngine.getAllMatches());

        this.view = new TournamentRoundView(tournamentName, mode);

        view.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (JOptionPane.showConfirmDialog(view,
                        "Wollen Sie das Turnier wirklich beenden?", "Bestätigung",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.QUESTION_MESSAGE) == JOptionPane.YES_OPTION) {
                    view.dispose();
                }
            }
        });

        view.getPreviewRefereeSheetsButton().addActionListener(_ -> previewRefereeSheets());

        view.getResultEntryButton().addActionListener(_ -> {
            new ResultEntryController(pairingEngine.getMatches(), this);
            updateResultsTable();
        });

        view.getPrintTableButton().addActionListener(_ -> printPlacementTable());
        view.getNextRoundButton().addActionListener(_ -> startNextRound());

        if (view.getManipulateButton() != null) {
            view.getManipulateButton().addActionListener(_ -> {
                boolean resultsEntered = pairingEngine.getMatches().stream()
                        .anyMatch(match -> match.getSecondPlayer() != null && !match.getOverallResult().isEmpty());

                if (!resultsEntered) {
                    List<Match> allPossibleOpenMatches = pairingEngine.calculateAllPossibleOpenMatches();
                    allPossibleOpenMatches.sort(Comparator.comparing(match -> match.getFirstPlayer().getFullName()));
                    Set<Match> uniqueMatches = new LinkedHashSet<>(allPossibleOpenMatches);
                    new MatchManagerController(uniqueMatches, playerList.size(), this).getView().setVisible(true);
                } else {
                    JOptionPane.showMessageDialog(view,
                            "Setzung kann nicht manipuliert werden, da bereits Ergebnisse eingetragen wurden.",
                            "Ergebnisse vorhanden", JOptionPane.WARNING_MESSAGE);
                }
            });
        }

        if (!skipInitialPairing) {
            String pairingsText = pairingEngine.generatePairings(currentRound);
            if (pairingsText != null) {
                view.getPairingsTextArea().setText(pairingsText);
            } else {
                JOptionPane.showMessageDialog(view, "Es wurden bereits alle möglichen Kombinationen gespielt.",
                        "Keine Paarungen mehr möglich", JOptionPane.INFORMATION_MESSAGE);
            }
        }
        updateResultsTable();
    }

    /**
     * Makes the tournament round window visible or invisible.
     *
     * @param visible {@code true} to show the window
     */
    public void setVisible(boolean visible) {
        view.setVisible(visible);
    }

    /**
     * Recalculates all player statistics via {@link ScoreCalculator} and refreshes
     * the standings table in the UI to reflect the latest results.
     */
    public void updateResultsTable() {
        scoreCalculator.calculate(playerList);

        DefaultTableModel tableModel = getDefaultTableModel(playerList);
        view.getResultsTable().setModel(tableModel);
        UITheme.applyTableStyling(view.getResultsTable());
        UITheme.setNameColumnWide(view.getResultsTable(), 1);

        view.validate();
        view.repaint();
    }

    /**
     * Builds the {@link DefaultTableModel} for the standings table.
     * Columns differ between Swiss System (includes BHZ and fBHZ) and Round Robin mode.
     *
     * @param allPlayers the players to include, sorted and ranked before adding to the model
     * @return the populated table model
     */
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
        if (mode == TournamentMode.SWISS) {
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

    /**
     * Builds the data array for a single row in the standings table.
     *
     * @param player the player for this row
     * @param i      the zero-based rank index (displayed as {@code i + 1})
     * @return the row data array
     */
    private Object[] getRowData(Player player, int i) {
        String winsLosses = player.getWins() + ":" + player.getLosses();
        if (mode == TournamentMode.SWISS) {
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

    /**
     * Sorts the player list in-place using the ranking criteria appropriate for the current mode.
     *
     * @param players the list of players to sort
     */
    private void sortPlayers(List<Player> players) {
        PairingEngine.sortPlayersByRanking(players, mode);
    }

    /**
     * Opens the referee sheet preview window for all non-bye matches in the current round.
     */
    private void previewRefereeSheets() {
        List<Match> matchesWithoutBye = pairingEngine.getMatches().stream()
                .filter(match -> match.getSecondPlayer() != null)
                .toList();
        new RefereeSheetsController(matchesWithoutBye);
    }

    /**
     * Advances to the next round. Validates that all current-round results have been entered,
     * increments the round counter, clears the current pairings, and generates new ones.
     * Saves the tournament state after a successful transition.
     * Shows a warning dialog if any results are still missing.
     */
    public void startNextRound() {
        boolean unfinished = pairingEngine.getMatches().stream()
                .anyMatch(match -> match.getSecondPlayer() != null
                        && (match.getOverallResult().isEmpty() || match.getOverallResult().equals(":")));
        if (unfinished) {
            JOptionPane.showMessageDialog(view,
                    "Es gibt noch unbeendete Begegnungen. Bitte alle Ergebnisse erfassen, bevor die nächste Runde gestartet werden kann.",
                    "Unbeendete Begegnungen", JOptionPane.WARNING_MESSAGE);
            return;
        }

        pairingEngine.clearCurrentRound();
        String pairingsText = pairingEngine.generatePairings(currentRound + 1);

        if (pairingsText != null) {
            currentRound++;
            view.getCurrentRoundLabel().setText("Runde " + currentRound);
            view.getPairingsTextArea().setText(pairingsText);
        } else {
            view.getPairingsTextArea().setText("");
        }

        saveTournamentState();

        if (pairingsText == null) {
            JOptionPane.showMessageDialog(view, "Es wurden bereits alle möglichen Kombinationen gespielt.",
                    "Keine Paarungen mehr möglich", JOptionPane.INFORMATION_MESSAGE);
        }

        updateResultsTable();
    }

    /**
     * Replaces the current round's pairings with the provided match selection and updates the UI.
     * Delegates to {@link PairingEngine#setNewMatches(List)} for state management.
     *
     * @param selectedMatches the manually selected matches to use for the current round
     */
    public void setNewMatches(List<Match> selectedMatches) {
        String text = pairingEngine.setNewMatches(selectedMatches);
        view.getPairingsTextArea().setText(text);
        updateResultsTable();
    }

    /**
     * Sends the current standings table to the printer using the system print dialog.
     * Shows an error dialog if printing fails.
     */
    private void printPlacementTable() {
        try {
            view.getResultsTable().print(
                JTable.PrintMode.FIT_WIDTH,
                new java.text.MessageFormat("Turnier: " + tournamentName + "  —  Runde " + currentRound),
                new java.text.MessageFormat("Seite {0}"),
                true,
                null,
                true
            );
        } catch (PrinterException e) {
            JOptionPane.showMessageDialog(view, "Fehler beim Drucken.", "Druckfehler", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Serializes the current tournament state to a {@code .ser} file in the user's Documents
     * folder. The file name is derived from the tournament name and the current round number.
     * Shows an error dialog if the file cannot be written.
     */
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
                mode
        );

        try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(file))) {
            out.writeObject(state);
        } catch (IOException _) {
            JOptionPane.showMessageDialog(view, "Fehler beim Speichern.", "Fehler", JOptionPane.ERROR_MESSAGE);
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
                state.mode(),
                true
        );
        round.currentRound = state.currentRound();
        round.view.getCurrentRoundLabel().setText("Runde " + state.currentRound());
        round.pairingEngine.restoreState(state.allMatches(), state.matches());
        round.pairingEngine.setFinished(state.finished());
        round.view.getPairingsTextArea().setText(round.pairingEngine.formatMatchesAsText(state.matches()));
        round.updateResultsTable();
        return round;
    }

    /**
     * Returns the matches scheduled for the current round.
     *
     * @return the current round's match list
     */
    public List<Match> getMatches() {
        return pairingEngine.getMatches();
    }
}
