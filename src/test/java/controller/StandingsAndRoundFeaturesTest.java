package controller;

import model.Match;
import model.Player;
import model.TournamentMode;
import model.TournamentState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Round Robin scoring (bye = 0 points, direct comparison), shared ranks, withdrawal, undo of the
 * last draw, end of tournament and the helpers behind the new UI functions.
 */
public class StandingsAndRoundFeaturesTest {

    @TempDir
    Path tempDir;

    private static Player player(String name, int ttr) {
        return new Player(name, name, "TTC", ttr);
    }

    /** A decided match with valid sets; {@code firstWins} decides the winner. */
    private static Match result(Player a, Player b, int loserSets, boolean firstWins) {
        Match m = new Match(a, b, 1);
        int i = 0;
        for (; i < loserSets; i++) m.setResults(i, firstWins ? new String[]{"9", "11"} : new String[]{"11", "9"});
        for (int k = 0; k < 3; k++, i++) m.setResults(i, firstWins ? new String[]{"11", "5"} : new String[]{"5", "11"});
        m.setOverallResult(firstWins ? "3:" + loserSets : loserSets + ":3");
        return m;
    }

    private static List<Player> order(List<Standings.Entry> standings) {
        return standings.stream().map(Standings.Entry::player).toList();
    }

    // --- Round Robin scoring and direct comparison -------------------------------------------

    @Test
    public void roundRobin_byeGivesNoPoint() {
        Player a = player("A", 1500);
        List<Match> matches = new ArrayList<>(List.of(new Match(a, null, -1)));
        new ScoreCalculator(matches, TournamentMode.ROUND_ROBIN).calculate(List.of(a));
        assertEquals(0, a.getPoints());
        new ScoreCalculator(matches, TournamentMode.SWISS).calculate(List.of(a));
        assertEquals(1, a.getPoints());
    }

    @Test
    public void roundRobin_directComparisonBeatsSetDifference() {
        Player a = player("A", 1500), b = player("B", 1600), c = player("C", 1400), d = player("D", 1300);
        // A beats B 3:2, but B has the better set difference overall
        List<Match> matches = new ArrayList<>(List.of(
                result(a, b, 2, true),
                result(c, a, 0, true),   // A loses 0:3 to C
                result(b, d, 0, true),   // B beats D 3:0
                result(b, c, 0, true),   // B beats C 3:0 -> B 2 wins
                result(a, d, 0, true),   // A beats D -> A 2 wins
                result(d, c, 0, true)));
        List<Player> players = List.of(a, b, c, d);
        new ScoreCalculator(matches, TournamentMode.ROUND_ROBIN).calculate(players);
        assertEquals(a.getPoints(), b.getPoints());
        assertTrue(b.getSetsWon() - b.getSetsLost() > a.getSetsWon() - a.getSetsLost());

        List<Standings.Entry> standings = Standings.compute(players, TournamentMode.ROUND_ROBIN, matches);
        assertEquals(a, standings.get(0).player(), "direkter Vergleich: A schlug B");
        assertEquals(b, standings.get(1).player());
        assertEquals(1, standings.get(0).rank());
        assertEquals(2, standings.get(1).rank());
    }

    @Test
    public void roundRobin_threeWayTieUsesMiniTable() {
        Player a = player("A", 1000), b = player("B", 1000), c = player("C", 1000), d = player("D", 1000);
        // A, B, C each beat one of the others (circle) and beat D; mini table decides by sets
        List<Match> matches = new ArrayList<>(List.of(
                result(a, b, 0, true),   // A 3:0 B
                result(b, c, 0, true),   // B 3:0 C
                result(c, a, 2, true),   // C 3:2 A
                result(a, d, 0, true), result(b, d, 0, true), result(c, d, 0, true)));
        List<Player> players = List.of(a, b, c, d);
        new ScoreCalculator(matches, TournamentMode.ROUND_ROBIN).calculate(players);
        // mini table: one win each; sets A +3-1 = +2, B -3+3 = 0, C -3+1 = -2 -> A, B, C
        List<Player> order = order(Standings.compute(players, TournamentMode.ROUND_ROBIN, matches));
        assertEquals(List.of(a, b, c, d), order);
    }

    @Test
    public void directComparison_ignoresMatchesAgainstPlayersWithOtherPoints() {
        Player a = player("A", 1000), b = player("B", 1000), c = player("C", 1000);
        List<Match> matches = new ArrayList<>(List.of(result(a, c, 0, true), result(b, c, 0, true)));
        new ScoreCalculator(matches, TournamentMode.ROUND_ROBIN).calculate(List.of(a, b, c));
        Map<Player, int[]> mini = Standings.directComparison(List.of(a, b, c), matches);
        assertArrayEquals(new int[]{0, 0, 0}, mini.get(a));
        assertArrayEquals(new int[]{0, 0, 0}, mini.get(b));
    }

    // --- shared ranks -----------------------------------------------------------------------

    @Test
    public void equalPlayersShareRank() {
        Player a = player("A", 1500), b = player("B", 1400), c = player("C", 1300);
        List<Standings.Entry> swiss = Standings.compute(List.of(c, a, b), TournamentMode.SWISS, List.of());
        assertEquals(List.of(1, 1, 1), swiss.stream().map(Standings.Entry::rank).toList());
        assertEquals(List.of(a, b, c), order(swiss), "Anzeige innerhalb des Rangs nach TTR");
    }

    @Test
    public void ranksSkipAfterTie() {
        Player a = player("A", 1500), b = player("B", 1400), c = player("C", 1300), d = player("D", 1200);
        a.setPoints(2);
        b.setPoints(1);
        c.setPoints(1);
        List<Standings.Entry> s = Standings.compute(List.of(a, b, c, d), TournamentMode.SWISS, List.of());
        assertEquals(List.of(1, 2, 2, 4), s.stream().map(Standings.Entry::rank).toList());
    }

    // --- engine: next round, withdrawal, undo -----------------------------------------------

    private static void enterAll(List<Match> matches) {
        for (Match m : matches) {
            if (m.getSecondPlayer() == null) continue;
            Match r = result(m.getFirstPlayer(), m.getSecondPlayer(), 1, true);
            for (int i = 0; i < 5; i++) m.setResults(i, r.getResults()[i]);
            m.setOverallResult(r.getOverallResult());
        }
    }

    @Test
    public void generateNextRound_keepsCurrentRoundWhenNoPairingPossible() {
        Player a = player("A", 1500), b = player("B", 1400);
        PairingEngine engine = new PairingEngine(new ArrayList<>(List.of(a, b)), 1, TournamentMode.SWISS);
        engine.generatePairings(1);
        enterAll(engine.getMatches());
        List<Match> round1 = new ArrayList<>(engine.getMatches());

        assertNull(engine.generateNextRound(2), "A und B haben schon gespielt");
        assertEquals(round1, engine.getMatches(), "Runde 1 bleibt sichtbar");
        assertEquals(1, engine.getAllMatches().size());
    }

    @Test
    public void withdrawnPlayer_isNotPairedInSwiss() {
        List<Player> ps = new ArrayList<>();
        for (int i = 0; i < 6; i++) ps.add(player("P" + i, 1500 - 10 * i));
        PairingEngine engine = new PairingEngine(ps, 3, TournamentMode.SWISS);
        engine.generatePairings(1);
        enterAll(engine.getMatches());
        new ScoreCalculator(engine.getAllMatches()).calculate(ps);

        engine.withdraw(ps.get(2));
        assertEquals(5, engine.activePlayers().size());
        assertNotNull(engine.generateNextRound(2));
        Set<Player> inRound = new HashSet<>();
        for (Match m : engine.getMatches()) {
            inRound.add(m.getFirstPlayer());
            if (m.getSecondPlayer() != null) inRound.add(m.getSecondPlayer());
        }
        assertFalse(inRound.contains(ps.get(2)));
        assertEquals(5, inRound.size());
        assertEquals(1, engine.getMatches().stream().filter(m -> m.getSecondPlayer() == null).count(),
                "5 aktive Spieler: ein Freilos");
    }

    @Test
    public void withdrawnPlayer_inRoundRobinOpponentIsSpielfrei() {
        List<Player> ps = new ArrayList<>();
        for (int i = 0; i < 4; i++) ps.add(player("P" + i, 1500 - 10 * i));
        PairingEngine engine = new PairingEngine(ps, 2, TournamentMode.ROUND_ROBIN);
        engine.generatePairings(1);
        engine.withdraw(ps.get(0));
        engine.generateNextRound(2);
        List<Match> round = engine.getMatches();
        assertTrue(round.stream().noneMatch(m -> ps.get(0).equals(m.getFirstPlayer()) || ps.get(0).equals(m.getSecondPlayer())));
        assertEquals(1, round.stream().filter(m -> m.getSecondPlayer() == null).count());
        assertEquals(1, round.stream().filter(m -> m.getSecondPlayer() != null).count());
    }

    @Test
    public void undoLastRound_restoresPreviousRound() {
        List<Player> ps = new ArrayList<>();
        for (int i = 0; i < 4; i++) ps.add(player("P" + i, 1500 - 10 * i));
        PairingEngine engine = new PairingEngine(ps, 2, TournamentMode.SWISS);
        engine.generatePairings(1);
        assertFalse(engine.canUndoLastRound(), "Runde 1 kann nicht zurückgenommen werden");
        enterAll(engine.getMatches());
        List<Match> round1 = new ArrayList<>(engine.getMatches());

        engine.generateNextRound(2);
        assertTrue(engine.canUndoLastRound());
        engine.undoLastRound();
        assertEquals(1, engine.getPairedRound());
        assertEquals(round1, engine.getMatches());
        assertEquals(2, engine.getAllMatches().size());

        // with a result entered, the draw can no longer be taken back
        engine.generateNextRound(2);
        Match first = engine.getMatches().getFirst();
        first.setResults(0, new String[]{"11", "3"});
        assertFalse(engine.canUndoLastRound());
        assertThrows(IllegalStateException.class, engine::undoLastRound);
    }

    // --- state, helpers -----------------------------------------------------------------------

    @Test
    public void tournamentState_oldConstructorAndRoundtripWithNewFields() throws Exception {
        Player a = player("A", 1500), b = player("B", 1400);
        TournamentState old = new TournamentState(List.of(a, b), List.of(), List.of(), 1, false, "T", 2, TournamentMode.SWISS);
        assertEquals(List.of(), old.withdrawnPlayers());
        assertEquals(0, old.plannedRounds());

        TournamentState full = new TournamentState(new ArrayList<>(List.of(a, b)), new ArrayList<>(), new ArrayList<>(),
                3, false, "T", 2, TournamentMode.SWISS, new ArrayList<>(List.of(b)), 7);
        var file = tempDir.resolve("t.ser").toFile();
        TournamentStorage.save(full, file);
        TournamentState loaded = TournamentStorage.load(file);
        assertEquals(7, loaded.plannedRounds());
        assertEquals(1, loaded.withdrawnPlayers().size());
        assertSame(loaded.playerList().get(1), loaded.withdrawnPlayers().getFirst());
    }

    @Test
    public void resultEntry_differsDetectsChanges() {
        String[][] stored = new String[5][2];
        stored[0] = new String[]{"11", "5"};
        String[][] same = new String[5][2];
        for (String[] row : same) Arrays.fill(row, "");
        same[0] = new String[]{"11 ", "5"};
        assertFalse(ResultEntryController.differs(same, stored), "leer und null sind gleich, Leerzeichen egal");

        String[][] changed = new String[5][2];
        for (String[] row : changed) Arrays.fill(row, "");
        changed[0] = new String[]{"11", "6"};
        assertTrue(ResultEntryController.differs(changed, stored));
    }

    @Test
    public void pairingsText_showsStatus() {
        Player a = player("A", 1500), b = player("B", 1400), c = player("C", 1300), d = player("D", 1200), e = player("E", 1100);
        Match done = result(a, b, 1, true);
        Match open = new Match(c, d, 2);
        String text = TournamentRound.formatPairingsWithStatus(List.of(done, open, new Match(e, null, -1)), TournamentMode.SWISS);
        assertTrue(text.contains("3:1"));
        assertTrue(text.contains("offen"));
        assertTrue(text.contains("Freilos"));
        assertTrue(text.contains("1 Begegnung(en) offen."));
        String rr = TournamentRound.formatPairingsWithStatus(List.of(new Match(e, null, -1)), TournamentMode.ROUND_ROBIN);
        assertTrue(rr.contains("spielfrei"));
        assertTrue(rr.contains("Alle Ergebnisse erfasst."));
    }

    @Test
    public void playerDialog_validation() {
        Player a = new Player("Anna", "Alpha", "TTC", 1500);
        assertNull(PlayerDialog.validate("Ben", "Beta", "TTC", "", List.of(a)));
        assertNull(PlayerDialog.validate("Ben", "Beta", "TTC", "1200", List.of(a)));
        assertNotNull(PlayerDialog.validate("", "Beta", "TTC", "", List.of(a)));
        assertNotNull(PlayerDialog.validate("Ben", "Beta", "TTC", "-5", List.of(a)));
        assertNotNull(PlayerDialog.validate("Ben", "Beta", "TTC", "abc", List.of(a)));
        assertNotNull(PlayerDialog.validate("anna", " ALPHA ", "Anderer Verein", "", List.of(a)), "gleicher Name");
        // editing a player: the list of others does not contain himself
        assertNull(PlayerDialog.validate("Anna", "Alpha", "Neuer Verein", "", List.of()));
    }

    @Test
    public void crossTable_reverseResult() {
        assertEquals("1:3", CrossTableController.reverseResult("3:1"));
        assertEquals("x", CrossTableController.reverseResult("x"));
    }
}
