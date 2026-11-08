package view;

import model.TournamentMode;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * The main tournament round window.
 *
 * <p>Displays the current pairings (with their result status), the standings table and all
 * action buttons. Pairings and standings share a split pane that the user can resize. All
 * interaction logic is handled by {@link controller.TournamentRound}.</p>
 */
public class TournamentRoundView extends JFrame {

    private final String tournamentName;
    private final JTextArea pairingsTextArea;
    private final JTable resultsTable;
    private final GroupTablesPanel groupTablesPanel;
    private final JLabel currentRoundLabel;
    private final JLabel statusLabel;
    private final JButton previewRefereeSheetsButton;
    private final JButton resultEntryButton;
    private final JButton manipulateButton;
    private final JButton undoRoundButton;
    private final JButton printTableButton;
    private final JButton crossTableButton;
    private final JButton bracketButton;
    private final JButton withdrawPlayerButton;
    private final JButton addPlayerButton;
    private final JButton nextRoundButton;

    /**
     * Constructs the tournament round window and lays out all components.
     *
     * @param tournamentName the name of the tournament, shown in the header and window title
     * @param mode           the tournament mode; Swiss-only buttons are hidden in Round Robin
     */
    public TournamentRoundView(String tournamentName, TournamentMode mode) {
        this.tournamentName = tournamentName;
        setTitle(tournamentName);
        UITheme.sizeToScreen(this, 900, 760);
        UITheme.applyAppIcon(this);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setLayout(new BorderLayout());
        getContentPane().setBackground(UITheme.BACKGROUND);

        // --- header ---------------------------------------------------------------------------
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(UITheme.PRIMARY);
        headerPanel.setBorder(new EmptyBorder(10, 20, 10, 20));

        JLabel titleLabel = new JLabel("Turnier: " + tournamentName);
        titleLabel.setFont(UITheme.FONT_TITLE);
        titleLabel.setForeground(Color.WHITE);

        currentRoundLabel = new JLabel("Runde 1");
        currentRoundLabel.setFont(UITheme.FONT_SUBTITLE);
        currentRoundLabel.setForeground(new Color(200, 220, 255));

        JPanel headerTextPanel = new JPanel();
        headerTextPanel.setLayout(new BoxLayout(headerTextPanel, BoxLayout.Y_AXIS));
        headerTextPanel.setBackground(UITheme.PRIMARY);
        headerTextPanel.add(titleLabel);
        headerTextPanel.add(Box.createVerticalStrut(2));
        headerTextPanel.add(currentRoundLabel);
        headerPanel.add(headerTextPanel, BorderLayout.WEST);

        statusLabel = new JLabel(" ");
        statusLabel.setFont(UITheme.FONT_SUBTITLE);
        statusLabel.setForeground(Color.WHITE);
        headerPanel.add(statusLabel, BorderLayout.EAST);
        add(headerPanel, BorderLayout.NORTH);

        // --- pairings ------------------------------------------------------------------------
        pairingsTextArea = new JTextArea();
        pairingsTextArea.setEditable(false);
        pairingsTextArea.setFont(UITheme.FONT_MONO);
        pairingsTextArea.setMargin(new Insets(8, 10, 8, 10));
        pairingsTextArea.setBackground(UITheme.SURFACE);

        JPanel pairingsInner = new JPanel(new BorderLayout());
        pairingsInner.setBackground(UITheme.SURFACE);
        pairingsInner.setBorder(UITheme.cardBorder("Aktuelle Begegnungen"));
        pairingsInner.add(new JScrollPane(pairingsTextArea), BorderLayout.CENTER);

        JPanel roundButtons = new JPanel(new WrapLayout(FlowLayout.CENTER, 10, 6));
        roundButtons.setBackground(UITheme.SURFACE);
        previewRefereeSheetsButton = UITheme.createSecondaryButton("Schiedsrichterzettel");
        resultEntryButton = UITheme.createPrimaryButton("Ergebnisse erfassen");
        roundButtons.add(previewRefereeSheetsButton);
        roundButtons.add(resultEntryButton);
        if (mode == TournamentMode.SWISS) {
            manipulateButton = UITheme.createSecondaryButton("Setzung manipulieren");
            roundButtons.add(manipulateButton);
        } else {
            manipulateButton = null;
        }
        undoRoundButton = UITheme.createSecondaryButton("Auslosung zurücknehmen");
        undoRoundButton.setToolTipText("Nimmt die Auslosung der aktuellen Runde zurück, solange noch kein Ergebnis erfasst ist.");
        roundButtons.add(undoRoundButton);
        pairingsInner.add(roundButtons, BorderLayout.SOUTH);

        JPanel pairingsCard = new JPanel(new BorderLayout());
        pairingsCard.setBackground(UITheme.BACKGROUND);
        pairingsCard.setBorder(new EmptyBorder(10, 12, 4, 12));
        pairingsCard.add(pairingsInner, BorderLayout.CENTER);

        // --- standings -------------------------------------------------------------------------
        resultsTable = new JTable();
        UITheme.applyTableStyling(resultsTable);
        // group/knockout: group tables instead of a ranking list
        groupTablesPanel = mode == TournamentMode.GROUPS_KO ? new GroupTablesPanel() : null;
        JScrollPane tableScrollPane = UITheme.neutralViewport(
                new JScrollPane(groupTablesPanel != null ? groupTablesPanel : resultsTable));
        tableScrollPane.getVerticalScrollBar().setUnitIncrement(16);
        tableScrollPane.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, UITheme.BORDER_COLOR));

        JPanel tableInner = new JPanel(new BorderLayout());
        tableInner.setBackground(UITheme.SURFACE);
        tableInner.setBorder(UITheme.cardBorder(groupTablesPanel != null ? "Gruppen" : "Rangliste"));
        tableInner.add(tableScrollPane, BorderLayout.CENTER);

        JPanel tableCard = new JPanel(new BorderLayout());
        tableCard.setBackground(UITheme.BACKGROUND);
        tableCard.setBorder(new EmptyBorder(4, 12, 4, 12));
        tableCard.add(tableInner, BorderLayout.CENTER);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, pairingsCard, tableCard);
        split.setResizeWeight(0.4);
        split.setBorder(null);
        split.setOneTouchExpandable(true);
        split.setBackground(UITheme.BACKGROUND);
        add(split, BorderLayout.CENTER);

        // --- bottom buttons ------------------------------------------------------------------
        JPanel bottomButtonPanel = new JPanel(new WrapLayout(FlowLayout.CENTER, 10, 10));
        bottomButtonPanel.setBackground(UITheme.BACKGROUND);

        printTableButton = UITheme.createSecondaryButton("Tabelle drucken");
        bottomButtonPanel.add(printTableButton);

        crossTableButton = UITheme.createSecondaryButton("Kreuztabelle");
        bottomButtonPanel.add(crossTableButton);

        if (mode == TournamentMode.GROUPS_KO) {
            bracketButton = UITheme.createSecondaryButton("Turnierbaum");
            bottomButtonPanel.add(bracketButton);
        } else {
            bracketButton = null;
        }

        withdrawPlayerButton = UITheme.createSecondaryButton("Spieler zurückziehen");
        bottomButtonPanel.add(withdrawPlayerButton);

        if (mode == TournamentMode.SWISS) {
            addPlayerButton = UITheme.createSecondaryButton("Spieler nachmelden");
            bottomButtonPanel.add(addPlayerButton);
        } else {
            addPlayerButton = null;
        }

        nextRoundButton = UITheme.createPrimaryButton("Nächste Runde auslosen");
        bottomButtonPanel.add(nextRoundButton);

        add(bottomButtonPanel, BorderLayout.SOUTH);
    }

    /**
     * Shows the round in the header and in the window title (visible in the taskbar).
     *
     * @param round         the current round
     * @param plannedRounds the planned number of rounds, 0 if open
     * @param finished      {@code true} if the tournament is over
     */
    public void showRound(int round, int plannedRounds, boolean finished) {
        showRound("Runde " + round + (plannedRounds > 0 ? " von " + plannedRounds : ""), finished);
    }

    /**
     * Shows a round label (e.g. "Vorrunde – Runde 2 von 3" or "KO-Runde: Halbfinale") in the
     * header and in the window title.
     *
     * @param roundText the label
     * @param finished  {@code true} if the tournament is over
     */
    public void showRound(String roundText, boolean finished) {
        currentRoundLabel.setText(roundText);
        statusLabel.setText(finished ? "Turnier beendet" : " ");
        setTitle(tournamentName + " – " + (finished ? "beendet" : roundText));
    }

    /**
     * Returns the button that opens the knockout bracket (group/knockout tournaments only).
     *
     * @return the button, or {@code null} for other formats
     */
    public JButton getBracketButton() {
        return bracketButton;
    }

    /**
     * Returns the pairings text area showing the current round's match-ups.
     *
     * @return the pairings text area
     */
    public JTextArea getPairingsTextArea() {
        return pairingsTextArea;
    }

    /**
     * Returns the standings table.
     *
     * @return the results table
     */
    public JTable getResultsTable() {
        return resultsTable;
    }

    /**
     * Returns the group tables shown instead of the ranking in a group/knockout tournament.
     *
     * @return the group tables panel, or {@code null} in the other formats
     */
    public GroupTablesPanel getGroupTablesPanel() {
        return groupTablesPanel;
    }

    /**
     * Returns the label showing the current round.
     *
     * @return the current round label
     */
    public JLabel getCurrentRoundLabel() {
        return currentRoundLabel;
    }

    /**
     * Returns the button that opens the referee sheets.
     *
     * @return the referee sheets button
     */
    public JButton getPreviewRefereeSheetsButton() {
        return previewRefereeSheetsButton;
    }

    /**
     * Returns the button that opens the result entry dialog.
     *
     * @return the result entry button
     */
    public JButton getResultEntryButton() {
        return resultEntryButton;
    }

    /**
     * Returns the button for manual pairing changes (Swiss only).
     *
     * @return the manipulate button, or {@code null} in Round Robin mode
     */
    public JButton getManipulateButton() {
        return manipulateButton;
    }

    /**
     * Returns the button that takes back the current round's draw.
     *
     * @return the undo button
     */
    public JButton getUndoRoundButton() {
        return undoRoundButton;
    }

    /**
     * Returns the button that prints the current standings table.
     *
     * @return the print table button
     */
    public JButton getPrintTableButton() {
        return printTableButton;
    }

    /**
     * Returns the button that opens the cross table.
     *
     * @return the cross table button
     */
    public JButton getCrossTableButton() {
        return crossTableButton;
    }

    /**
     * Returns the button for withdrawing a player.
     *
     * @return the withdraw button
     */
    public JButton getWithdrawPlayerButton() {
        return withdrawPlayerButton;
    }

    /**
     * Returns the button for late registration of a player (Swiss only).
     *
     * @return the add player button, or {@code null} in Round Robin mode
     */
    public JButton getAddPlayerButton() {
        return addPlayerButton;
    }

    /**
     * Returns the button that draws and starts the next round.
     *
     * @return the next round button
     */
    public JButton getNextRoundButton() {
        return nextRoundButton;
    }
}
