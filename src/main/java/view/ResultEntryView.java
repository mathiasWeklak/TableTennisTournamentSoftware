package view;

import controller.ResultEntryController;
import controller.TournamentRound;
import model.Match;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.List;
import java.util.stream.IntStream;

/**
 * View for entering match results during a tournament round.
 *
 * <p>Displays one tab per non-bye match. Each tab contains per-set score fields for both
 * players and read-only summary fields that update automatically as set scores are entered.
 * Saving (either via button or window close) delegates to
 * {@link ResultEntryController#saveResults(boolean)}.</p>
 */
public class ResultEntryView extends JFrame {

    private final JTabbedPane tabbedPane;
    private final TournamentRound tournamentRound;
    private final ResultEntryController controller;

    /**
     * Constructs the result entry window and displays it.
     *
     * @param controller      the controller handling save and auto-update logic
     * @param matches         the list of matches whose results can be entered
     * @param tournamentRound the tournament round, used to update the standings table on save
     */
    public ResultEntryView(ResultEntryController controller, List<Match> matches, TournamentRound tournamentRound) {
        this.controller = controller;
        this.tournamentRound = tournamentRound;

        setTitle("Ergebniserfassung");
        setLayout(new BorderLayout());
        setSize(560, 520);
        getContentPane().setBackground(UITheme.BACKGROUND);

        tabbedPane = new JTabbedPane();
        tabbedPane.setFont(UITheme.FONT_BODY);

        matches.stream()
                .filter(match -> match.getSecondPlayer() != null)
                .forEach(match -> tabbedPane.addTab("Tisch " + match.getTableNumber(), createMatchPanel(match)));

        JButton saveButton = UITheme.createPrimaryButton("Ergebnisse speichern");
        saveButton.addActionListener(_ -> controller.saveResults(true));

        JPanel savePanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 10));
        savePanel.setBackground(UITheme.BACKGROUND);
        savePanel.add(saveButton);

        add(tabbedPane, BorderLayout.CENTER);
        add(savePanel, BorderLayout.SOUTH);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                controller.saveResults(false);
                dispose();
            }
        });

        setLocationRelativeTo(null);
        setVisible(true);
    }

    /**
     * Builds the result-entry panel for a single match.
     * Contains per-set score input fields and read-only overall-result summary fields.
     * The panel stores its field references as client properties for retrieval by the controller.
     *
     * @param match the match for which to build the entry panel
     * @return the configured match panel
     */
    private JPanel createMatchPanel(Match match) {
        JPanel matchPanel = new JPanel(new BorderLayout());
        matchPanel.setBackground(UITheme.BACKGROUND);
        matchPanel.setBorder(new EmptyBorder(12, 16, 12, 16));

        JTextField[][] setResultsFields = new JTextField[5][2];

        JPanel scoreCard = new JPanel(new BorderLayout(0, 0));
        scoreCard.setBackground(UITheme.SURFACE);
        scoreCard.setBorder(UITheme.cardBorder("Satzergebnisse"));

        JPanel gridPanel = new JPanel(new GridBagLayout());
        gridPanel.setBackground(UITheme.SURFACE);
        gridPanel.setBorder(new EmptyBorder(6, 8, 8, 8));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(3, 6, 3, 6);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0.3;
        JLabel headerEmpty = new JLabel("");
        headerEmpty.setFont(UITheme.FONT_SUBTITLE);
        gridPanel.add(headerEmpty, gbc);

        gbc.gridx = 1; gbc.weightx = 0.35;
        JLabel p1Header = new JLabel(match.getFirstPlayer().getFullName(), SwingConstants.CENTER);
        p1Header.setFont(UITheme.FONT_SUBTITLE);
        p1Header.setForeground(UITheme.PRIMARY);
        gridPanel.add(p1Header, gbc);

        gbc.gridx = 2; gbc.weightx = 0.35;
        JLabel p2Header = new JLabel(match.getSecondPlayer().getFullName(), SwingConstants.CENTER);
        p2Header.setFont(UITheme.FONT_SUBTITLE);
        p2Header.setForeground(UITheme.PRIMARY);
        gridPanel.add(p2Header, gbc);

        JSeparator sep = new JSeparator();
        sep.setForeground(UITheme.BORDER_COLOR);
        gbc.gridx = 0; gbc.gridy = 1; gbc.gridwidth = 3; gbc.insets = new Insets(2, 6, 4, 6);
        gridPanel.add(sep, gbc);
        gbc.gridwidth = 1; gbc.insets = new Insets(3, 6, 3, 6);

        IntStream.range(0, 5).forEach(i -> {
            setResultsFields[i][0] = controller.createIntegerField();
            setResultsFields[i][1] = controller.createIntegerField();
            styleScoreField(setResultsFields[i][0]);
            styleScoreField(setResultsFields[i][1]);

            gbc.gridx = 0; gbc.gridy = i + 2; gbc.weightx = 0.3;
            JLabel setLabel = new JLabel("Satz " + (i + 1));
            setLabel.setFont(UITheme.FONT_BODY);
            setLabel.setForeground(UITheme.TEXT_MUTED);
            gridPanel.add(setLabel, gbc);

            gbc.gridx = 1; gbc.weightx = 0.35;
            gridPanel.add(setResultsFields[i][0], gbc);

            gbc.gridx = 2; gbc.weightx = 0.35;
            gridPanel.add(setResultsFields[i][1], gbc);

            setResultsFields[i][0].getDocument().addDocumentListener(new ResultDocumentListener(matchPanel));
            setResultsFields[i][1].getDocument().addDocumentListener(new ResultDocumentListener(matchPanel));
        });

        if (match.getResults() != null) {
            IntStream.range(0, 5)
                    .filter(i -> match.getResults()[i] != null)
                    .forEach(i -> {
                        setResultsFields[i][0].setText(match.getResults()[i][0]);
                        setResultsFields[i][1].setText(match.getResults()[i][1]);
                    });
        }

        JSeparator sep2 = new JSeparator();
        sep2.setForeground(UITheme.BORDER_COLOR);
        gbc.gridx = 0; gbc.gridy = 7; gbc.gridwidth = 3; gbc.insets = new Insets(4, 6, 2, 6);
        gridPanel.add(sep2, gbc);
        gbc.gridwidth = 1; gbc.insets = new Insets(3, 6, 3, 6);

        JTextField wonSetsField = new JTextField();
        JTextField lostSetsField = new JTextField();
        wonSetsField.setEditable(false);
        lostSetsField.setEditable(false);
        wonSetsField.setHorizontalAlignment(SwingConstants.CENTER);
        lostSetsField.setHorizontalAlignment(SwingConstants.CENTER);
        wonSetsField.setFont(UITheme.FONT_SUBTITLE);
        lostSetsField.setFont(UITheme.FONT_SUBTITLE);
        wonSetsField.setBackground(new Color(232, 245, 233));
        lostSetsField.setBackground(new Color(255, 235, 238));

        gbc.gridx = 0; gbc.gridy = 8; gbc.weightx = 0.3;
        JLabel totalLabel = new JLabel("Gesamtergebnis");
        totalLabel.setFont(UITheme.FONT_BODY.deriveFont(Font.BOLD));
        gridPanel.add(totalLabel, gbc);

        gbc.gridx = 1; gbc.weightx = 0.35;
        gridPanel.add(wonSetsField, gbc);

        gbc.gridx = 2; gbc.weightx = 0.35;
        gridPanel.add(lostSetsField, gbc);

        String overallResult = match.getOverallResult();
        if (overallResult != null && overallResult.contains(":")) {
            String[] parts = overallResult.split(":");
            if (parts.length == 2) {
                wonSetsField.setText(parts[0]);
                lostSetsField.setText(parts[1]);
            }
        }

        scoreCard.add(gridPanel, BorderLayout.CENTER);
        matchPanel.add(scoreCard, BorderLayout.CENTER);

        matchPanel.putClientProperty("matchData",
                new ResultEntryController.MatchPanelData(setResultsFields, wonSetsField, lostSetsField, match));

        return matchPanel;
    }

    /**
     * Applies standard styling to a score input field (centered text, fixed size, body font).
     *
     * @param field the text field to style
     */
    private static void styleScoreField(JTextField field) {
        field.setFont(UITheme.FONT_BODY);
        field.setHorizontalAlignment(SwingConstants.CENTER);
        field.setPreferredSize(new Dimension(60, 28));
    }

    /**
     * Returns the tabbed pane containing one result-entry tab per match.
     * Used by the controller to iterate over match panels and read entered scores.
     *
     * @return the tabbed pane
     */
    public JTabbedPane getTabbedPane() {
        return tabbedPane;
    }

    /**
     * Returns the tournament round associated with this view.
     * Used by the controller to trigger a standings table refresh after saving results.
     *
     * @return the tournament round
     */
    public TournamentRound getTournamentRound() {
        return tournamentRound;
    }

    /**
     * Document listener that triggers an overall-result recalculation whenever a set score
     * field is modified.
     */
    private class ResultDocumentListener implements DocumentListener {

        private final JPanel matchPanel;

        /**
         * Constructs a listener bound to the given match panel.
         *
         * @param matchPanel the panel containing the score fields to monitor
         */
        ResultDocumentListener(JPanel matchPanel) {
            this.matchPanel = matchPanel;
        }

        /** {@inheritDoc} */
        @Override
        public void insertUpdate(DocumentEvent e) {
            controller.updateOverallResult(matchPanel);
        }

        /** {@inheritDoc} */
        @Override
        public void removeUpdate(DocumentEvent e) {
            controller.updateOverallResult(matchPanel);
        }

        /** {@inheritDoc} */
        @Override
        public void changedUpdate(DocumentEvent e) {
            controller.updateOverallResult(matchPanel);
        }
    }
}
