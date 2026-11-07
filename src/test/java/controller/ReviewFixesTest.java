package controller;

import model.Match;
import model.Player;
import model.TournamentMode;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the fixes of the second review: seeding with lots, single result rule, ITTF direct
 * comparison, minimum number of active players, checks over all rounds, Buchholz for unpaired
 * rounds and small helpers.
 */
public class ReviewFixesTest {

    private static Player player(String name, int ttr) {
        return new Player(name, name, "TTC", ttr);
    }

    private static Match withSets(Player a, Player b, int round, String[]... sets) {
        Match m = new Match(a, b, 1);
        for (int i = 0; i < sets.length; i++) m.setResults(i, sets[i]);
        m.setOverallResult(model.ResultRules.overallResult(m.getResults()));
        m.setPairingInfo(round, a.getPoints(), b.getPoints());
        return m;
    }

    private static final String[] W = {"11", "5"};
    private static final String[] L = {"5", "11"};

    // --- seeding ------------------------------------------------------------------------------

    @Test
    public void seed_sortsByTtrAndDrawsLotsForTies() {
        Player a = player("A", 1800), b = player("B", 0), c = player("C", 0), d = player("D", 0), e = player("E", 1500);
        List<Player> players = List.of(b, c, a, d, e);
        Set<List<Player>> orders = new HashSet<>();
        for (long r = 0; r < 30; r++) {
            List<Player> seeded = TournamentRound.seed(players, new Random(r));
            assertEquals(a, seeded.get(0));
            assertEquals(e, seeded.get(1));
            assertEquals(Set.of(b, c, d), new HashSet<>(seeded.subList(2, 5)));
            orders.add(seeded);
        }
        assertTrue(orders.size() > 1, "Gleichstand wird ausgelost");
    }

    @Test
    public void withoutTtr_byeGoesToLastSeedAndPairingFollowsSeeding() {
        List<Player> ps = new ArrayList<>();
        for (int i = 1; i <= 5; i++) ps.add(player("S" + i, 0));
        PairingEngine engine = new PairingEngine(ps, 3, TournamentMode.SWISS);
        engine.generatePairings(1);
        Map<Player, Player> opp = new HashMap<>();
        Player bye = null;
        for (Match m : engine.getMatches()) {
            if (m.getSecondPlayer() == null) bye = m.getFirstPlayer();
            else {
                opp.put(m.getFirstPlayer(), m.getSecondPlayer());
                opp.put(m.getSecondPlayer(), m.getFirstPlayer());
            }
        }
        assertEquals(ps.get(4), bye, "Freilos an den letzten Setzplatz");
        assertEquals(ps.get(2), opp.get(ps.get(0)), "1 gegen 3");
        assertEquals(ps.get(3), opp.get(ps.get(1)), "2 gegen 4");
    }

    @Test
    public void standings_tieOrderFollowsSeeding() {
        List<Player> ps = List.of(player("Z", 0), player("Y", 0), player("X", 0));
        List<Player> order = Standings.compute(ps, TournamentMode.SWISS, List.of()).stream()
                .map(Standings.Entry::player).toList();
        assertEquals(ps, order);
    }

    // --- single result rule ---------------------------------------------------------------------

    @Test
    public void isDecided_singleRule() {
        Player a = player("A", 1), b = player("B", 2);
        Match overallOnly = new Match(a, b, 1);
        overallOnly.setOverallResult("3:1");
        assertTrue(overallOnly.isDecided());
        assertArrayEquals(new int[]{3, 1}, overallOnly.decidedSets());

        Match complete = withSets(a, b, 1, W, L, W, W);
        assertTrue(complete.isDecided());
        assertArrayEquals(new int[]{3, 1}, complete.decidedSets());

        Match gap = withSets(a, b, 1, W, new String[]{"", ""}, W, W);
        assertEquals("3:0", gap.getOverallResult());
        assertFalse(gap.isDecided(), "Lücke: zählt nicht, obwohl das Gesamtergebnis 3:0 lautet");
        assertNull(gap.decidedSets());

        Match afterDecision = withSets(a, b, 1, W, W, W, L);
        assertFalse(afterDecision.isDecided());

        assertFalse(new Match(a, null, -1).isDecided());
    }

    @Test
    public void undecidedMatch_notInTableAndListedAsOpen() {
        Player a = player("A", 1), b = player("B", 2);
        Match gap = withSets(a, b, 2, W, new String[]{"", ""}, W, W);
        new ScoreCalculator(new ArrayList<>(List.of(gap))).calculate(List.of(a, b));
        assertEquals(0, a.getPoints());
        assertTrue(TournamentRound.hasUnfinishedMatches(List.of(gap)));
        assertTrue(TournamentRound.formatPairingsWithStatus(List.of(gap), TournamentMode.SWISS).contains("offen"));
        assertEquals(List.of("Runde 2, Tisch 1: A A – B B"), TournamentRound.unfinishedMatchDescriptions(List.of(gap)));
    }

    @Test
    public void unfinishedDescriptions_coverAllRounds() {
        Player a = player("A", 1), b = player("B", 2), c = player("C", 3), d = player("D", 4);
        Match round1Broken = withSets(a, b, 1, W, W);       // corrected later to an incomplete result
        Match round2Done = withSets(c, d, 2, W, W, W);
        List<String> open = TournamentRound.unfinishedMatchDescriptions(List.of(round1Broken, round2Done, new Match(a, null, -1)));
        assertEquals(1, open.size());
        assertTrue(open.getFirst().startsWith("Runde 1"));
    }

    @Test
    public void pairingText_twoDigitTables() {
        Player a = new Player("Anna", "Alpha", "TTC", 1), b = new Player("Ben", "Beta", "TTC", 2);
        String text = TournamentRound.formatPairingsWithStatus(List.of(new Match(a, b, 10), new Match(a, b, 3)), TournamentMode.SWISS);
        assertTrue(text.contains("Tisch 10: Anna Alpha"), text);
        assertTrue(text.contains("Tisch  3: Anna Alpha"), text);
    }

    // --- ITTF direct comparison ---------------------------------------------------------------

    @Test
    public void roundRobin_remainingTiedPlayersAreComparedAgainOnlyAmongThemselves() {
        Player a = player("A", 1000), b = player("B", 1000), c = player("C", 1000), d = player("D", 1000);
        String[] w9 = {"11", "9"};
        List<Match> ms = new ArrayList<>(List.of(
                withSets(a, b, 1, w9, w9, w9),                                  // A 3:0 B
                withSets(b, c, 2, w9, w9, new String[]{"0", "11"}, w9),         // B 3:1 C
                withSets(c, a, 3, new String[]{"5", "11"}, new String[]{"5", "11"}, w9, w9, w9), // C 3:2 A
                withSets(a, d, 4, w9, w9, w9), withSets(b, d, 5, w9, w9, w9), withSets(c, d, 6, w9, w9, w9)));
        List<Player> all = List.of(a, b, c, d);
        new ScoreCalculator(ms, TournamentMode.ROUND_ROBIN).calculate(all);
        // mini sets: A +2, B -1, C -1 -> A first; B and C again only among themselves: B beat C
        List<Standings.Entry> standings = Standings.compute(all, TournamentMode.ROUND_ROBIN, ms);
        assertEquals(List.of(a, b, c, d), standings.stream().map(Standings.Entry::player).toList());
        assertEquals(List.of(1, 2, 3, 4), standings.stream().map(Standings.Entry::rank).toList());
    }

    @Test
    public void roundRobin_unresolvableTieSharesRank() {
        Player a = player("A", 1000), b = player("B", 1000), c = player("C", 1000);
        // circle with identical scores: completely symmetric
        List<Match> ms = new ArrayList<>(List.of(
                withSets(a, b, 1, W, W, W), withSets(b, c, 2, W, W, W), withSets(c, a, 3, W, W, W)));
        new ScoreCalculator(ms, TournamentMode.ROUND_ROBIN).calculate(List.of(a, b, c));
        List<Standings.Entry> s = Standings.compute(List.of(a, b, c), TournamentMode.ROUND_ROBIN, ms);
        assertEquals(List.of(1, 1, 1), s.stream().map(Standings.Entry::rank).toList());
    }

    // --- minimum number of active players -------------------------------------------------------

    @Test
    public void swiss_fewerThanTwoActivePlayers_noRound() {
        List<Player> ps = new ArrayList<>();
        for (int i = 0; i < 4; i++) ps.add(player("P" + i, 100 * i));
        PairingEngine engine = new PairingEngine(ps, 2, TournamentMode.SWISS);
        engine.generatePairings(1);
        List<Match> round1 = new ArrayList<>(engine.getMatches());
        ps.subList(1, 4).forEach(engine::withdraw);
        assertNull(engine.generateNextRound(2));
        assertTrue(engine.isFinished());
        assertEquals(round1, engine.getMatches(), "Runde 1 bleibt erhalten");
    }

    @Test
    public void roundRobin_fewerThanTwoActivePlayers_noRound() {
        List<Player> ps = new ArrayList<>();
        for (int i = 0; i < 4; i++) ps.add(player("P" + i, 100 * i));
        PairingEngine engine = new PairingEngine(ps, 2, TournamentMode.ROUND_ROBIN);
        engine.generatePairings(1);
        ps.subList(0, 3).forEach(engine::withdraw);
        assertNull(engine.generateNextRound(2));
        assertTrue(engine.isFinished());
        assertEquals(3, engine.roundRobinRounds());
    }

    // --- Buchholz for unpaired rounds -----------------------------------------------------------

    @Test
    public void buchholz_unpairedCompletedRoundsCountAsVirtualOpponent() {
        Player a = player("A", 4), b = player("B", 3), c = player("C", 2), d = player("D", 1);
        List<Match> ms = new ArrayList<>();
        ms.add(withSets(a, b, 1, W, W, W));                 // round 1: only A-B (C and D registered late)
        ms.add(withSets(c, a, 2, W, W, W));                 // round 2
        ms.add(withSets(d, b, 2, W, W, W));
        Match pending = new Match(a, d, 1);                   // round 3 not complete yet
        pending.setPairingInfo(3, 1, 1);
        ms.add(pending);
        new ScoreCalculator(ms).calculate(List.of(a, b, c, d));

        assertEquals(1, a.getPoints());
        assertEquals(1, c.getPoints());
        assertEquals(1, d.getPoints());
        assertEquals(0 + 1, a.getBuchholz(), "A: B(0) + C(1)");
        assertEquals(1 + 1, c.getBuchholz(), "C: A(1) + virtueller Gegner Runde 1 (1)");
        assertEquals(0 + 1, d.getBuchholz(), "D: B(0) + virtueller Gegner Runde 1 (1)");
        assertEquals(1 + 1, b.getBuchholz(), "B: A(1) + D(1); Runde 3 ist nicht abgeschlossen");
    }

    @Test
    public void buchholz_roundRobinUnaffected() {
        Player a = player("A", 4), b = player("B", 3), c = player("C", 2);
        List<Match> ms = new ArrayList<>(List.of(withSets(a, b, 1, W, W, W)));
        new ScoreCalculator(ms, TournamentMode.ROUND_ROBIN).calculate(List.of(a, b, c));
        assertEquals(0, c.getBuchholz());
    }

    // --- withdrawal in Round Robin: matches are struck --------------------------------------------

    @Test
    public void roundRobin_matchesOfWithdrawnPlayerAreStruck() {
        Player a = player("A", 4), b = player("B", 3), c = player("C", 2);
        Match ab = withSets(a, b, 1, W, W, W);   // A beat B
        Match cb = withSets(c, b, 2, W, W, W);   // C beat B
        Match ac = withSets(a, c, 3, W, W, W);   // A beat C
        List<Match> ms = new ArrayList<>(List.of(ab, cb, ac));
        Set<Player> withdrawn = new HashSet<>();
        ScoreCalculator calc = new ScoreCalculator(ms, TournamentMode.ROUND_ROBIN);
        calc.setAnnulledPlayers(withdrawn);
        calc.calculate(List.of(a, b, c));
        assertEquals(2, a.getPoints());
        assertEquals(1, c.getPoints());

        withdrawn.add(b);   // live set, as used by TournamentRound
        calc.calculate(List.of(a, b, c));
        assertEquals(1, a.getPoints(), "Sieg gegen B gestrichen");
        assertEquals(0, c.getPoints(), "Sieg gegen B gestrichen");
        assertEquals(3, a.getSetsWon());
        assertEquals(0, b.getPoints() + b.getWins() + b.getLosses() + b.getSetsWon() + b.getSetsLost());

        assertEquals(List.of(ac), ScoreCalculator.countingMatches(ms, TournamentMode.ROUND_ROBIN, withdrawn));
    }

    @Test
    public void swiss_matchesOfWithdrawnPlayerKeepCounting() {
        Player a = player("A", 4), b = player("B", 3);
        List<Match> ms = new ArrayList<>(List.of(withSets(a, b, 1, W, W, W)));
        ScoreCalculator calc = new ScoreCalculator(ms, TournamentMode.SWISS);
        calc.setAnnulledPlayers(Set.of(b));
        calc.calculate(List.of(a, b));
        assertEquals(1, a.getPoints());
        assertSame(ms, ScoreCalculator.countingMatches(ms, TournamentMode.SWISS, Set.of(b)));
    }

    @Test
    public void roundRobin_struckOpenMatchDoesNotBlockNextRound() {
        Player a = player("A", 4), b = player("B", 3);
        Match open = new Match(a, b, 1);
        List<Match> counting = ScoreCalculator.countingMatches(List.of(open), TournamentMode.ROUND_ROBIN, Set.of(b));
        assertTrue(TournamentRound.unfinishedMatchDescriptions(counting).isEmpty());
    }

    // --- small helpers ---------------------------------------------------------------------------

    @Test
    public void storage_currentFileHasNoRoundNumber() {
        File dir = new File(".");
        assertEquals("Herbst_Cup.ser", TournamentStorage.currentFile(dir, "Herbst Cup").getName());
        assertEquals("Herbst_Cup_Runde_2.ser", TournamentStorage.saveFile(dir, "Herbst Cup", 2).getName());
    }

    @Test
    public void errorHandler_formatContainsStackTrace() {
        String text = ErrorHandler.format(new IllegalStateException("kaputt"));
        assertTrue(text.startsWith("=== "));
        assertTrue(text.contains("IllegalStateException: kaputt"));
        assertTrue(text.contains("ReviewFixesTest"));
    }
}
