package view;

import model.Match;

import javax.swing.*;
import java.awt.*;

/**
 * View class for managing the display and interaction of possible and selected matches.
 * Provides a user interface to manage match selections for a tournament.
 */
public class MatchManagerView extends JFrame {
    private final JList<Match> possibleMatchesList;
    private final JList<Match> selectedMatchesList;
    private final JButton downButton;
    private final JButton upButton;
    private final JButton commitButton;

    /**
     * Constructs a MatchManagerView with the specified models and player count.
     *
     * @param possibleMatchesModel  The model containing possible matches.
     * @param selectedMatchesModel  The model containing selected matches.
     * @param playerCount           The total number of players.
     */
    public MatchManagerView(DefaultListModel<Match> possibleMatchesModel,
                            DefaultListModel<Match> selectedMatchesModel, int playerCount) {

        setTitle("Begegnungs Manager");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(600, 700);
        setLayout(new BorderLayout());

        possibleMatchesList = new JList<>(possibleMatchesModel);
        selectedMatchesList = new JList<>(selectedMatchesModel);

        possibleMatchesList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        selectedMatchesList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        possibleMatchesList.setCellRenderer(new MatchRenderer());
        selectedMatchesList.setCellRenderer(new MatchRenderer());

        downButton = new JButton("↓");
        upButton = new JButton("↑");

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

        commitButton = new JButton("Ausgewählte Begegnungen als neue Setzung übernehmen");

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
     * Gets the list of possible matches.
     *
     * @return The JList of possible matches.
     */
    public JList<Match> getPossibleMatchesList() {
        return possibleMatchesList;
    }

    /**
     * Gets the list of selected matches.
     *
     * @return The JList of selected matches.
     */
    public JList<Match> getSelectedMatchesList() {
        return selectedMatchesList;
    }

    /**
     * Gets the button for moving a match to the selected list.
     *
     * @return The JButton for moving a match down.
     */
    public JButton getDownButton() {
        return downButton;
    }

    /**
     * Gets the button for moving a match to the possible list.
     *
     * @return The JButton for moving a match up.
     */
    public JButton getUpButton() {
        return upButton;
    }

    /**
     * Gets the button for committing the selected matches.
     *
     * @return The JButton for committing selected matches.
     */
    public JButton getCommitButton() {
        return commitButton;
    }

    /**
     * Custom cell renderer for displaying matches in the lists.
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
