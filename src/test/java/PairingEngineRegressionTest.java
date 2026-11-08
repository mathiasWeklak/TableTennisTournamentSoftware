import controller.PairingEngine;
import controller.ScoreCalculator;
import model.Match;
import model.Player;
import model.TournamentMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression and property tests for the pairing engine: bye handling, manual pairing changes,
 * table assignment and whole simulated tournaments.
 */
public class PairingEngineRegressionTest {

    private static List<Player> players(int n) {
        List<Player> list = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            list.add(new Player("Vorname" + i, "Nachname" + i, "Verein" + (i % 4), 1000 + 37 * i));
        }
        return list;
    }

    private static String key(Player a, Player b) {
        String x = a.getFullName();
        String y = b.getFullName();
        return x.compareTo(y) < 0 ? x + "|" + y : y + "|" + x;
    }

    private static void enterResults(List<Match> round, Random rnd) {
        for (Match m : round) {
            if (m.getSecondPlayer() == null) continue;
            boolean firstWins = rnd.nextBoolean();
            int loserSets = rnd.nextInt(3);
            List<String[]> sets = new ArrayList<>();
            for (int i = 0; i < loserSets; i++) sets.add(firstWins ? new String[]{"8", "11"} : new String[]{"11", "8"});
            for (int i = 0; i < 3; i++) sets.add(firstWins ? new String[]{"11", "6"} : new String[]{"6", "11"});
            for (int i = 0; i < sets.size(); i++) m.setResults(i, sets.get(i));
            m.setOverallResult(firstWins ? "3:" + loserSets : loserSets + ":3");
        }
    }

    private static Player byeOf(List<Match> round) {
        return round.stream().filter(m -> m.getSecondPlayer() == null).map(Match::getFirstPlayer).findFirst().orElse(null);
    }

    private static void assertValidRound(List<Player> all, List<Match> round, int tables) {
        Set<Player> seen = new HashSet<>();
        Set<Integer> usedTables = new HashSet<>();
        int normal = 0;
        for (Match m : round) {
            assertTrue(seen.add(m.getFirstPlayer()), "Spieler doppelt in Runde");
            if (m.getSecondPlayer() != null) {
                assertTrue(seen.add(m.getSecondPlayer()), "Spieler doppelt in Runde");
                // 0 = waits for a free table (more matches than tables)
                assertTrue(m.getTableNumber() >= 0 && m.getTableNumber() <= tables, "Tisch außerhalb 0.." + tables);
                if (m.getTableNumber() > 0) assertTrue(usedTables.add(m.getTableNumber()), "Tisch doppelt belegt");
                normal++;
            } else {
                assertEquals(-1, m.getTableNumber(), "Freilos darf keinen Tisch belegen");
            }
        }
        assertEquals(new HashSet<>(all), seen, "Jeder Spieler genau einmal pro Runde");
        assertEquals(all.size() / 2, normal);
    }

    // --- whole tournaments -------------------------------------------------------------------

    @ParameterizedTest
    @CsvSource({"6,5,3", "7,6,3", "8,7,4", "9,7,2", "12,9,6", "15,9,5", "16,9,8", "33,9,10"})
    public void swiss_noRematchesNoDoubleByesValidRounds(int n, int rounds, int tables) {
        for (long seed = 1; seed <= 5; seed++) {
            Random rnd = new Random(seed);
            List<Player> ps = players(n);
            PairingEngine engine = new PairingEngine(ps, tables, TournamentMode.SWISS);
            ScoreCalculator calc = new ScoreCalculator(engine.getAllMatches());

            Set<String> played = new HashSet<>();
            Map<Player, Integer> byes = new HashMap<>();
            for (int r = 1; r <= rounds; r++) {
                engine.clearCurrentRound();
                String text = engine.generatePairings(r);
                if (text == null) break;
                List<Match> round = engine.getMatches();
                assertValidRound(ps, round, tables);
                for (Match m : round) {
                    if (m.getSecondPlayer() == null) {
                        byes.merge(m.getFirstPlayer(), 1, Integer::sum);
                    } else {
                        assertTrue(played.add(key(m.getFirstPlayer(), m.getSecondPlayer())),
                                "Rematch in Runde " + r + " (n=" + n + ", seed=" + seed + ")");
                    }
                }
                enterResults(round, rnd);
                calc.calculate(ps);
            }
            byes.values().forEach(count -> assertEquals(1, count, "Doppeltes Freilos"));
        }
    }

    @Test
    public void swiss_largeFieldIsFast() {
        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
            Random rnd = new Random(7);
            List<Player> ps = players(101);
            PairingEngine engine = new PairingEngine(ps, 30, TournamentMode.SWISS);
            ScoreCalculator calc = new ScoreCalculator(engine.getAllMatches());
            for (int r = 1; r <= 11; r++) {
                engine.clearCurrentRound();
                assertNotNull(engine.generatePairings(r));
                enterResults(engine.getMatches(), rnd);
                calc.calculate(ps);
            }
        });
    }

    @Test
    public void swiss_nearlyExhaustedFieldIsFast() {
        assertTimeoutPreemptively(Duration.ofSeconds(20), () -> {
            Random rnd = new Random(3);
            List<Player> ps = players(14);
            PairingEngine engine = new PairingEngine(ps, 7, TournamentMode.SWISS);
            ScoreCalculator calc = new ScoreCalculator(engine.getAllMatches());
            for (int r = 1; r <= 13; r++) {
                engine.clearCurrentRound();
                if (engine.generatePairings(r) == null) break;
                enterResults(engine.getMatches(), rnd);
                calc.calculate(ps);
            }
        });
    }

    @ParameterizedTest
    @CsvSource({"4", "5", "8", "9"})
    public void roundRobin_everyPairExactlyOnce(int n) {
        List<Player> ps = players(n);
        PairingEngine engine = new PairingEngine(ps, 3, TournamentMode.ROUND_ROBIN);
        Set<String> played = new HashSet<>();
        Map<Player, Integer> byes = new HashMap<>();
        int round = 1;
        while (true) {
            engine.clearCurrentRound();
            if (engine.generatePairings(round) == null) break;
            assertValidRound(ps, engine.getMatches(), 3);
            for (Match m : engine.getMatches()) {
                if (m.getSecondPlayer() == null) {
                    byes.merge(m.getFirstPlayer(), 1, Integer::sum);
                } else {
                    assertTrue(played.add(key(m.getFirstPlayer(), m.getSecondPlayer())));
                }
            }
            round++;
        }
        assertEquals(n * (n - 1) / 2, played.size());
        assertEquals(n % 2 == 0 ? n - 1 : n, round - 1);
        assertTrue(engine.isFinished());
        if (n % 2 != 0) {
            assertEquals(n, byes.size());
            byes.values().forEach(c -> assertEquals(1, c));
        }
    }

    // --- bye selection -----------------------------------------------------------------------

    @Test
    public void swiss_byeGoesToNextCandidateWhenLowestCannotBeBye() {
        // A has already played B, C and D. If E (lowest) got the bye, A would have no opponent.
        Player a = new Player("A", "A", "X", 1600);
        Player b = new Player("B", "B", "X", 1500);
        Player c = new Player("C", "C", "X", 1400);
        Player d = new Player("D", "D", "X", 1300);
        Player e = new Player("E", "E", "X", 1200);
        List<Player> ps = List.of(a, b, c, d, e);

        List<Match> history = new ArrayList<>();
        for (Player opp : List.of(b, c, d)) {
            Match m = new Match(a, opp, 1);
            m.setOverallResult("3:0");
            history.add(m);
        }
        PairingEngine engine = new PairingEngine(ps, 2, TournamentMode.SWISS);
        engine.restoreState(history, List.of());
        new ScoreCalculator(engine.getAllMatches()).calculate(ps);

        engine.generatePairings(4);
        List<Match> round = engine.getMatches();
        assertValidRound(ps, round, 2);
        assertEquals(d, byeOf(round), "Nächstschwächerer Spieler ohne Freilos bekommt das Freilos");
        assertTrue(round.stream().anyMatch(m -> m.getSecondPlayer() != null
                && Set.of(m.getFirstPlayer(), m.getSecondPlayer()).equals(Set.of(a, e))));
    }

    @Test
    public void swiss_manualByeChangeIsRespectedInFollowingRounds() {
        List<Player> ps = players(5);
        PairingEngine engine = new PairingEngine(ps, 2, TournamentMode.SWISS);
        ScoreCalculator calc = new ScoreCalculator(engine.getAllMatches());
        Random rnd = new Random(11);

        engine.generatePairings(1);
        Player autoBye = byeOf(engine.getMatches());
        Player manualBye = ps.stream().filter(p -> !p.equals(autoBye)).findFirst().orElseThrow();

        // manual pairing: give the bye to someone else
        List<Player> rest = new ArrayList<>(ps);
        rest.remove(manualBye);
        engine.setNewMatches(List.of(new Match(manualBye, null, -1),
                new Match(rest.get(0), rest.get(1), -1), new Match(rest.get(2), rest.get(3), -1)));
        assertEquals(manualBye, byeOf(engine.getMatches()));
        assertEquals(List.of(manualBye), engine.getPlayersWithBye(engine.getAllMatches()));

        enterResults(engine.getMatches(), rnd);
        calc.calculate(ps);

        Map<Player, Integer> byes = new HashMap<>();
        byes.put(manualBye, 1);
        for (int r = 2; r <= 5; r++) {
            engine.clearCurrentRound();
            if (engine.generatePairings(r) == null) break;
            Player bye = byeOf(engine.getMatches());
            assertNotNull(bye);
            assertNull(byes.put(bye, 1), "Spieler " + bye + " bekommt ein zweites Freilos in Runde " + r);
            enterResults(engine.getMatches(), rnd);
            calc.calculate(ps);
        }
    }

    @Test
    public void swiss_playerWhoseByeWasRemovedManuallyIsEligibleAgain() {
        List<Player> ps = players(5); // ascending TTR: player 0 is lowest and gets the automatic bye
        PairingEngine engine = new PairingEngine(ps, 2, TournamentMode.SWISS);
        ScoreCalculator calc = new ScoreCalculator(engine.getAllMatches());
        engine.generatePairings(1);
        Player p0 = ps.get(0);
        assertEquals(p0, byeOf(engine.getMatches()));

        // manual change: bye to player 1; player 0 plays and loses clearly
        Match lostClearly = new Match(ps.get(2), p0, -1);
        Match close = new Match(ps.get(3), ps.get(4), -1);
        engine.setNewMatches(List.of(new Match(ps.get(1), null, -1), lostClearly, close));
        for (int i = 0; i < 3; i++) lostClearly.setResults(i, new String[]{"11", "0"});
        lostClearly.setOverallResult("3:0");
        String[][] closeSets = {{"11", "9"}, {"9", "11"}, {"11", "9"}, {"9", "11"}, {"11", "9"}};
        for (int i = 0; i < 5; i++) close.setResults(i, closeSets[i]);
        close.setOverallResult("3:2");
        calc.calculate(ps);

        engine.clearCurrentRound();
        engine.generatePairings(2);
        assertEquals(p0, byeOf(engine.getMatches()),
                "Spieler 0 hatte nach der manuellen Änderung kein Freilos und ist jetzt Letzter");
    }

    // --- manual pairing changes --------------------------------------------------------------

    @Test
    public void setNewMatches_keepsEarlierMatchWithSamePlayers() {
        List<Player> ps = players(4);
        PairingEngine engine = new PairingEngine(ps, 2, TournamentMode.SWISS);
        engine.generatePairings(1);
        List<Match> round1 = new ArrayList<>(engine.getMatches());
        enterResults(round1, new Random(1));

        engine.clearCurrentRound();
        engine.generatePairings(2);

        // manual change that repeats a round-1 pairing, then a second manual change
        Match r1 = round1.getFirst();
        List<Player> others = new ArrayList<>(ps);
        others.remove(r1.getFirstPlayer());
        others.remove(r1.getSecondPlayer());
        engine.setNewMatches(List.of(new Match(r1.getFirstPlayer(), r1.getSecondPlayer(), -1),
                new Match(others.get(0), others.get(1), -1)));
        engine.setNewMatches(List.of(new Match(ps.get(0), ps.get(3), -1), new Match(ps.get(1), ps.get(2), -1)));

        for (Match m : round1) {
            assertTrue(engine.getAllMatches().stream().anyMatch(x -> x == m), "Spiel aus Runde 1 wurde entfernt");
        }
        assertEquals(4, engine.getAllMatches().size());
    }

    @Test
    public void setNewMatches_assignsTablesOnlyToRealMatches() {
        List<Player> ps = players(5);
        PairingEngine engine = new PairingEngine(ps, 4, TournamentMode.SWISS);
        engine.generatePairings(1);
        engine.setNewMatches(List.of(new Match(ps.get(4), null, 7),
                new Match(ps.get(0), ps.get(1), -1), new Match(ps.get(2), ps.get(3), -1)));
        assertValidRound(ps, engine.getMatches(), 4);
        List<Integer> tables = engine.getMatches().stream()
                .filter(m -> m.getSecondPlayer() != null).map(Match::getTableNumber).toList();
        assertEquals(List.of(1, 2), tables);
    }

    @Test
    public void calculateAllPossibleOpenMatches_excludesPlayedAndUsedByes() {
        List<Player> ps = players(5);
        PairingEngine engine = new PairingEngine(ps, 2, TournamentMode.SWISS);
        engine.generatePairings(1);
        enterResults(engine.getMatches(), new Random(2));
        List<Match> round1 = new ArrayList<>(engine.getMatches());
        engine.clearCurrentRound();
        engine.generatePairings(2);

        List<Match> open = engine.calculateAllPossibleOpenMatches();
        Set<String> openKeys = open.stream().filter(m -> m.getSecondPlayer() != null)
                .map(m -> key(m.getFirstPlayer(), m.getSecondPlayer())).collect(Collectors.toSet());
        for (Match m : round1) {
            if (m.getSecondPlayer() != null) {
                assertFalse(openKeys.contains(key(m.getFirstPlayer(), m.getSecondPlayer())));
            } else {
                Player hadBye = m.getFirstPlayer();
                assertTrue(open.stream().noneMatch(x -> x.getSecondPlayer() == null && x.getFirstPlayer().equals(hadBye)));
            }
        }
        // current round (round 2) pairings are still offered, so they can be re-selected
        for (Match m : engine.getMatches()) {
            if (m.getSecondPlayer() != null) {
                assertTrue(openKeys.contains(key(m.getFirstPlayer(), m.getSecondPlayer())));
            }
        }
    }
}
