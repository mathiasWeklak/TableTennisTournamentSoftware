package view;

import model.TournamentMode;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * The main tournament round window.
 *
 * <p>Displays the current pairings text area, the standings table, and all action buttons.
 * All interaction logic is handled by {@link controller.TournamentRound}.</p>
 */
public class TournamentRoundView extends JFrame {

    private final JTextArea pairingsTextArea;
    private final JTable resultsTable;
    private final JLabel currentRoundLabel;
    private final JButton previewRefereeSheetsButton;
    private final JButton resultEntryButton;
    private final JButton manipulateButton;
    private final JButton printTableButton;
    private final JButton nextRoundButton;

    /**
     * Constructs the tournament round window and lays out all components.
     *
     * @param tournamentName the name of the tournament, shown in the header
     * @param mode           the tournament mode; determines whether the manipulate button is shown
     */
    public TournamentRoundView(String tournamentName, TournamentMode mode) {
        setTitle("Turnierrunde");
        setSize(800, 680);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setLayout(new BorderLayout());
        getContentPane().setBackground(UITheme.BACKGROUND);

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
        add(headerPanel, BorderLayout.NORTH);

        pairingsTextArea = new JTextArea(11, 20);
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

        JPanel buttonsPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 8));
        buttonsPanel.setBackground(UITheme.BACKGROUND);

        previewRefereeSheetsButton = UITheme.createSecondaryButton("Schiedsrichterzettel anzeigen");
        buttonsPanel.add(previewRefereeSheetsButton);

        resultEntryButton = UITheme.createPrimaryButton("Ergebnisse erfassen");
        buttonsPanel.add(resultEntryButton);

        if (mode == TournamentMode.SWISS) {
            manipulateButton = UITheme.createSecondaryButton("Setzung manipulieren");
            buttonsPanel.add(manipulateButton);
        } else {
            manipulateButton = null;
        }

        bottomPanel.add(buttonsPanel, BorderLayout.NORTH);

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

        printTableButton = UITheme.createSecondaryButton("Tabelle drucken");
        bottomButtonPanel.add(printTableButton);

        nextRoundButton = UITheme.createPrimaryButton("Nächste Runde auslosen und starten");
        bottomButtonPanel.add(nextRoundButton);

        bottomPanel.add(bottomButtonPanel, BorderLayout.SOUTH);
        add(bottomPanel, BorderLayout.SOUTH);
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
     * Returns the label displaying the current round number.
     *
     * @return the current round label
     */
    public JLabel getCurrentRoundLabel() {
        return currentRoundLabel;
    }

    /**
     * Returns the button that opens the referee sheets preview.
     *
     * @return the preview referee sheets button
     */
    public JButton getPreviewRefereeSheetsButton() {
        return previewRefereeSheetsButton;
    }

    /**
     * Returns the button that opens the result entry window.
     *
     * @return the result entry button
     */
    public JButton getResultEntryButton() {
        return resultEntryButton;
    }

    /**
     * Returns the button for manually overriding pairings, or {@code null} if not in Swiss mode.
     *
     * @return the manipulate button, or {@code null}
     */
    public JButton getManipulateButton() {
        return manipulateButton;
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
     * Returns the button that advances to the next round.
     *
     * @return the next round button
     */
    public JButton getNextRoundButton() {
        return nextRoundButton;
    }
}
