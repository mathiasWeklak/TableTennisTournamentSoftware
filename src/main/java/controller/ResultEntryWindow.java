package controller;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.PlainDocument;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import model.Match;

/**
 * GUI window for entering match results in a tournament round.
 */
public class ResultEntryWindow extends JFrame {
    private final JTabbedPane tabbedPane;
    private final TournamentRound tournamentRound;

    /**
     * Constructs the result entry window for given matches and tournament round.
     *
     * @param matches         List of matches to display and manage results for
     * @param tournamentRound The tournament round associated with the matches
     */
    public ResultEntryWindow(List<Match> matches, TournamentRound tournamentRound) {
        this.tournamentRound = tournamentRound;

        setTitle("Ergebniserfassung");
        setLayout(new BorderLayout());
        setSize(400, 300);

        tabbedPane = new JTabbedPane();

        matches.stream()
                .filter(match -> match.getSecondPlayer() != null)
                .forEach(match -> tabbedPane.addTab("Tisch " + match.getTableNumber(), createMatchPanel(match)));

        JButton saveButton = new JButton("Speichern");
        saveButton.addActionListener(e -> saveResults(true));

        add(tabbedPane, BorderLayout.CENTER);
        add(saveButton, BorderLayout.SOUTH);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                saveResults(false);
                dispose();
            }
        });

        setLocationRelativeTo(null);
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
            setResultsFields[i][0] = createIntegerField();
            setResultsFields[i][1] = createIntegerField();
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

    /**
     * Creates a text field that only accepts positive integers.
     *
     * @return JTextField configured to accept only positive integers
     */
    private JTextField createIntegerField() {
        JTextField textField = new JTextField();
        textField.setDocument(new PositiveIntegerDocument());
        return textField;
    }

    /**
     * Saves the entered results for all matches and updates the UI accordingly.
     *
     * @param showConfirmation Flag indicating whether to show a confirmation message
     */
    private void saveResults(boolean showConfirmation) {
        IntStream.range(0, tabbedPane.getTabCount())
                .forEach(i -> {
                    Component component = tabbedPane.getComponentAt(i);
                    if (component instanceof JPanel matchPanel) {
                        JTextField[][] setResultsFields = (JTextField[][]) matchPanel.getClientProperty("setResultsFields");
                        JTextField wonSetsField = (JTextField) matchPanel.getClientProperty("wonSetsField");
                        JTextField lostSetsField = (JTextField) matchPanel.getClientProperty("lostSetsField");
                        Match match = (Match) matchPanel.getClientProperty("match");

                        IntStream.range(0, 5)
                                .forEach(j -> {
                                    String[] result = new String[2];
                                    result[0] = setResultsFields[j][0].getText();
                                    result[1] = setResultsFields[j][1].getText();
                                    match.setResults(j, result);
                                });

                        String overallResult = wonSetsField.getText() + ":" + lostSetsField.getText();
                        match.setOverallResult(overallResult);
                    }
                });

        tournamentRound.updateResultsTable();
        if (showConfirmation) {
            JOptionPane.showMessageDialog(this, "Ergebnisse gespeichert.");
        }
    }

    /**
     * Document listener that updates overall results based on set results changes.
     */
    private record ResultDocumentListener(JPanel matchPanel) implements DocumentListener {

        @Override
        public void insertUpdate(DocumentEvent e) {
            updateOverallResult();
        }

        @Override
        public void removeUpdate(DocumentEvent e) {
            updateOverallResult();
        }

        @Override
        public void changedUpdate(DocumentEvent e) {
            updateOverallResult();
        }

        private void updateOverallResult() {
            JTextField[][] setResultsFields = (JTextField[][]) matchPanel.getClientProperty("setResultsFields");
            JTextField wonSetsField = (JTextField) matchPanel.getClientProperty("wonSetsField");
            JTextField lostSetsField = (JTextField) matchPanel.getClientProperty("lostSetsField");

            if (setResultsFields == null || wonSetsField == null || lostSetsField == null) {
                return;
            }

            AtomicInteger wonSets = new AtomicInteger();
            AtomicInteger lostSets = new AtomicInteger();

            IntStream.range(0, 5)
                    .forEach(i -> {
                        try {
                            int firstPlayerScore = Integer.parseInt(setResultsFields[i][0].getText());
                            int secondPlayerScore = Integer.parseInt(setResultsFields[i][1].getText());
                            if (firstPlayerScore > secondPlayerScore) {
                                wonSets.getAndIncrement();
                            } else if (secondPlayerScore > firstPlayerScore) {
                                lostSets.getAndIncrement();
                            }
                        } catch (NumberFormatException ignored) {
                        }
                    });

            wonSetsField.setText(String.valueOf(wonSets.get()));
            lostSetsField.setText(String.valueOf(lostSets.get()));
        }
    }

    /**
     * Document that allows only positive integers to be entered into a text field.
     */
    private static class PositiveIntegerDocument extends PlainDocument {
        @Override
        public void insertString(int offs, String str, AttributeSet a) throws BadLocationException {
            StringBuilder sb = new StringBuilder();
            str.chars()
                    .filter(Character::isDigit)
                    .forEach(c -> sb.append((char) c));
            super.insertString(offs, sb.toString(), a);
        }
    }
}
