package view;

import static model.Match.MAX_SETS;

import controller.ResultEntryController;
import model.Match;
import model.Player;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

/**
 * Modal dialog for entering match results.
 *
 * <p>Displays one tab per non-bye match of the selected round; complete matches are marked with
 * a check mark. Keyboard flow: the content of a score field is selected when it gets the focus,
 * Enter moves to the next field and from the last field to the next match, Ctrl+S saves.
 * Closing asks about unsaved changes.</p>
 */
public class ResultEntryView extends JDialog {

    /** Drawn check mark: a font-independent replacement for "✓", which many fonts lack. */
    static final Icon DONE_ICON = new CheckIcon(14);

    private final JTabbedPane tabbedPane;
    private final ResultEntryController controller;
    private final JLabel hintLabel;

    /**
     * Constructs the modal result entry dialog. The caller fills it with
     * {@link #showMatches(List, boolean)} and makes it visible.
     *
     * @param owner        the window that owns the dialog
     * @param controller   the controller handling save and auto-update logic
     * @param rounds       the rounds that can be selected (ascending)
     * @param initialRound the round shown first
     */
    public ResultEntryView(Window owner, ResultEntryController controller, List<Integer> rounds, int initialRound) {
        super(owner, "Ergebniserfassung", ModalityType.APPLICATION_MODAL);
        this.controller = controller;

        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setLayout(new BorderLayout());
        UITheme.sizeToScreen(this, 600, 580);
        getContentPane().setBackground(UITheme.BACKGROUND);

        // --- round selection ---
        JPanel roundRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        roundRow.setBackground(UITheme.BACKGROUND);
        JLabel roundLabel = new JLabel("Runde:");
        roundLabel.setFont(UITheme.FONT_BODY);
        JComboBox<Integer> roundBox = new JComboBox<>(rounds.toArray(new Integer[0]));
        roundBox.setSelectedItem(initialRound);
        roundBox.setFocusable(false);
        roundBox.addActionListener(_ -> {
            Integer selected = (Integer) roundBox.getSelectedItem();
            if (selected != null && !controller.switchRound(selected)) {
                SwingUtilities.invokeLater(() -> roundBox.setSelectedItem(currentlyShownRound));
            } else if (selected != null) {
                currentlyShownRound = selected;
            }
        });
        currentlyShownRound = initialRound;
        hintLabel = new JLabel(" ");
        hintLabel.setFont(UITheme.FONT_BODY);
        hintLabel.setForeground(UITheme.TEXT_MUTED);
        hintLabel.setBorder(new EmptyBorder(0, 10, 4, 10));
        roundRow.add(roundLabel);
        roundRow.add(roundBox);
        // the hint gets its own row, so it is never pushed out of the visible area
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.setBackground(UITheme.BACKGROUND);
        topPanel.add(roundRow, BorderLayout.NORTH);
        topPanel.add(hintLabel, BorderLayout.SOUTH);
        add(topPanel, BorderLayout.NORTH);

        tabbedPane = new JTabbedPane();
        tabbedPane.setFont(UITheme.FONT_BODY);
        tabbedPane.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
        add(tabbedPane, BorderLayout.CENTER);

        // --- buttons ---
        JButton saveButton = UITheme.createPrimaryButton("Ergebnisse speichern (Strg+S)");
        saveButton.addActionListener(_ -> controller.saveResults(true));
        JButton closeButton = UITheme.createSecondaryButton("Schließen");
        closeButton.addActionListener(_ -> controller.requestClose());

        JPanel savePanel = new JPanel(new WrapLayout(FlowLayout.CENTER, 10, 10));
        savePanel.setBackground(UITheme.BACKGROUND);
        savePanel.add(saveButton);
        savePanel.add(closeButton);
        add(savePanel, BorderLayout.SOUTH);

        // Ctrl+S saves, Escape closes (with the unsaved-changes question)
        JRootPane root = getRootPane();
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_S, KeyEvent.CTRL_DOWN_MASK), "save");
        root.getActionMap().put("save", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                controller.saveResults(true);
            }
        });
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "close");
        root.getActionMap().put("close", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                controller.requestClose();
            }
        });

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                controller.requestClose();
            }
        });

        setLocationRelativeTo(owner);
    }

    private int currentlyShownRound;

    /**
     * Replaces the tabs with the non-bye matches of a round.
     *
     * @param matches      the matches of the round
     * @param earlierRound {@code true} if this is not the current round (shows a hint)
     */
    public void showMatches(List<Match> matches, boolean earlierRound) {
        tabbedPane.removeAll();
        hintLabel.setText(earlierRound
                ? "<html><div style='width:520px'>Korrektur einer früheren Runde: ändert die Tabelle, nicht die bereits ausgelosten Runden.</div></html>"
                : " ");
        for (Match match : matches) {
            if (match.getSecondPlayer() == null) continue;
            String fullNames = match.getFirstPlayer().getFullName() + " vs. " + match.getSecondPlayer().getFullName();
            String title = ResultEntryController.tabTitle(match);
            if (indexOfBaseTitle(title) >= 0) {
                // same table and same last names: fall back to the (unique) full names
                title = "T" + match.getTableNumber() + ": " + fullNames;
            }
            JPanel panel = createMatchPanel(match);
            panel.putClientProperty("baseTitle", title);
            tabbedPane.addTab(title, null, panel, fullNames);
            controller.updateOverallResult(panel);
        }
        if (tabbedPane.getTabCount() > 0) {
            tabbedPane.setSelectedIndex(0);
            focusFirstField(0);
        }
    }

    private int indexOfBaseTitle(String title) {
        for (int i = 0; i < tabbedPane.getTabCount(); i++) {
            Component c = tabbedPane.getComponentAt(i);
            if (c instanceof JPanel p && title.equals(p.getClientProperty("baseTitle"))) return i;
        }
        return -1;
    }

    /**
     * Marks a tab as complete (check mark) or not.
     *
     * @param matchPanel the match panel
     * @param complete   {@code true} if the entered result is a complete, valid match result
     */
    public void markTab(JPanel matchPanel, boolean complete) {
        int index = tabbedPane.indexOfComponent(matchPanel);
        if (index < 0) return;
        tabbedPane.setIconAt(index, complete ? DONE_ICON : null);
    }

    /**
     * A green circle with a white check mark, drawn with Java2D.
     */
    static final class CheckIcon implements Icon {
        private final int size;

        CheckIcon(int size) {
            this.size = size;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(UITheme.SUCCESS);
                g2.fillOval(x, y, size, size);
                g2.setColor(Color.WHITE);
                g2.setStroke(new BasicStroke(size / 7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                int[] xs = {x + size * 27 / 100, x + size * 44 / 100, x + size * 74 / 100};
                int[] ys = {y + size * 52 / 100, y + size * 69 / 100, y + size * 33 / 100};
                g2.drawPolyline(xs, ys, 3);
            } finally {
                g2.dispose();
            }
        }

        @Override
        public int getIconWidth() {
            return size;
        }

        @Override
        public int getIconHeight() {
            return size;
        }
    }

    /**
     * Returns the input data of all match tabs.
     *
     * @return one entry per tab
     */
    public List<ResultEntryController.MatchPanelData> getMatchPanelData() {
        List<ResultEntryController.MatchPanelData> data = new ArrayList<>();
        for (int i = 0; i < tabbedPane.getTabCount(); i++) {
            if (tabbedPane.getComponentAt(i) instanceof JPanel panel
                    && panel.getClientProperty("matchData") instanceof ResultEntryController.MatchPanelData d) {
                data.add(d);
            }
        }
        return data;
    }

    private void focusFirstField(int tabIndex) {
        if (tabbedPane.getComponentAt(tabIndex) instanceof JPanel panel
                && panel.getClientProperty("matchData") instanceof ResultEntryController.MatchPanelData d) {
            SwingUtilities.invokeLater(() -> d.setResultsFields()[0][0].requestFocusInWindow());
        }
    }

    /**
     * Builds the result-entry panel for a single match: per-set score fields and read-only
     * overall-result fields. The field references are stored as client property "matchData".
     *
     * @param match the match for which to build the entry panel
     * @return the configured match panel
     */
    private JPanel createMatchPanel(Match match) {
        JPanel matchPanel = new JPanel(new BorderLayout());
        matchPanel.setBackground(UITheme.BACKGROUND);
        matchPanel.setBorder(new EmptyBorder(12, 16, 12, 16));

        JTextField[][] setResultsFields = new JTextField[MAX_SETS][2];

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
        gridPanel.add(new JLabel(""), gbc);

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

        IntStream.range(0, MAX_SETS).forEach(i -> {
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

            addAutoFillBehavior(setResultsFields[i][0], setResultsFields[i][1]);
            addAutoFillBehavior(setResultsFields[i][1], setResultsFields[i][0]);
        });

        String[][] stored = match.getResults();
        IntStream.range(0, MAX_SETS)
                .filter(i -> stored[i] != null)
                .forEach(i -> {
                    setResultsFields[i][0].setText(stored[i][0]);
                    setResultsFields[i][1].setText(stored[i][1]);
                });

        // listeners after loading, so loading does not count as a change
        for (int i = 0; i < MAX_SETS; i++) {
            for (int j = 0; j < 2; j++) {
                JTextField field = setResultsFields[i][j];
                field.getDocument().addDocumentListener(new ResultDocumentListener(matchPanel));
                addKeyboardBehavior(field, i == MAX_SETS - 1 && j == 1);
            }
        }

        JSeparator sep2 = new JSeparator();
        sep2.setForeground(UITheme.BORDER_COLOR);
        gbc.gridx = 0; gbc.gridy = 7; gbc.gridwidth = 3; gbc.insets = new Insets(4, 6, 2, 6);
        gridPanel.add(sep2, gbc);
        gbc.gridwidth = 1; gbc.insets = new Insets(3, 6, 3, 6);

        JTextField wonSetsField = new JTextField();
        JTextField lostSetsField = new JTextField();
        for (JTextField total : new JTextField[]{wonSetsField, lostSetsField}) {
            total.setEditable(false);
            total.setFocusable(false); // Tab must not stop on the read-only totals
            total.setHorizontalAlignment(SwingConstants.CENTER);
            total.setFont(UITheme.FONT_SUBTITLE);
        }
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

        scoreCard.add(gridPanel, BorderLayout.CENTER);
        matchPanel.add(scoreCard, BorderLayout.CENTER);

        // walkover (Nichtantritt): fills 3 x 11:0 for the winner
        JPanel walkoverRow = new JPanel(new WrapLayout(FlowLayout.CENTER, 8, 6));
        walkoverRow.setBackground(UITheme.BACKGROUND);
        walkoverRow.add(new JLabel("Kampflos:"));
        for (boolean firstWins : new boolean[]{true, false}) {
            Player winner = firstWins ? match.getFirstPlayer() : match.getSecondPlayer();
            JButton button = UITheme.createSecondaryButton("Sieg " + winner.getLastName());
            button.setFocusable(false);
            button.setToolTipText("Nichtantritt: trägt 3 Sätze 11:0 für " + winner.getFullName()
                    + " ein (zählt als 3:0 mit 33:0 Bällen). Danach speichern.");
            button.addActionListener(_ -> fillWalkover(setResultsFields, firstWins));
            walkoverRow.add(button);
        }
        matchPanel.add(walkoverRow, BorderLayout.SOUTH);

        matchPanel.putClientProperty("matchData",
                new ResultEntryController.MatchPanelData(setResultsFields, wonSetsField, lostSetsField, match));

        return matchPanel;
    }

    /**
     * Fills a walkover result: three sets 11:0 for the winner, the remaining sets empty.
     *
     * @param fields    the score fields of the match
     * @param firstWins {@code true} if the first player wins
     */
    static void fillWalkover(JTextField[][] fields, boolean firstWins) {
        for (int i = 0; i < MAX_SETS; i++) {
            boolean played = i < model.ResultRules.SETS_TO_WIN;
            fields[i][0].setText(played ? (firstWins ? "11" : "0") : "");
            fields[i][1].setText(played ? (firstWins ? "0" : "11") : "");
        }
    }

    /**
     * Selects the field content on focus (so typing replaces a wrong value) and makes Enter move
     * to the next field; from the last field of a match Enter moves to the next match.
     */
    private void addKeyboardBehavior(JTextField field, boolean lastFieldOfMatch) {
        field.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                SwingUtilities.invokeLater(field::selectAll);
            }
        });
        field.addActionListener(_ -> {
            if (lastFieldOfMatch) {
                int next = tabbedPane.getSelectedIndex() + 1;
                if (next < tabbedPane.getTabCount()) {
                    tabbedPane.setSelectedIndex(next);
                    focusFirstField(next);
                }
            } else {
                field.transferFocus();
            }
        });
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
     *
     * @return the tabbed pane
     */
    public JTabbedPane getTabbedPane() {
        return tabbedPane;
    }

    /**
     * Attaches a focus listener to {@code source} that auto-fills {@code target} when focus
     * leaves {@code source}. Auto-fill rules: value 0–9 → target gets "11"; value 10 → target
     * gets "12"; value ≥ 11 → no auto-fill. Auto-fill only triggers when {@code target} is empty.
     */
    private static void addAutoFillBehavior(JTextField source, JTextField target) {
        source.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                if (!target.getText().isEmpty()) return;
                String text = source.getText();
                if (text.isEmpty()) return;
                try {
                    int value = Integer.parseInt(text);
                    if (value <= 9) {
                        target.setText("11");
                    } else if (value == 10) {
                        target.setText("12");
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        });
    }

    /**
     * Document listener that triggers an overall-result recalculation whenever a set score
     * field is modified.
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
