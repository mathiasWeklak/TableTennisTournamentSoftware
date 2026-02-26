package view;

import model.Match;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * View for manually overriding the current round's pairings in Swiss System mode.
 *
 * <p>Displays all unplayed possible matches in one list and allows the user to move
 * matches to a "selected" list. When the required number of matches (covering all players)
 * is selected, the user can commit the new pairings to the tournament round.</p>
 *
 * <p>All interaction logic is handled by {@link controller.MatchManagerController}.</p>
 */
public class MatchManagerView extends JFrame {
    private final JList<Match> possibleMatchesList;
    private final JList<Match> selectedMatchesList;
    private final JButton downButton;
    private final JButton upButton;
    private final JButton commitButton;

    /**
     * Constructs the match manager window.
     *
     * @param possibleMatchesModel the list model of all possible (unplayed) matches
     * @param selectedMatchesModel the list model for the currently selected matches
     * @param playerCount          the total number of players (used to compute required match count)
     */
    public MatchManagerView(DefaultListModel<Match> possibleMatchesModel,
                            DefaultListModel<Match> selectedMatchesModel, int playerCount) {

        setTitle("Begegnungs Manager");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(680, 750);
        setLayout(new BorderLayout());
        getContentPane().setBackground(UITheme.BACKGROUND);

        possibleMatchesList = new JList<>(possibleMatchesModel);
        selectedMatchesList = new JList<>(selectedMatchesModel);

        possibleMatchesList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        selectedMatchesList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        possibleMatchesList.setFont(UITheme.FONT_BODY);
        possibleMatchesList.setFixedCellHeight(28);
        possibleMatchesList.setBackground(UITheme.SURFACE);
        possibleMatchesList.setSelectionBackground(new Color(187, 222, 251));

        selectedMatchesList.setFont(UITheme.FONT_BODY);
        selectedMatchesList.setFixedCellHeight(28);
        selectedMatchesList.setBackground(UITheme.SURFACE);
        selectedMatchesList.setSelectionBackground(new Color(187, 222, 251));

        possibleMatchesList.setCellRenderer(new MatchRenderer());
        selectedMatchesList.setCellRenderer(new MatchRenderer());

        downButton = UITheme.createPrimaryButton("Auswählen  ↓");
        upButton = UITheme.createSecondaryButton("↑  Entfernen");

        JPanel arrowPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 6));
        arrowPanel.setBackground(UITheme.BACKGROUND);
        arrowPanel.add(downButton);
        arrowPanel.add(upButton);

        JScrollPane possibleScrollPane = new JScrollPane(possibleMatchesList);
        possibleScrollPane.setBorder(null);

        JPanel possibleCard = new JPanel(new BorderLayout(0, 0));
        possibleCard.setBackground(UITheme.BACKGROUND);
        possibleCard.setBorder(new EmptyBorder(12, 12, 0, 12));

        JPanel possibleInner = new JPanel(new BorderLayout());
        possibleInner.setBackground(UITheme.SURFACE);
        possibleInner.setBorder(UITheme.cardBorder("Alle möglichen Begegnungen"));
        possibleInner.add(possibleScrollPane, BorderLayout.CENTER);
        possibleCard.add(possibleInner, BorderLayout.CENTER);

        int required = playerCount / 2 + playerCount % 2;
        JScrollPane selectedScrollPane = new JScrollPane(selectedMatchesList);
        selectedScrollPane.setBorder(null);
        selectedScrollPane.setPreferredSize(new Dimension(0, 160));

        JPanel selectedCard = new JPanel(new BorderLayout(0, 0));
        selectedCard.setBackground(UITheme.BACKGROUND);
        selectedCard.setBorder(new EmptyBorder(0, 12, 12, 12));

        JPanel selectedInner = new JPanel(new BorderLayout());
        selectedInner.setBackground(UITheme.SURFACE);
        selectedInner.setBorder(UITheme.cardBorder(
            "Ausgewählte Begegnungen  (genau " + required + " erforderlich)"
        ));
        selectedInner.add(selectedScrollPane, BorderLayout.CENTER);
        selectedCard.add(selectedInner, BorderLayout.CENTER);

        commitButton = UITheme.createPrimaryButton("Ausgewählte Begegnungen als neue Setzung übernehmen");

        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.setBackground(UITheme.BACKGROUND);
        bottomPanel.add(selectedCard, BorderLayout.CENTER);

        JPanel commitPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 10));
        commitPanel.setBackground(UITheme.BACKGROUND);
        commitPanel.add(commitButton);
        bottomPanel.add(commitPanel, BorderLayout.SOUTH);

        add(possibleCard, BorderLayout.CENTER);
        add(arrowPanel, BorderLayout.NORTH);
        add(bottomPanel, BorderLayout.SOUTH);
    }

    /**
     * Returns the list of all possible (unplayed) matches.
     *
     * @return the possible matches list
     */
    public JList<Match> getPossibleMatchesList() {
        return possibleMatchesList;
    }

    /**
     * Returns the list of matches selected for the current round.
     *
     * @return the selected matches list
     */
    public JList<Match> getSelectedMatchesList() {
        return selectedMatchesList;
    }

    /**
     * Returns the "select match" (down arrow) button.
     *
     * @return the select/down button
     */
    public JButton getDownButton() {
        return downButton;
    }

    /**
     * Returns the "remove match" (up arrow) button.
     *
     * @return the remove/up button
     */
    public JButton getUpButton() {
        return upButton;
    }

    /**
     * Returns the "commit selected matches" button.
     *
     * @return the commit button
     */
    public JButton getCommitButton() {
        return commitButton;
    }

    /**
     * Custom list cell renderer for {@link Match} objects.
     * Displays each match as "Player1  vs  Player2" or "Player1  —  Freilos" for bye matches,
     * with alternating row background colors.
     */
    private static class MatchRenderer extends DefaultListCellRenderer {
        /**
         * {@inheritDoc}
         * Formats the match label and applies alternating row background colors.
         */
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            setFont(UITheme.FONT_BODY);
            if (!isSelected) {
                setBackground(index % 2 == 0 ? UITheme.SURFACE : UITheme.ROW_ALT);
            }
            setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));
            if (value instanceof Match match) {
                if (match.getSecondPlayer() == null) {
                    setText(match.getFirstPlayer().getFullName() + "  —  Freilos");
                } else {
                    setText(match.getFirstPlayer().getFullName() + "  vs  " + match.getSecondPlayer().getFullName());
                }
            }
            return this;
        }
    }
}
