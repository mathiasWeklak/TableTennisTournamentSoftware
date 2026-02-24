package view;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class TournamentView extends JFrame {
    private final JList<String> playerJList;
    private final DefaultListModel<String> playerListModel;
    private final JTextField tournamentNameField;
    private final JTextField tableCountField;
    private final JCheckBox modusField;
    private final JButton addPlayerButton;
    private final JButton removePlayerButton;
    private final JButton beginTournamentButton;
    private final JMenuItem loadMenuItem;

    public TournamentView() {
        setTitle("Tischtennis Turniersoftware");
        setSize(780, 540);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setLayout(new BorderLayout());
        getContentPane().setBackground(UITheme.BACKGROUND);

        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(UITheme.PRIMARY);
        headerPanel.setBorder(new EmptyBorder(14, 20, 14, 20));
        JLabel headerLabel = new JLabel("Tischtennis Turniersoftware");
        headerLabel.setFont(UITheme.FONT_TITLE);
        headerLabel.setForeground(Color.WHITE);
        headerPanel.add(headerLabel, BorderLayout.WEST);

        JPanel formCard = new JPanel(new GridBagLayout());
        formCard.setBackground(UITheme.SURFACE);
        formCard.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, UITheme.BORDER_COLOR),
            new EmptyBorder(12, 20, 12, 20)
        ));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 8, 4, 8);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0; gbc.gridy = 0; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        formCard.add(makeLabel("Turniername:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        tournamentNameField = new JTextField(20);
        tournamentNameField.setFont(UITheme.FONT_BODY);
        formCard.add(tournamentNameField, gbc);

        gbc.gridx = 2; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        formCard.add(makeLabel("Anzahl Tische:"), gbc);
        gbc.gridx = 3;
        tableCountField = new JTextField(5);
        tableCountField.setFont(UITheme.FONT_BODY);
        formCard.add(tableCountField, gbc);

        gbc.gridx = 4;
        formCard.add(makeLabel("Jeder gegen Jeden:"), gbc);
        gbc.gridx = 5;
        modusField = new JCheckBox();
        formCard.add(modusField, gbc);

        JPanel playerCard = new JPanel(new BorderLayout(0, 6));
        playerCard.setBackground(UITheme.BACKGROUND);
        playerCard.setBorder(new EmptyBorder(12, 16, 12, 16));

        JPanel playerInner = new JPanel(new BorderLayout(0, 6));
        playerInner.setBackground(UITheme.SURFACE);
        playerInner.setBorder(UITheme.cardBorder("Spielerliste"));

        playerListModel = new DefaultListModel<>();
        playerJList = new JList<>(playerListModel);
        playerJList.setFont(UITheme.FONT_BODY);
        playerJList.setBackground(UITheme.SURFACE);
        playerJList.setSelectionBackground(new Color(187, 222, 251));

        JScrollPane playerScrollPane = new JScrollPane(playerJList);
        playerInner.add(playerScrollPane, BorderLayout.CENTER);

        JPanel playerBtns = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        playerBtns.setBackground(UITheme.SURFACE);
        addPlayerButton = UITheme.createPrimaryButton("+ Spieler hinzufügen");
        removePlayerButton = UITheme.createSecondaryButton("Spieler entfernen");
        playerBtns.add(addPlayerButton);
        playerBtns.add(removePlayerButton);
        playerInner.add(playerBtns, BorderLayout.SOUTH);
        playerCard.add(playerInner, BorderLayout.CENTER);

        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBackground(UITheme.BACKGROUND);
        mainPanel.add(formCard, BorderLayout.NORTH);
        mainPanel.add(playerCard, BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 14));
        bottomPanel.setBackground(UITheme.BACKGROUND);
        beginTournamentButton = UITheme.createPrimaryButton("Turnier beginnen und erste Runde auslosen");
        bottomPanel.add(beginTournamentButton);

        add(headerPanel, BorderLayout.NORTH);
        add(mainPanel, BorderLayout.CENTER);
        add(bottomPanel, BorderLayout.SOUTH);

        JMenuBar menuBar = new JMenuBar();
        JMenu fileMenu = new JMenu("Datei");
        fileMenu.setFont(UITheme.FONT_BODY);
        JMenuItem loadTournamentItem = new JMenuItem("Turnier laden");
        loadTournamentItem.setFont(UITheme.FONT_BODY);
        fileMenu.add(loadTournamentItem);
        menuBar.add(fileMenu);
        setJMenuBar(menuBar);
        this.loadMenuItem = loadTournamentItem;
    }

    private static JLabel makeLabel(String text) {
        JLabel lbl = new JLabel(text);
        lbl.setFont(UITheme.FONT_BODY);
        return lbl;
    }

    public JList<String> getPlayerJList() {
        return playerJList;
    }

    public DefaultListModel<String> getPlayerListModel() {
        return playerListModel;
    }

    public JTextField getTournamentNameField() {
        return tournamentNameField;
    }

    public JTextField getTableCountField() {
        return tableCountField;
    }

    public JCheckBox getModusField() {
        return modusField;
    }

    public JButton getAddPlayerButton() {
        return addPlayerButton;
    }

    public JButton getRemovePlayerButton() {
        return removePlayerButton;
    }

    public JButton getBeginTournamentButton() {
        return beginTournamentButton;
    }

    public JMenuItem getLoadMenuItem() {
        return loadMenuItem;
    }
}
