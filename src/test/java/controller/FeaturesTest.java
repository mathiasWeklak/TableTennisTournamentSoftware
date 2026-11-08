package controller;

import model.Match;
import model.Player;
import model.TournamentMode;
import org.junit.jupiter.api.Test;
import view.GroupTablesPanel;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * New functions of version 1.2: table queue, participant import/export, results in round 1,
 * unique tab titles and long placing lists.
 */
public class FeaturesTest {

    private static List<Player> players(int n) {
        List<Player> ps = new ArrayList<>();
        for (int i = 0; i < n; i++) ps.add(new Player("V" + i, "N" + i, "C", 2000 - i));
        return ps;
    }

    private static void win(Match m) {
        for (int i = 0; i < 3; i++) m.setResults(i, new String[]{"11", "5"});
        m.setOverallResult("3:0");
    }

    // --- table queue -----------------------------------------------------------------------

    @Test
    public void tableQueue_surplusMatchesWaitAndGetFreedTables() {
        PairingEngine engine = new PairingEngine(players(10), 3, TournamentMode.SWISS);
        engine.generateNextRound(1);
        List<Match> round = engine.getMatches();
        assertEquals(List.of(1, 2, 3, 0, 0), round.stream().map(Match::getTableNumber).toList());
        assertEquals("–", round.get(3).getTableLabel());

        assertTrue(engine.assignFreeTables().isEmpty(), "noch kein Tisch frei");
        win(round.get(1)); // table 2 becomes free
        List<Match> called = engine.assignFreeTables();
        assertEquals(List.of(round.get(3)), called);
        assertEquals(2, round.get(3).getTableNumber());
        assertEquals(0, round.get(4).getTableNumber());
        assertEquals(2, round.get(1).getTableNumber(), "das fertige Spiel behält seine Tischnummer");

        win(round.get(0));
        win(round.get(2));
        assertEquals(List.of(round.get(4)), engine.assignFreeTables());
        assertEquals(1, round.get(4).getTableNumber(), "kleinster freier Tisch");
    }

    @Test
    public void tableQueue_enoughTables_nobodyWaits() {
        PairingEngine engine = new PairingEngine(players(8), 4, TournamentMode.SWISS);
        engine.generateNextRound(1);
        assertTrue(engine.getMatches().stream().allMatch(m -> m.getTableNumber() >= 1));
    }

    @Test
    public void tableQueue_struckMatchFreesItsTable() {
        List<Player> ps = players(6);
        PairingEngine engine = new PairingEngine(ps, 2, TournamentMode.ROUND_ROBIN);
        engine.generateNextRound(1);
        Match waiting = engine.getMatches().stream().filter(m -> m.getTableNumber() == 0).findFirst().orElseThrow();
        Match onTable = engine.getMatches().stream().filter(m -> m.getTableNumber() == 1).findFirst().orElseThrow();
        engine.withdraw(onTable.getFirstPlayer()); // Round Robin: his matches are struck
        assertEquals(List.of(waiting), engine.assignFreeTables());
        assertEquals(1, waiting.getTableNumber());
    }

    @Test
    public void tableQueue_manualPairingAlsoQueues() {
        List<Player> ps = players(6);
        PairingEngine engine = new PairingEngine(ps, 1, TournamentMode.SWISS);
        engine.generateNextRound(1);
        engine.setNewMatches(List.of(new Match(ps.get(0), ps.get(1), -1), new Match(ps.get(2), ps.get(3), -1),
                new Match(ps.get(4), ps.get(5), -1)));
        assertEquals(List.of(1, 0, 0), engine.getMatches().stream().map(Match::getTableNumber).toList());
    }

    // --- results in round 1 ----------------------------------------------------------------

    @Test
    public void hasResultsInCurrentRound_alsoForPartialEntries() {
        PairingEngine engine = new PairingEngine(players(4), 2, TournamentMode.SWISS);
        engine.generateNextRound(1);
        assertFalse(engine.hasResultsInCurrentRound());
        engine.getMatches().getFirst().setResults(0, new String[]{"11", "7"});
        assertTrue(engine.hasResultsInCurrentRound());
    }

    // --- participant CSV -------------------------------------------------------------------

    @Test
    public void csv_headerWithQuotesAndBom() {
        String text = ParticipantCsv.decode(("﻿Vorname;Nachname;Verein;Q-TTR\r\n"
                + "Anna;Becker;\"TTC Blau; Weiß\";1650\r\n"
                + "Max;Müller;SV Eintracht;\r\n").getBytes(StandardCharsets.UTF_8));
        ParticipantCsv.ImportResult r = ParticipantCsv.parse(text, false, List.of());
        assertEquals(List.of(), r.errors());
        assertEquals(2, r.players().size());
        assertEquals("TTC Blau; Weiß", r.players().getFirst().getClub());
        assertEquals(1650, r.players().getFirst().getTtr());
        assertEquals(0, r.players().get(1).getTtr(), "leerer TTR = 0");
    }

    @Test
    public void csv_columnsByName_inAnyOrder() {
        String text = "Verein,TTR,Name,Vorname,Geburtsjahr\nTTC A,1500,Becker,Anna,1990\n";
        ParticipantCsv.ImportResult r = ParticipantCsv.parse(text, false, List.of());
        assertEquals("Anna Becker", r.players().getFirst().getFullName());
        assertEquals("TTC A", r.players().getFirst().getClub());
    }

    @Test
    public void csv_withoutHeader_positional() {
        ParticipantCsv.ImportResult r = ParticipantCsv.parse("Anna\tBecker\tTTC A\t1500\n", false, List.of());
        assertEquals(1, r.players().size());
        assertEquals(1500, r.players().getFirst().getTtr());
    }

    @Test
    public void csv_invalidLinesAreReportedWithLineNumber() {
        Player existing = new Player("Anna", "Becker", "TTC A", 1);
        String text = "Vorname;Nachname;Verein;TTR\nanna;becker;TTC B;1\nBen;Franke;;1\nCarl;Graf;TTC C;abc\nDora;Haas;TTC D;12\n";
        ParticipantCsv.ImportResult r = ParticipantCsv.parse(text, false, List.of(existing));
        assertEquals(1, r.players().size());
        assertEquals("Dora Haas", r.players().getFirst().getFullName());
        assertEquals(3, r.errors().size());
        assertTrue(r.errors().get(0).startsWith("Zeile 2:"), r.errors().get(0));
        assertTrue(r.errors().get(2).startsWith("Zeile 4:"), r.errors().get(2));
    }

    @Test
    public void csv_duplicateWithinTheFileIsRejected() {
        ParticipantCsv.ImportResult r = ParticipantCsv.parse("Anna;Becker;A;1\nAnna;Becker;B;2\n", false, List.of());
        assertEquals(1, r.players().size());
        assertEquals(1, r.errors().size());
    }

    @Test
    public void csv_doubles_andPersonInTwoPairs() {
        String text = "Vorname;Nachname;Verein;TTR;Vorname 2;Nachname 2;Verein 2;TTR 2\n"
                + "Anna;Becker;A;1500;Lena;Krause;B;1400\n"
                + "Lena;Krause;B;1400;Max;Müller;C;1300\n";
        ParticipantCsv.ImportResult r = ParticipantCsv.parse(text, true, List.of());
        assertEquals(1, r.players().size());
        assertTrue(r.players().getFirst().isDoubles());
        assertEquals(2900, r.players().getFirst().getTtr());
        assertTrue(r.errors().getFirst().contains("anderen Doppel"), r.errors().getFirst());
    }

    @Test
    public void csv_windows1252FromExcel() {
        byte[] bytes = "Vorname;Nachname;Verein;TTR\nJürgen;Größ;TSV Grün;1500\n".getBytes(Charset.forName("windows-1252"));
        ParticipantCsv.ImportResult r = ParticipantCsv.parse(ParticipantCsv.decode(bytes), false, List.of());
        assertEquals("Jürgen Größ", r.players().getFirst().getFullName());
        assertEquals("TSV Grün", r.players().getFirst().getClub());
    }

    @Test
    public void csv_formatAndParseAgain() {
        List<Player> singles = List.of(new Player("Anna", "Becker", "TTC \"A\"; Süd", 1500), new Player("Max", "Müller", "SV", 0));
        ParticipantCsv.ImportResult r = ParticipantCsv.parse(ParticipantCsv.format(singles), false, List.of());
        assertEquals(List.of(), r.errors());
        assertEquals(singles.getFirst().getClub(), r.players().getFirst().getClub());
        assertEquals(2, r.players().size());

        List<Player> pairs = List.of(Player.pair(new Player("Anna", "Becker", "A", 1500), new Player("Lena", "Krause", "B", 1400)));
        ParticipantCsv.ImportResult rp = ParticipantCsv.parse(ParticipantCsv.format(pairs), true, List.of());
        assertEquals(List.of(), rp.errors());
        assertEquals("B", rp.players().getFirst().getPartnerClub());
        assertEquals(1400, rp.players().getFirst().getPartnerTtr());
    }

    @Test
    public void csv_missingNameColumns() {
        ParticipantCsv.ImportResult r = ParticipantCsv.parse("Name;Verein\nAnna Becker;A\n", true, List.of());
        assertTrue(r.players().isEmpty());
        assertFalse(r.errors().isEmpty());
    }

    @Test
    public void csv_emptySeparatorLinesAreSkipped() {
        ParticipantCsv.ImportResult r = ParticipantCsv.parse("Vorname;Nachname;Verein;TTR\nAnna;Becker;A;1\n;;;\n ; ; ;\n", false, List.of());
        assertEquals(1, r.players().size());
        assertEquals(List.of(), r.errors());
    }

    @Test
    public void csv_synonymsCountTogetherForThePartner() {
        String text = "Vorname;Nachname;Verein;TTR;Vorname;Name;Verein;Q-TTR\nAnna;Becker;A;1500;Lena;Krause;B;1400\n";
        ParticipantCsv.ImportResult r = ParticipantCsv.parse(text, true, List.of());
        assertEquals(List.of(), r.errors());
        assertEquals(1400, r.players().getFirst().getPartnerTtr());
        assertEquals("Krause", r.players().getFirst().getPartnerLastName());
    }

    @Test
    public void csv_doublesFileWhileSinglesIsSelected() {
        String withHeader = "Vorname;Nachname;Verein;TTR;Vorname;Nachname;Verein;TTR\nAnna;Becker;A;1;Lena;Krause;B;2\n";
        ParticipantCsv.ImportResult r = ParticipantCsv.parse(withHeader, false, List.of());
        assertTrue(r.players().isEmpty());
        assertTrue(r.errors().getFirst().contains("Doppel"), r.errors().getFirst());
        assertTrue(ParticipantCsv.parse("Anna;Becker;A;1;Lena;Krause;B;2\n", false, List.of()).players().isEmpty());
    }

    @Test
    public void csv_ttrWithThousandsSeparatorAndNbsp() {
        ParticipantCsv.ImportResult r = ParticipantCsv.parse("Anna;Becker;A;1.234\nMax ;Müller;B; 1 567\n", false, List.of());
        assertEquals(List.of(), r.errors());
        assertEquals(1234, r.players().get(0).getTtr());
        assertEquals("Max", r.players().get(1).getFirstName());
        assertEquals(1567, r.players().get(1).getTtr());
    }

    @Test
    public void tableQueue_oldFilesWithCyclicTablesAreQueuedOnLoad() {
        List<Player> ps = players(10);
        List<Match> round = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            Match m = new Match(ps.get(2 * i), ps.get(2 * i + 1), i % 3 + 1); // old style: 1,2,3,1,2
            m.setPairingInfo(1, 0, 0);
            round.add(m);
        }
        PairingEngine engine = new PairingEngine(ps, 3, TournamentMode.SWISS);
        engine.restoreState(round, round);
        assertEquals(List.of(1, 2, 3, 0, 0), round.stream().map(Match::getTableNumber).toList());
        win(round.get(2));
        assertEquals(List.of(round.get(3)), engine.assignFreeTables());
        assertEquals(3, round.get(3).getTableNumber());
    }

    @Test
    public void pairingText_decidedMatchWithoutTableIsNotWaiting() {
        List<Player> ps = players(4);
        Match decided = new Match(ps.get(0), ps.get(1), 0);
        win(decided);
        Match waiting = new Match(ps.get(2), ps.get(3), 0);
        String text = TournamentRound.formatPairingsWithStatus(List.of(decided, waiting), TournamentMode.SWISS, 0);
        assertTrue(text.contains("Tisch  –: V0 N0"), text);
        assertTrue(text.contains("Wartet:   V2 N2"), text);
    }

    // --- tab titles, long placing lists ----------------------------------------------------

    @Test
    public void tabTitle_uniqueNamesAndWaitingTable() {
        Player a = new Player("Anna", "Müller", "C", 1);
        Player m = new Player("Max", "Müller", "C", 1);
        Player b = new Player("Ben", "Becker", "C", 1);
        Match match = new Match(a, b, 0);
        assertEquals("T–: A. Müller – Becker", ResultEntryController.tabTitle(match, Player.uniqueShortNames(List.of(a, m, b))));
        assertEquals("T–: Müller – Becker", ResultEntryController.tabTitle(match));
    }

    @Test
    public void longPlacingList_continuesOnFurtherPages() throws Exception {
        List<Object[]> rows = new ArrayList<>();
        for (int i = 1; i <= 70; i++) rows.add(new Object[]{i + ".", "Spieler " + i, "Verein"});
        Printable printable = GroupTablesPanel.printable(List.of(), rows, "Endstand", "Siegerliste: Test");
        PageFormat pf = new PageFormat();
        BufferedImage img = new BufferedImage((int) pf.getWidth(), (int) pf.getHeight(), BufferedImage.TYPE_INT_RGB);
        int pages = 0;
        while (pages < 10) {
            Graphics2D g = img.createGraphics();
            int r = printable.print(g, pf, pages);
            g.dispose();
            if (r == Printable.NO_SUCH_PAGE) break;
            pages++;
        }
        assertTrue(pages >= 2, "70 Zeilen passen nicht auf eine Seite: " + pages);
    }
}
