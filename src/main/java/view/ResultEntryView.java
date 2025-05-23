package view;

import controller.ResultEntryController;
import controller.TournamentRound;
import model.Match;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.List;
import java.util.stream.IntStream;

/**
 * GUI window for entering match results in a tournament round.
 */
public class ResultEntryView extends JFrame {

    private final JTabbedPane tabbedPane;
    private final TournamentRound tournamentRound;
    private final ResultEntryController controller;

    /**
     * Constructs the result entry window for given matches and tournament round.
     *
     * @param controller      The controller handling the logic for this view
     * @param matches         List of matches to display and manage results for
     * @param tournamentRound The tournament round associated with the matches
     */
    public ResultEntryView(ResultEntryController controller, List<Match> matches, TournamentRound tournamentRound) {
        this.controller = controller;
        this.tournamentRound = tournamentRound;

        setTitle("Ergebniserfassung");
        setLayout(new BorderLayout());
        setSize(400, 300);

        tabbedPane = new JTabbedPane();

        matches.stream()
                .filter(match -> match.getSecondPlayer() != null)
                .forEach(match -> tabbedPane.addTab("Tisch " + match.getTableNumber(), createMatchPanel(match)));

        JButton saveButton = new JButton("Speichern");
        saveButton.addActionListener(e -> controller.saveResults(true));

        add(tabbedPane, BorderLayout.CENTER);
        add(saveButton, BorderLayout.SOUTH);

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
     * Creates a panel for a specific match with input fields for set results.
     *
     * @param match The match for which the panel is created
     * @return JPanel representing the match input panel
     */
    private JPanel createMatchPanel(Match match) {
        JPanel matchPanel = new JPanel(new BorderLayout());

        JTextField[][] setResultsFields = new JTextField[5][2];
        JPanel resultsPanel = new JPanel(new GridLayout(7, 3));

        resultsPanel.add(new JLabel("Begegnung"));
        resultsPanel.add(new JLabel(match.getFirstPlayer().getFullName()));
        resultsPanel.add(new JLabel(match.getSecondPlayer().getFullName()));

        IntStream.range(0, 5).forEach(i -> {
            setResultsFields[i][0] = controller.createIntegerField();
            setResultsFields[i][1] = controller.createIntegerField();
            resultsPanel.add(new JLabel("Satz " + (i + 1)));
            resultsPanel.add(setResultsFields[i][0]);
            resultsPanel.add(setResultsFields[i][1]);

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

        JTextField wonSetsField = new JTextField();
        JTextField lostSetsField = new JTextField();
        resultsPanel.add(new JLabel("Gesamtergebnis"));
        resultsPanel.add(wonSetsField);
        resultsPanel.add(lostSetsField);

        String overallResult = match.getOverallResult();
        if (overallResult != null && overallResult.contains(":")) {
            String[] parts = overallResult.split(":");
            if (parts.length == 2) {
                wonSetsField.setText(parts[0]);
                lostSetsField.setText(parts[1]);
            }
        }

        wonSetsField.setEditable(false);
        lostSetsField.setEditable(false);

        matchPanel.add(resultsPanel, BorderLayout.CENTER);

        matchPanel.putClientProperty("setResultsFields", setResultsFields);
        matchPanel.putClientProperty("wonSetsField", wonSetsField);
        matchPanel.putClientProperty("lostSetsField", lostSetsField);
        matchPanel.putClientProperty("match", match);

        return matchPanel;
    }

    public JTabbedPane getTabbedPane() {
        return tabbedPane;
    }

    public TournamentRound getTournamentRound() {
        return tournamentRound;
    }

    /**
     * Document listener that updates overall results based on set results changes.
     */
    private class ResultDocumentListener implements DocumentListener {

        private final JPanel matchPanel;

        ResultDocumentListener(JPanel matchPanel) {
            this.matchPanel = matchPanel;
        }

        @Override
        public void insertUpdate(DocumentEvent e) {
            controller.updateOverallResult(matchPanel);
        }

        @Override
        public void removeUpdate(DocumentEvent e) {
            controller.updateOverallResult(matchPanel);
        }

        @Override
        public void changedUpdate(DocumentEvent e) {
            controller.updateOverallResult(matchPanel);
        }
    }
}
