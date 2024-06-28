package controller;

import model.Match;
import model.Player;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * JFrame-based GUI for managing match selections in a tournament round.
 */
public class MatchManager extends JFrame {
    private final DefaultListModel<Match> possibleMatchesModel;
    private final DefaultListModel<Match> selectedMatchesModel;
    private final JList<Match> possibleMatchesList;
    private final JList<Match> selectedMatchesList;
    private final List<Match> allPossibleMatches;
    private final int playerCount;
    private final TournamentRound tournamentRound;
    private Match lastSelectedPossibleMatch = null;

    /**
     * Constructs a MatchManager with initial set of matches, player count, and parent TournamentRound.
     *
     * @param matches        Initial set of matches.
     * @param playerCount    Number of players in the tournament round.
     * @param tournamentRound The parent TournamentRound instance.
     */
    public MatchManager(Set<Match> matches, int playerCount, TournamentRound tournamentRound) {
        this.playerCount = playerCount;
        this.tournamentRound = tournamentRound;

        setTitle("Begegnungs Manager");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(600, 700);
        setLayout(new BorderLayout());

        allPossibleMatches = new ArrayList<>(matches);

        possibleMatchesModel = new DefaultListModel<>();
        selectedMatchesModel = new DefaultListModel<>();

        for (Match match : matches) {
            possibleMatchesModel.addElement(match);
        }

        possibleMatchesList = new JList<>(possibleMatchesModel);
        selectedMatchesList = new JList<>(selectedMatchesModel);

        possibleMatchesList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        selectedMatchesList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        possibleMatchesList.setCellRenderer(new MatchRenderer());
        selectedMatchesList.setCellRenderer(new MatchRenderer());

        possibleMatchesList.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    handlePossibleMatchSelection();
                }
            }
        });

        selectedMatchesList.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    moveMatchToPossible();
                }
            }
        });

        JButton downButton = new JButton("↓");
        downButton.addActionListener(e -> moveMatchToSelected());

        JButton upButton = new JButton("↑");
        upButton.addActionListener(e -> moveMatchToPossible());

        JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new GridLayout(1, 2));
        buttonPanel.add(downButton);
        buttonPanel.add(upButton);

        JScrollPane possibleScrollPane = new JScrollPane(possibleMatchesList);
        possibleScrollPane.setPreferredSize(new Dimension(600, 450));
        possibleScrollPane.setBorder(BorderFactory.createTitledBorder("Alle möglichen Begegnungen"));

        JScrollPane selectedScrollPane = new JScrollPane(selectedMatchesList);
        selectedScrollPane.setPreferredSize(new Dimension(600, 150));
        selectedScrollPane.setBorder(BorderFactory.createTitledBorder("Ausgewählte Begegnungen (Es müssen genau " + (playerCount / 2 + (playerCount % 2)) + " ausgewählt werden!)"));

        JButton commitButton = new JButton("Ausgewählte Begegnungen als neue Setzung übernehmen");
        commitButton.addActionListener(e -> commitSelectedMatches());

        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.add(possibleScrollPane, BorderLayout.CENTER);
        topPanel.add(buttonPanel, BorderLayout.SOUTH);

        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.add(selectedScrollPane, BorderLayout.CENTER);
        bottomPanel.add(commitButton, BorderLayout.SOUTH);

        add(topPanel, BorderLayout.NORTH);
        add(bottomPanel, BorderLayout.CENTER);
    }

    /**
     * Handles the selection of a possible match in the list. Allows selecting/deselecting by left-clicking.
     */
    private void handlePossibleMatchSelection() {
        int index = possibleMatchesList.getSelectedIndex();
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
     * Moves the currently selected match from possible matches to selected matches.
     */
    private void moveMatchToSelected() {
        int index = possibleMatchesList.getSelectedIndex();
        if (index != -1) {
            Match selectedMatch = possibleMatchesModel.getElementAt(index);
            possibleMatchesModel.remove(index);
            selectedMatchesModel.addElement(selectedMatch);
            filterPossibleMatches();
        }
    }

    /**
     * Moves the currently selected match from selected matches back to possible matches.
     */
    private void moveMatchToPossible() {
        int index = selectedMatchesList.getSelectedIndex();
        if (index != -1) {
            Match selectedMatch = selectedMatchesModel.getElementAt(index);
            selectedMatchesModel.remove(index);
            possibleMatchesModel.addElement(selectedMatch);
            refreshPossibleMatches();
        }
    }

    /**
     * Filters the list of possible matches based on currently selected matches.
     */
    private void filterPossibleMatches() {
        addSelectedPlayers();
    }

    private void addSelectedPlayers() {
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
     * Refreshes the list of possible matches based on currently selected matches.
     */
    private void refreshPossibleMatches() {
        addSelectedPlayers();
    }

    /**
     * Commits the selected matches as the new pairing for the tournament round, provided the conditions are met.
     */
    private void commitSelectedMatches() {
        int neededMatches = playerCount / 2 + (playerCount % 2);
        if (selectedMatchesModel.getSize() != neededMatches) {
            JOptionPane.showMessageDialog(this, "Die Anzahl der ausgewählten Begegnungen stimmt nicht mit der benötigten Anzahl überein.");
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
            JOptionPane.showMessageDialog(this, "Bei ungerader Spieleranzahl muss ein Freilos-Spiel ausgewählt sein.");
            return;
        }

        List<Match> selectedMatches = new ArrayList<>();
        for (int i = 0; i < selectedMatchesModel.getSize(); i++) {
            selectedMatches.add(selectedMatchesModel.getElementAt(i));
        }

        tournamentRound.setNewMatches(selectedMatches);
        JOptionPane.showMessageDialog(this, "Ausgewählte Begegnungen wurden als neue Setzung übernommen.");
        dispose();
    }

    /**
     * Custom cell renderer for rendering matches in JList components.
     */
    private static class MatchRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof Match match) {
                if (match.getSecondPlayer() == null) {
                    setText(match.getFirstPlayer().getFullName() + " - Freilos");
                } else {
                    setText(match.getFirstPlayer().getFullName() + " vs " + match.getSecondPlayer().getFullName());
                }
            }
            return this;
        }
    }
}
