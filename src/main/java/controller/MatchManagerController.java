package controller;

import model.Match;
import model.Player;
import view.MatchManagerView;

import javax.swing.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Controller class for managing matches in a tournament.
 * Handles the interaction between the view and the model for selecting and committing matches.
 */
public class MatchManagerController {
    private final DefaultListModel<Match> possibleMatchesModel;
    private final DefaultListModel<Match> selectedMatchesModel;
    private final List<Match> allPossibleMatches;
    private final int playerCount;
    private final TournamentRound tournamentRound;
    private Match lastSelectedPossibleMatch = null;
    final MatchManagerView view;

    /**
     * Constructs a MatchManagerController.
     *
     * @param matches Set of all possible matches.
     * @param playerCount Number of players in the tournament.
     * @param tournamentRound The current tournament round.
     */
    public MatchManagerController(Set<Match> matches, int playerCount, TournamentRound tournamentRound) {
        this.playerCount = playerCount;
        this.tournamentRound = tournamentRound;

        allPossibleMatches = new ArrayList<>(matches);

        possibleMatchesModel = new DefaultListModel<>();
        selectedMatchesModel = new DefaultListModel<>();

        for (Match match : matches) {
            possibleMatchesModel.addElement(match);
        }

        view = new MatchManagerView(possibleMatchesModel, selectedMatchesModel, playerCount);
        addListeners();
    }

    /**
     * Adds event listeners to the view components.
     */
    private void addListeners() {
        view.getPossibleMatchesList().addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    handlePossibleMatchSelection();
                }
            }
        });

        view.getSelectedMatchesList().addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    moveMatchToPossible();
                }
            }
        });

        view.getDownButton().addActionListener(e -> moveMatchToSelected());

        view.getUpButton().addActionListener(e -> moveMatchToPossible());

        view.getCommitButton().addActionListener(e -> commitSelectedMatches());
    }

    /**
     * Handles the selection of a possible match.
     * If the same match is selected twice consecutively, it is moved to the selected matches list.
     */
    private void handlePossibleMatchSelection() {
        int index = view.getPossibleMatchesList().getSelectedIndex();
        if (index != -1) {
            Match selectedMatch = possibleMatchesModel.getElementAt(index);
            if (selectedMatch.equals(lastSelectedPossibleMatch)) {
                moveMatchToSelected();
                lastSelectedPossibleMatch = null;
            } else {
                lastSelectedPossibleMatch = selectedMatch;
            }
        }
    }

    /**
     * Moves the selected match from the possible matches list to the selected matches list.
     */
    private void moveMatchToSelected() {
        int index = view.getPossibleMatchesList().getSelectedIndex();
        if (index != -1) {
            Match selectedMatch = possibleMatchesModel.getElementAt(index);
            possibleMatchesModel.remove(index);
            selectedMatchesModel.addElement(selectedMatch);
            addSelectedPlayers(selectedMatchesModel, possibleMatchesModel, allPossibleMatches);
        }
    }

    /**
     * Moves the selected match from the selected matches list to the possible matches list.
     */
    private void moveMatchToPossible() {
        int index = view.getSelectedMatchesList().getSelectedIndex();
        if (index != -1) {
            Match selectedMatch = selectedMatchesModel.getElementAt(index);
            selectedMatchesModel.remove(index);
            possibleMatchesModel.addElement(selectedMatch);
            addSelectedPlayers(selectedMatchesModel, possibleMatchesModel, allPossibleMatches);
        }
    }

    /**
     * Updates the possible matches list by removing matches that contain players already selected.
     * Also ensures that no duplicate players are selected and handles the case of a bye (freilos).
     *
     * @param selectedMatchesModel The model containing the selected matches.
     * @param possibleMatchesModel The model containing the possible matches.
     * @param allPossibleMatches List of all possible matches.
     */
    static void addSelectedPlayers(DefaultListModel<Match> selectedMatchesModel, DefaultListModel<Match> possibleMatchesModel, List<Match> allPossibleMatches) {
        List<Player> selectedPlayers = new ArrayList<>();
        boolean freilosSelected = false;
        for (int i = 0; i < selectedMatchesModel.getSize(); i++) {
            Match match = selectedMatchesModel.getElementAt(i);
            selectedPlayers.add(match.getFirstPlayer());
            if (match.getSecondPlayer() != null) {
                selectedPlayers.add(match.getSecondPlayer());
            } else {
                freilosSelected = true;
            }
        }

        possibleMatchesModel.clear();
        for (Match match : allPossibleMatches) {
            if ((!selectedPlayers.contains(match.getFirstPlayer()) &&
                    (match.getSecondPlayer() == null || !selectedPlayers.contains(match.getSecondPlayer()))) &&
                    (!freilosSelected || match.getSecondPlayer() != null)) {
                possibleMatchesModel.addElement(match);
            }
        }
    }

    /**
     * Commits the selected matches to the tournament round.
     * Validates the number of selected matches and ensures correct handling of a bye if needed.
     */
    private void commitSelectedMatches() {
        int neededMatches = playerCount / 2 + (playerCount % 2);
        if (selectedMatchesModel.getSize() != neededMatches) {
            JOptionPane.showMessageDialog(view, "Die Anzahl der ausgewählten Begegnungen stimmt nicht mit der benötigten Anzahl überein.");
            return;
        }

        boolean freilosSelected = false;
        for (int i = 0; i < selectedMatchesModel.getSize(); i++) {
            if (selectedMatchesModel.getElementAt(i).getSecondPlayer() == null) {
                freilosSelected = true;
                break;
            }
        }

        if (playerCount % 2 != 0 && !freilosSelected) {
            JOptionPane.showMessageDialog(view, "Bei ungerader Spieleranzahl muss ein Freilos-Spiel ausgewählt sein.");
            return;
        }

        List<Match> selectedMatches = new ArrayList<>();
        for (int i = 0; i < selectedMatchesModel.getSize(); i++) {
            selectedMatches.add(selectedMatchesModel.getElementAt(i));
        }

        tournamentRound.setNewMatches(selectedMatches);
        JOptionPane.showMessageDialog(view, "Ausgewählte Begegnungen wurden als neue Setzung übernommen.");
        view.dispose();
    }
}
