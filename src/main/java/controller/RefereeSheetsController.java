package controller;

import model.Match;
import view.RefereeSheetsView;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import java.util.List;

/**
 * Controller for the referee sheet (Schiedsrichterzettel) feature.
 *
 * <p>Builds the UI via {@link RefereeSheetsView} and provides the data and print logic
 * for generating one referee sheet per match. Each sheet displays the tournament name and
 * round, the table number, the two player names, five set rows, and a total-result row.</p>
 */
public class RefereeSheetsController {

    /** Number of sheet columns per printed page. */
    private static final int PRINT_COLUMNS = 2;
    /** Number of sheet rows per printed page. */
    private static final int PRINT_ROWS = 3;
    /** Number of sheets per printed page ({@value #PRINT_COLUMNS} x {@value #PRINT_ROWS}). */
    private static final int SHEETS_PER_PAGE = PRINT_COLUMNS * PRINT_ROWS;

    /** Labels of the rows below the player header on a printed sheet. */
    private static final String[] ROW_LABELS = {"Satz 1", "Satz 2", "Satz 3", "Satz 4", "Satz 5", "Gesamt"};

    /** Page margin for printing in points (half an inch), independent of the printer default. */
    private static final double MARGIN = 36;

    private final Window owner;
    private final List<Match> matches;
    private final String tournamentName;
    private final String roundLabel;
    private final int koRounds;
    private RefereeSheetsView view;
    private boolean closed;

    /**
     * Constructs the controller for the given list of matches and schedules UI initialization
     * on the Event Dispatch Thread.
     *
     * @param owner          the window owning the (modeless) referee sheets dialog; may be {@code null}
     * @param matches        the matches of the current round without bye matches (second player != null)
     * @param tournamentName the name of the tournament, printed on every sheet
     * @param round          the current round number, printed on every sheet
     */
    public RefereeSheetsController(java.awt.Window owner, List<Match> matches, String tournamentName, int round) {
        this(owner, matches, tournamentName, "Runde " + round, 0);
    }

    /**
     * Constructs the controller for the given list of matches and schedules UI initialization
     * on the Event Dispatch Thread.
     *
     * @param owner          the window owning the (modeless) referee sheets dialog; may be {@code null}
     * @param matches        the matches of the current round without bye matches (second player != null)
     * @param tournamentName the name of the tournament, printed on every sheet
     * @param roundLabel     the current round, e.g. "Runde 3" or "KO-Runde: Halbfinale"
     * @param koRounds       number of knockout rounds (names of knockout matches), 0 if not relevant
     */
    public RefereeSheetsController(java.awt.Window owner, List<Match> matches, String tournamentName,
                                   String roundLabel, int koRounds) {
        this.owner = owner;
        this.matches = List.copyOf(matches);
        this.tournamentName = tournamentName;
        this.roundLabel = roundLabel;
        this.koRounds = koRounds;
        SwingUtilities.invokeLater(this::initializeUI);
    }

    /**
     * Initializes and displays the {@link RefereeSheetsView}.
     */
    private void initializeUI() {
        if (closed) return;
        view = new RefereeSheetsView(owner, this, matches);
    }

    /**
     * Closes the window (e.g. because the pairings changed and the sheets are outdated).
     */
    public void close() {
        closed = true;
        if (view != null) view.dispose();
    }

    /**
     * Returns the heading of the window, e.g. {@code "Stadtmeisterschaft – Runde 3"}.
     * If no tournament name is set, only the round is returned.
     *
     * @return the heading consisting of tournament name and round
     */
    public String getHeaderText() {
        return withTournament(roundLabel);
    }

    /**
     * Returns the heading printed on the sheet of a match: tournament name and stage, e.g.
     * {@code "Stadtmeisterschaft – Gruppe B, Runde 2"}, {@code "... – Halbfinale"} or
     * {@code "... – Runde 3"}.
     *
     * @param match the match
     * @return the heading
     */
    public String getHeaderText(Match match) {
        return withTournament(stageOf(match));
    }

    /**
     * Returns the stage of a match: "Gruppe B, Runde 2", a knockout round name or the round.
     *
     * @param match the match
     * @return the stage
     */
    String stageOf(Match match) {
        String stage;
        if (match.getGroupNo() > 0) {
            stage = "Gruppe " + model.GroupKoState.groupName(match.getGroupNo())
                    + (match.getRound() > 0 ? ", Runde " + match.getRound() : "");
        } else if (match.isThirdPlace()) {
            stage = "Spiel um Platz 3";
        } else if (match.getKoRound() > 0 && koRounds > 0) {
            stage = GroupKnockout.roundName(match.getKoRound(), koRounds);
        } else {
            stage = roundLabel;
        }
        return stage;
    }

    private String withTournament(String stage) {
        if (tournamentName == null || tournamentName.isBlank()) {
            return stage;
        }
        return tournamentName.strip() + " – " + stage;
    }

    /**
     * Returns a short, unique label for the given match consisting of the table number and the
     * last names of both players, e.g. {@code "T1: Müller – Meier"}. Since tables can be used
     * more than once per round, the table number alone is not unique.
     *
     * @param match the match to label
     * @return the label for tabs and sheet titles
     */
    public String getSheetLabel(Match match) {
        return "T" + match.getTableNumber() + ": "
                + lastNameOf(match.getFirstPlayer()) + " – " + lastNameOf(match.getSecondPlayer());
    }

    /**
     * Returns the last name of the given player, falling back to the full name if no last name is set.
     *
     * @param player the player
     * @return the last name, or the full name as fallback
     */
    private static String lastNameOf(model.Player player) {
        if (player == null) {
            return "";
        }
        String lastName = player.getShortName();
        return lastName == null || lastName.isBlank() ? player.getFullName() : lastName;
    }

    /**
     * Creates a read-only {@link DefaultTableModel} for the given match's referee sheet.
     * The column headers carry the player names; the rows are the five sets plus the total.
     *
     * @param match the match to create the sheet for
     * @return the table model for the referee sheet
     */
    public DefaultTableModel createRefereeSheetTableModel(Match match) {
        String[] columnNames = {"", match.getFirstPlayer().getFullName(), match.getSecondPlayer().getFullName()};
        Object[][] data = {
                {"Satz 1", "", ""},
                {"Satz 2", "", ""},
                {"Satz 3", "", ""},
                {"Satz 4", "", ""},
                {"Satz 5", "", ""},
                {"Gesamt", "", ""}
        };
        return new DefaultTableModel(data, columnNames) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
    }

    /**
     * Opens the system print dialog and prints all referee sheets, fitting six sheets per page
     * (two columns, three rows). Each sheet carries the tournament name and round.
     * Shows an error dialog (owned by the view) if printing fails.
     */
    public void printRefereeSheets() {
        PrinterJob job = PrinterJob.getPrinterJob();
        job.setJobName("Schiedsrichterzettel");
        // fixed margins: the printer default (often one inch) would leave too little room for names
        java.awt.print.PageFormat format = job.defaultPage();
        java.awt.print.Paper paper = format.getPaper();
        paper.setImageableArea(MARGIN, MARGIN, paper.getWidth() - 2 * MARGIN, paper.getHeight() - 2 * MARGIN);
        format.setPaper(paper);

        job.setPrintable(printable(), job.validatePage(format));

        if (job.printDialog()) {
            try {
                job.print();
            } catch (PrinterException e) {
                JOptionPane.showMessageDialog(view, "Fehler beim Drucken der Schiedsrichterzettel.", "Druckfehler", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    /**
     * Returns the sheets as printable, six per page (used for printing and tests).
     *
     * @return the printable
     */
    Printable printable() {
        return (graphics, pageFormat, pageIndex) -> {
            int totalPages = (int) Math.ceil((double) matches.size() / SHEETS_PER_PAGE);

            if (pageIndex >= totalPages) {
                return Printable.NO_SUCH_PAGE;
            }

            Graphics2D g2 = (Graphics2D) graphics;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.translate(pageFormat.getImageableX(), pageFormat.getImageableY());

            int pageWidth = (int) pageFormat.getImageableWidth();
            int pageHeight = (int) pageFormat.getImageableHeight();

            int startIdx = pageIndex * SHEETS_PER_PAGE;
            int endIdx = Math.min(startIdx + SHEETS_PER_PAGE, matches.size());

            int hGap = 16;
            int vGap = 16;
            int cellWidth = (pageWidth - hGap * (PRINT_COLUMNS - 1)) / PRINT_COLUMNS;
            int cellHeight = (pageHeight - vGap * (PRINT_ROWS - 1)) / PRINT_ROWS;

            for (int i = startIdx; i < endIdx; i++) {
                int slot = i - startIdx;
                int col = slot % PRINT_COLUMNS;
                int row = slot / PRINT_COLUMNS;
                int xOffset = col * (cellWidth + hGap);
                int yOffset = row * (cellHeight + vGap);
                drawRefereeSheet(g2, matches.get(i), cellWidth, cellHeight, xOffset, yOffset);
            }

            return Printable.PAGE_EXISTS;
        };
    }

    /**
     * Draws a single referee sheet for the given match into the given cell.
     * The sheet includes a title bar with table number, tournament name and round, a header
     * with the player names, five set rows, and a highlighted total-result row.
     *
     * <p>All vertical sizes are derived from the available box height, so the rows never
     * extend beyond the sheet box, even in landscape orientation or with small margins.
     * Drawing is additionally clipped to the sheet box.</p>
     *
     * @param g2     the graphics context to draw into
     * @param match  the match whose data is rendered
     * @param width  the allocated width for this sheet in points
     * @param height the allocated height for this sheet in points
     * @param x      the horizontal offset (left edge) at which to draw the sheet
     * @param y      the vertical offset (top edge) at which to draw the sheet
     */
    private void drawRefereeSheet(Graphics2D g2, Match match, int width, int height, int x, int y) {
        final int innerPadding = 4;
        final int left = x + innerPadding;
        final int innerWidth = width - innerPadding * 2;
        final int boxHeight = height - innerPadding * 2;
        final int top = y + innerPadding;
        if (innerWidth <= 0 || boxHeight <= 0) {
            return;
        }

        // Distribute the box height: title bar 1.2 units, player header 1 unit, each row 1 unit.
        boolean twoLines = match.getFirstPlayer().isDoubles() || match.getSecondPlayer().isDoubles();
        double unit = boxHeight / (2.2 + ROW_LABELS.length);
        int titleBarHeight = (int) Math.min(24, Math.floor(unit * 1.2));
        int headerHeight = (int) (twoLines ? Math.min(32, Math.floor(unit * 1.2)) : Math.min(20, Math.floor(unit)));
        int rowHeight = (boxHeight - titleBarHeight - headerHeight - 2) / ROW_LABELS.length;
        rowHeight = Math.max(1, rowHeight);

        float baseSize = (float) Math.max(6, Math.min(10, rowHeight * 0.65));
        float titleSize = (float) Math.max(6, Math.min(12, titleBarHeight * 0.55));
        float headerTextSize = (float) Math.max(6, Math.min(10, headerHeight * (twoLines ? 0.34 : 0.6)));
        Font boldFont = new Font("Arial", Font.BOLD, 10).deriveFont(baseSize);
        Font plainFont = new Font("Arial", Font.PLAIN, 10).deriveFont(baseSize);
        Font headerFont = new Font("Arial", Font.BOLD, 10).deriveFont(headerTextSize);
        Font titleFont = new Font("Arial", Font.BOLD, 12).deriveFont(titleSize);
        Font tournamentFont = new Font("Arial", Font.PLAIN, 10).deriveFont(Math.max(6f, titleSize - 2f));

        Shape oldClip = g2.getClip();
        Stroke oldStroke = g2.getStroke();
        g2.clipRect(left, top, innerWidth + 1, boxHeight + 1);

        g2.setColor(new Color(230, 235, 245));
        g2.fillRect(left + 1, top + 1, innerWidth - 1, titleBarHeight - 1);
        g2.setColor(Color.BLACK);

        g2.setFont(titleFont);
        FontMetrics titleFm = g2.getFontMetrics();
        int titleBaseline = top + (titleBarHeight + titleFm.getAscent() - titleFm.getDescent()) / 2;
        String tableLabel = "Tisch " + match.getTableNumber();
        g2.drawString(tableLabel, left + 8, titleBaseline);
        int tableLabelEnd = left + 8 + titleFm.stringWidth(tableLabel);

        g2.setFont(tournamentFont);
        FontMetrics tournamentFm = g2.getFontMetrics();
        int maxHeaderWidth = left + innerWidth - 8 - (tableLabelEnd + 12);
        if (maxHeaderWidth > 0) {
            // if both do not fit, the stage is more important than the tournament name
            String header = getHeaderText(match);
            if (tournamentFm.stringWidth(header) > maxHeaderWidth) header = stageOf(match);
            header = truncate(header, tournamentFm, maxHeaderWidth);
            g2.drawString(header, left + innerWidth - 8 - tournamentFm.stringWidth(header), titleBaseline);
        }

        g2.setStroke(new BasicStroke(1f));
        g2.drawLine(left, top + titleBarHeight, left + innerWidth, top + titleBarHeight);

        int contentY = top + titleBarHeight + 1;
        // label column just wide enough for "Gesamt", so the names get the room
        int labelColWidth = Math.min(innerWidth / 4, g2.getFontMetrics(boldFont).stringWidth("Gesamt") + 14);
        int playerColWidth = (innerWidth - labelColWidth) / 2;
        int col1X = left + labelColWidth;
        int col2X = col1X + playerColWidth;

        g2.setColor(new Color(210, 220, 240));
        g2.fillRect(left + 1, contentY, innerWidth - 1, headerHeight);
        g2.setColor(Color.BLACK);

        drawNames(g2, nameLines(match.getFirstPlayer(), headerFont, g2, playerColWidth - 8), headerFont,
                col1X + 4, contentY, playerColWidth - 8, headerHeight);
        drawNames(g2, nameLines(match.getSecondPlayer(), headerFont, g2, playerColWidth - 8), headerFont,
                col2X + 4, contentY, playerColWidth - 8, headerHeight);

        g2.drawLine(left, contentY + headerHeight, left + innerWidth, contentY + headerHeight);

        int rowsStartY = contentY + headerHeight;

        for (int i = 0; i < ROW_LABELS.length; i++) {
            int rowY = rowsStartY + i * rowHeight;
            boolean isTotal = i == ROW_LABELS.length - 1;
            // the total row takes the rest of the box (no empty strip below it)
            int h = isTotal ? top + boxHeight - rowY : rowHeight;

            if (isTotal) {
                g2.setColor(new Color(232, 245, 233));
                g2.fillRect(left + 1, rowY, innerWidth - 1, h);
                g2.setColor(Color.BLACK);
                g2.setFont(boldFont);
            } else {
                g2.setColor(i % 2 == 0 ? Color.WHITE : new Color(248, 248, 252));
                g2.fillRect(left + 1, rowY, innerWidth - 1, h);
                g2.setColor(Color.BLACK);
                g2.setFont(plainFont);
            }

            FontMetrics rowFm = g2.getFontMetrics();
            int textY = rowY + (h + rowFm.getAscent() - rowFm.getDescent()) / 2;
            g2.drawString(truncate(ROW_LABELS[i], rowFm, labelColWidth - 8), left + 6, textY);

            if (!isTotal) {
                g2.setStroke(new BasicStroke(0.5f));
                g2.drawLine(left, rowY + h, left + innerWidth, rowY + h);
            }
        }

        int gridBottom = top + boxHeight;
        g2.setStroke(new BasicStroke(1f));
        g2.drawLine(col1X, contentY, col1X, gridBottom);
        g2.drawLine(col2X, contentY, col2X, gridBottom);

        g2.setClip(oldClip);
        g2.setStroke(new BasicStroke(1.5f));
        g2.setColor(Color.BLACK);
        g2.drawRect(left, top, innerWidth, boxHeight);
        g2.setStroke(oldStroke);
    }

    /**
     * Draws the names of a participant (one line, two for a pair), vertically centered. A name
     * that is too wide is drawn smaller (down to 6 pt) and only then shortened.
     */
    private void drawNames(Graphics2D g2, List<String> names, Font font, int x, int top, int maxWidth, int height) {
        Font fitted = font;
        for (float size = font.getSize2D(); size > 6f
                && (!fits(g2, fitted, names, maxWidth) || linesHeight(g2, fitted, names.size()) > height - 2); size -= 0.5f) {
            fitted = font.deriveFont(size - 0.5f);
        }
        g2.setFont(fitted);
        FontMetrics fm = g2.getFontMetrics();
        int lineHeight = fm.getAscent() + fm.getDescent();
        int y = top + (height - lineHeight * names.size()) / 2 + fm.getAscent();
        for (String name : names) {
            g2.drawString(truncate(name, fm, maxWidth), x, y);
            y += lineHeight;
        }
    }

    /**
     * Name lines of a participant: a pair on two lines; a single player on one line, or on two
     * (first name, last name) if the name does not fit at 8 pt.
     */
    private static List<String> nameLines(model.Player player, Font font, Graphics2D g2, int maxWidth) {
        if (player.isDoubles()) return player.getPersonNames();
        String full = player.getFullName();
        if (fits(g2, font.deriveFont(Math.min(font.getSize2D(), 8f)), List.of(full), maxWidth)
                || player.getFirstName().isBlank() || player.getLastName().isBlank()) {
            return List.of(full);
        }
        return List.of(player.getFirstName(), player.getLastName());
    }

    private static int linesHeight(Graphics2D g2, Font font, int lines) {
        FontMetrics fm = g2.getFontMetrics(font);
        return (fm.getAscent() + fm.getDescent()) * lines;
    }

    private static boolean fits(Graphics2D g2, Font font, List<String> names, int maxWidth) {
        FontMetrics fm = g2.getFontMetrics(font);
        return names.stream().allMatch(n -> fm.stringWidth(n) <= maxWidth);
    }

    /**
     * Truncates {@code text} to fit within {@code maxWidth} pixels as measured by {@code fm},
     * appending an ellipsis ({@code ...}) if truncation is necessary.
     *
     * @param text     the text to potentially truncate
     * @param fm       the font metrics used to measure string width
     * @param maxWidth the maximum allowed width in pixels
     * @return the original text if it fits, or a truncated version with {@code ...} appended
     */
    private String truncate(String text, FontMetrics fm, int maxWidth) {
        if (fm.stringWidth(text) <= maxWidth) return text;
        String ellipsis = "...";
        while (text.length() > 1 && fm.stringWidth(text + ellipsis) > maxWidth) {
            text = text.substring(0, text.length() - 1);
        }
        return text + ellipsis;
    }
}
