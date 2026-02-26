import controller.PairingEngine;
import model.Match;
import model.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class PairingEngineTest {

    private Player p1, p2, p3, p4;

    @BeforeEach
    public void setUp() {
        p1 = new Player("Alice", "A", "ClubA", 1500);
        p2 = new Player("Bob", "B", "ClubB", 1600);
        p3 = new Player("Carol", "C", "ClubC", 1400);
        p4 = new Player("Dave", "D", "ClubD", 1300);
    }

    @Test
    public void testInitialState_matchesAndAllMatchesAreEmpty() {
        PairingEngine engine = new PairingEngine(List.of(p1, p2), 1, false);
        assertTrue(engine.getMatches().isEmpty());
        assertTrue(engine.getAllMatches().isEmpty());
    }

    @Test
    public void testInitialState_isNotFinished() {
        PairingEngine engine = new PairingEngine(List.of(p1, p2), 1, false);
        assertFalse(engine.isFinished());
    }

    @Test
    public void testSetFinished_toTrue() {
        PairingEngine engine = new PairingEngine(List.of(p1, p2), 1, false);
        engine.setFinished(true);
        assertTrue(engine.isFinished());
    }

    @Test
    public void testSetFinished_backToFalse() {
        PairingEngine engine = new PairingEngine(List.of(p1, p2), 1, false);
        engine.setFinished(true);
        engine.setFinished(false);
        assertFalse(engine.isFinished());
    }

    @Test
    public void testClearCurrentRound_removesCurrentMatches() {
        PairingEngine engine = new PairingEngine(List.of(p1, p2), 1, false);
        engine.generatePairings(new HashSet<>(), 1);
        assertFalse(engine.getMatches().isEmpty());
        engine.clearCurrentRound();
        assertTrue(engine.getMatches().isEmpty());
    }

    @Test
    public void testClearCurrentRound_doesNotRemoveAllMatches() {
        PairingEngine engine = new PairingEngine(List.of(p1, p2), 1, false);
        engine.generatePairings(new HashSet<>(), 1);
        int allMatchesSizeBefore = engine.getAllMatches().size();
        engine.clearCurrentRound();
        assertEquals(allMatchesSizeBefore, engine.getAllMatches().size());
    }

    @Test
    public void testGetPlayersWithBye_noByeMatches_returnsEmptyList() {
        PairingEngine engine = new PairingEngine(List.of(p1, p2), 1, false);
        List<Match> matches = List.of(new Match(p1, p2, 1));
        assertTrue(engine.getPlayersWithBye(matches).isEmpty());
    }

    @Test
    public void testGetPlayersWithBye_withByeMatch_returnsPlayer() {
        PairingEngine engine = new PairingEngine(List.of(p1, p2), 1, false);
        List<Match> matches = List.of(new Match(p1, null, -1));
        List<Player> byePlayers = engine.getPlayersWithBye(matches);
        assertEquals(1, byePlayers.size());
        assertTrue(byePlayers.contains(p1));
    }

    @Test
    public void testGetPlayersWithBye_multipleByes_samePlayer_countedOnce() {
        PairingEngine engine = new PairingEngine(List.of(p1, p2), 1, false);
        List<Match> matches = List.of(new Match(p1, null, -1), new Match(p1, null, -1));
        List<Player> byePlayers = engine.getPlayersWithBye(matches);
        assertEquals(1, byePlayers.size());
    }

    @Test
    public void testGetPlayersWithBye_differentByePlayers_returnsAll() {
        PairingEngine engine = new PairingEngine(List.of(p1, p2, p3), 1, false);
        List<Match> matches = List.of(new Match(p1, null, -1), new Match(p2, null, -1));
        List<Player> byePlayers = engine.getPlayersWithBye(matches);
        assertEquals(2, byePlayers.size());
    }

    @Test
    public void testGenerateAllPairings_twoPlayers_returnsOnePairing() {
        PairingEngine engine = new PairingEngine(List.of(p1, p2), 1, false);
        List<Match> pairings = engine.generateAllPairings(List.of(p1, p2));
        assertEquals(1, pairings.size());
        assertNotNull(pairings.getFirst().getFirstPlayer());
        assertNotNull(pairings.getFirst().getSecondPlayer());
    }

    @Test
    public void testGenerateAllPairings_fourPlayers_returnsSixPairings() {
        PairingEngine engine = new PairingEngine(List.of(p1, p2, p3, p4), 2, false);
        List<Match> pairings = engine.generateAllPairings(List.of(p1, p2, p3, p4));
        assertEquals(6, pairings.size());
    }

    @Test
    public void testGenerateAllPairings_oddPlayers_includesByeMatches() {
        PairingEngine engine = new PairingEngine(List.of(p1, p2, p3), 1, false);
        List<Match> pairings = engine.generateAllPairings(List.of(p1, p2, p3));
        long byeCount = pairings.stream().filter(m -> m.getSecondPlayer() == null).count();
        long regularCount = pairings.stream().filter(m -> m.getSecondPlayer() != null).count();
        assertEquals(3, byeCount);
        assertEquals(3, regularCount);
    }

    @Test
    public void testGenerateAllPairings_emptyList_returnsEmptyList() {
        PairingEngine engine = new PairingEngine(new ArrayList<>(), 1, false);
        assertTrue(engine.generateAllPairings(new ArrayList<>()).isEmpty());
    }

    @Test
    public void testGenerateAllPairings_noDuplicatePairings() {
        PairingEngine engine = new PairingEngine(List.of(p1, p2, p3, p4), 2, false);
        List<Match> pairings = engine.generateAllPairings(List.of(p1, p2, p3, p4));
        Set<Match> unique = new HashSet<>(pairings);
        assertEquals(pairings.size(), unique.size());
    }

    @Test
    public void testCalculatePairingDifference_nothingPlayed_returnsAll() {
        PairingEngine engine = new PairingEngine(List.of(p1, p2), 1, false);
        List<Match> all = engine.generateAllPairings(List.of(p1, p2));
        List<Match> remaining = engine.calculatePairingDifference(all, new ArrayList<>(), new ArrayList<>());
        assertEquals(1, remaining.size());
    }

    @Test
    public void testCalculatePairingDifference_matchAlreadyPlayed_excluded() {
        PairingEngine engine = new PairingEngine(List.of(p1, p2), 1, false);
        List<Match> all = engine.generateAllPairings(List.of(p1, p2));
        List<Match> remaining = engine.calculatePairingDifference(all, List.of(new Match(p1, p2, 1)), new ArrayList<>());
        assertTrue(remaining.isEmpty());
    }

    @Test
    public void testCalculatePairingDifference_reverseOrderAlreadyPlayed_excluded() {
        PairingEngine engine = new PairingEngine(List.of(p1, p2), 1, false);
        List<Match> all = engine.generateAllPairings(List.of(p1, p2));
        List<Match> remaining = engine.calculatePairingDifference(all, List.of(new Match(p2, p1, 1)), new ArrayList<>());
        assertTrue(remaining.isEmpty());
    }

    @Test
    public void testCalculatePairingDifference_byePlayerAsOpponent_notExcluded() {
        PairingEngine engine = new PairingEngine(List.of(p1, p2), 1, false);
        List<Match> all = List.of(new Match(p1, p2, -1));
        List<Match> remaining = engine.calculatePairingDifference(all, new ArrayList<>(), List.of(p2));
        assertEquals(1, remaining.size());
    }

    @Test
    public void testCalculatePairingDifference_byeMatchForAlreadyByePlayer_excluded() {
        PairingEngine engine = new PairingEngine(List.of(p1, p2), 1, false);
        List<Match> all = List.of(new Match(p1, null, -1));
        List<Match> remaining = engine.calculatePairingDifference(all, new ArrayList<>(), List.of(p1));
        assertTrue(remaining.isEmpty());
    }

    @Test
    public void testSelectUniquePlayerMatches_twoPlayers_returnsMatch() {
        PairingEngine engine = new PairingEngine(List.of(p1, p2), 1, false);
        List<Match> matchList = new ArrayList<>(List.of(new Match(p1, p2, -1)));
        List<Match> result = engine.selectUniquePlayerMatches(matchList, new ArrayList<>(List.of(1)));
        assertNotNull(result);
        assertEquals(1, result.size());
    }

    @Test
    public void testSelectUniquePlayerMatches_tableAssigned() {
        PairingEngine engine = new PairingEngine(List.of(p1, p2), 1, false);
        List<Match> matchList = new ArrayList<>(List.of(new Match(p1, p2, -1)));
        List<Match> result = engine.selectUniquePlayerMatches(matchList, new ArrayList<>(List.of(5)));
        assertNotNull(result);
        assertEquals(5, result.getFirst().getTableNumber());
    }

    @Test
    public void testSelectUniquePlayerMatches_notAllPlayersCovered_returnsNull() {
        PairingEngine engine = new PairingEngine(List.of(p1, p2, p3), 1, false);
        List<Match> matchList = new ArrayList<>(List.of(new Match(p1, p2, -1)));
        List<Match> result = engine.selectUniquePlayerMatches(matchList, new ArrayList<>(List.of(1)));
        assertNull(result);
    }

    @Test
    public void testSelectUniquePlayerMatches_oddPlayers_includesByeMatch() {
        PairingEngine engine = new PairingEngine(List.of(p1, p2, p3), 1, false);
        List<Match> matchList = new ArrayList<>(List.of(
                new Match(p1, p2, -1),
                new Match(p3, null, -1)
        ));
        List<Match> result = engine.selectUniquePlayerMatches(matchList, new ArrayList<>(List.of(1)));
        assertNotNull(result);
        assertEquals(2, result.size());
        long byeCount = result.stream().filter(m -> m.getSecondPlayer() == null).count();
        assertEquals(1, byeCount);
    }

    @Test
    public void testRestoreState_replacesMatchesAndAllMatches() {
        PairingEngine engine = new PairingEngine(List.of(p1, p2), 1, false);
        Match m1 = new Match(p1, p2, 1);
        engine.restoreState(List.of(m1), List.of(m1));
        assertEquals(1, engine.getAllMatches().size());
        assertEquals(1, engine.getMatches().size());
    }

    @Test
    public void testRestoreState_overwritesPreviousState() {
        PairingEngine engine = new PairingEngine(List.of(p1, p2), 1, false);
        engine.generatePairings(new HashSet<>(), 1);
        Match restored = new Match(p1, p2, 2);
        engine.restoreState(List.of(restored), List.of(restored));
        assertEquals(1, engine.getAllMatches().size());
        assertEquals(1, engine.getMatches().size());
        assertEquals(2, engine.getMatches().getFirst().getTableNumber());
    }

    @Test
    public void testRestoreState_byeListDerivedFromSavedAllMatches() {
        PairingEngine engine = new PairingEngine(List.of(p1, p2, p3), 1, false);
        Match bye = new Match(p1, null, -1);
        Match regular = new Match(p2, p3, 1);
        engine.restoreState(List.of(bye, regular), List.of(bye, regular));
        assertEquals(2, engine.getAllMatches().size());
    }

    @Test
    public void testGeneratePairings_swissSystem_evenPlayers_generatesMatch() {
        PairingEngine engine = new PairingEngine(List.of(p1, p2), 1, false);
        String text = engine.generatePairings(new HashSet<>(), 1);
        assertNotNull(text);
        assertEquals(1, engine.getMatches().size());
        assertEquals(1, engine.getAllMatches().size());
    }

    @Test
    public void testGeneratePairings_swissSystem_resultTextContainsVs() {
        PairingEngine engine = new PairingEngine(List.of(p1, p2), 1, false);
        String text = engine.generatePairings(new HashSet<>(), 1);
        assertNotNull(text);
        assertTrue(text.contains("vs."));
    }

    @Test
    public void testGeneratePairings_swissSystem_oddPlayers_generatesOneByeAndOneMatch() {
        PairingEngine engine = new PairingEngine(List.of(p1, p2, p3), 2, false);
        String text = engine.generatePairings(new HashSet<>(), 1);
        assertNotNull(text);
        assertEquals(2, engine.getMatches().size());
        long byeCount = engine.getMatches().stream().filter(m -> m.getSecondPlayer() == null).count();
        assertEquals(1, byeCount);
    }

    @Test
    public void testGeneratePairings_swissSystem_oddPlayers_byeTextPresent() {
        PairingEngine engine = new PairingEngine(List.of(p1, p2, p3), 2, false);
        String text = engine.generatePairings(new HashSet<>(), 1);
        assertNotNull(text);
        assertTrue(text.contains("Freilos"));
    }

    @Test
    public void testGeneratePairings_swissSystem_allPairsPlayed_returnsNull() {
        PairingEngine engine = new PairingEngine(List.of(p1, p2), 1, false);
        engine.generatePairings(new HashSet<>(), 1);
        engine.clearCurrentRound();
        String text = engine.generatePairings(new HashSet<>(), 2);
        assertNull(text);
        assertTrue(engine.isFinished());
    }

    @Test
    public void testGeneratePairings_swissSystem_fourPlayers_twoMatches() {
        PairingEngine engine = new PairingEngine(List.of(p1, p2, p3, p4), 2, false);
        String text = engine.generatePairings(new HashSet<>(), 1);
        assertNotNull(text);
        assertEquals(2, engine.getMatches().size());
    }

    @Test
    public void testGeneratePairings_roundRobin_round1_twoPlayers_generatesMatch() {
        PairingEngine engine = new PairingEngine(List.of(p1, p2), 1, true);
        String text = engine.generatePairings(new HashSet<>(), 1);
        assertNotNull(text);
        assertEquals(1, engine.getMatches().size());
    }

    @Test
    public void testGeneratePairings_roundRobin_roundBeyondMax_returnsNull() {
        PairingEngine engine = new PairingEngine(List.of(p1, p2), 1, true);
        String text = engine.generatePairings(new HashSet<>(), 2);
        assertNull(text);
        assertTrue(engine.isFinished());
    }

    @Test
    public void testGeneratePairings_roundRobin_oddPlayers_roundBeyondMax_returnsNull() {
        PairingEngine engine = new PairingEngine(List.of(p1, p2, p3), 1, true);
        String text = engine.generatePairings(new HashSet<>(), 4);
        assertNull(text);
        assertTrue(engine.isFinished());
    }

    @Test
    public void testGeneratePairings_roundRobin_oddPlayers_round1_addsByeMatch() {
        PairingEngine engine = new PairingEngine(List.of(p1, p2, p3), 2, true);
        String text = engine.generatePairings(new HashSet<>(), 1);
        assertNotNull(text);
        assertTrue(text.contains("Freilos"));
    }

    @Test
    public void testSetNewMatches_replacesCurrentMatchesAndReturnsText() {
        PairingEngine engine = new PairingEngine(List.of(p1, p2), 1, false);
        engine.generatePairings(new HashSet<>(), 1);
        Match newMatch = new Match(p1, p2, -1);
        String text = engine.setNewMatches(List.of(newMatch));
        assertNotNull(text);
        assertEquals(1, engine.getMatches().size());
    }

    @Test
    public void testSetNewMatches_assignsTableToNormalMatch() {
        PairingEngine engine = new PairingEngine(List.of(p1, p2), 1, false);
        engine.generatePairings(new HashSet<>(), 1);
        Match newMatch = new Match(p1, p2, -1);
        engine.setNewMatches(List.of(newMatch));
        assertEquals(1, engine.getMatches().getFirst().getTableNumber());
    }

    @Test
    public void testSetNewMatches_byeMatchInInput_appearsInMatches() {
        PairingEngine engine = new PairingEngine(List.of(p1, p2, p3), 2, false);
        engine.generatePairings(new HashSet<>(), 1);
        Match byeMatch = new Match(p3, null, -1);
        Match normalMatch = new Match(p1, p2, -1);
        engine.setNewMatches(List.of(normalMatch, byeMatch));
        long byeCount = engine.getMatches().stream().filter(m -> m.getSecondPlayer() == null).count();
        assertEquals(1, byeCount);
    }

    @Test
    public void testSetNewMatches_textContainsVs() {
        PairingEngine engine = new PairingEngine(List.of(p1, p2), 1, false);
        engine.generatePairings(new HashSet<>(), 1);
        Match newMatch = new Match(p1, p2, -1);
        String text = engine.setNewMatches(List.of(newMatch));
        assertTrue(text.contains("vs."));
    }

    @Test
    public void testCalculateAllPossibleOpenMatches_currentRoundNotYetPlayed_returnsCurrentPairing() {
        PairingEngine engine = new PairingEngine(List.of(p1, p2), 1, false);
        engine.generatePairings(new HashSet<>(), 1);
        List<Match> open = engine.calculateAllPossibleOpenMatches();
        assertEquals(1, open.size());
    }

    @Test
    public void testCalculateAllPossibleOpenMatches_afterRoundPlayed_noneRemaining() {
        PairingEngine engine = new PairingEngine(List.of(p1, p2), 1, false);
        engine.generatePairings(new HashSet<>(), 1);
        engine.clearCurrentRound();
        List<Match> open = engine.calculateAllPossibleOpenMatches();
        assertTrue(open.isEmpty());
    }

    @Test
    public void testCalculateAllPossibleOpenMatches_fourPlayers_multipleRemainingPairings() {
        PairingEngine engine = new PairingEngine(List.of(p1, p2, p3, p4), 2, false);
        engine.generatePairings(new HashSet<>(), 1);
        List<Match> open = engine.calculateAllPossibleOpenMatches();
        assertFalse(open.isEmpty());
    }

    @Test
    public void testRoundRobin_evenPlayers_allPairsGenerated() {
        PairingEngine engine = new PairingEngine(List.of(p1, p2, p3, p4), 2, true);
        Set<String> seenPairs = new HashSet<>();
        int round = 1;
        String text = engine.generatePairings(new HashSet<>(), round);
        while (text != null) {
            for (Match m : engine.getMatches()) {
                if (m.getSecondPlayer() != null) {
                    String key = m.getFirstPlayer().getFullName() + "||" + m.getSecondPlayer().getFullName();
                    String keyRev = m.getSecondPlayer().getFullName() + "||" + m.getFirstPlayer().getFullName();
                    assertFalse(seenPairs.contains(key) || seenPairs.contains(keyRev), "Duplicate pairing in round " + round);
                    seenPairs.add(key);
                }
            }
            engine.clearCurrentRound();
            round++;
            text = engine.generatePairings(new HashSet<>(), round);
        }
        assertEquals(3, round - 1);
        assertEquals(6, seenPairs.size());
    }

    @Test
    public void testRoundRobin_evenPlayers_eachPlayerPlaysEveryRound() {
        PairingEngine engine = new PairingEngine(List.of(p1, p2, p3, p4), 2, true);
        int round = 1;
        String text = engine.generatePairings(new HashSet<>(), round);
        while (text != null) {
            Set<Player> playersThisRound = new HashSet<>();
            for (Match m : engine.getMatches()) {
                playersThisRound.add(m.getFirstPlayer());
                if (m.getSecondPlayer() != null) playersThisRound.add(m.getSecondPlayer());
            }
            assertEquals(4, playersThisRound.size(), "Not all players played in round " + round);
            engine.clearCurrentRound();
            round++;
            text = engine.generatePairings(new HashSet<>(), round);
        }
    }

    @Test
    public void testRoundRobin_oddPlayers_correctNumberOfRounds() {
        Player p5 = new Player("Eve", "E", "ClubE", 1200);
        PairingEngine engine = new PairingEngine(List.of(p1, p2, p3, p4, p5), 2, true);
        int round = 1;
        String text = engine.generatePairings(new HashSet<>(), round);
        while (text != null) {
            engine.clearCurrentRound();
            round++;
            text = engine.generatePairings(new HashSet<>(), round);
        }
        assertEquals(5, round - 1);
    }

    @Test
    public void testRoundRobin_oddPlayers_eachPlayerGetsExactlyOneBye() {
        Player p5 = new Player("Eve", "E", "ClubE", 1200);
        List<Player> players = List.of(p1, p2, p3, p4, p5);
        PairingEngine engine = new PairingEngine(players, 2, true);
        Map<Player, Integer> byeCount = new HashMap<>();
        for (Player p : players) byeCount.put(p, 0);

        int round = 1;
        String text = engine.generatePairings(new HashSet<>(), round);
        while (text != null) {
            for (Match m : engine.getMatches()) {
                if (m.getSecondPlayer() == null) {
                    byeCount.merge(m.getFirstPlayer(), 1, Integer::sum);
                }
            }
            engine.clearCurrentRound();
            round++;
            text = engine.generatePairings(new HashSet<>(), round);
        }
        for (Player p : players) {
            assertEquals(1, byeCount.get(p), p.getFullName() + " should have exactly one bye");
        }
    }

    @Test
    public void testRoundRobin_oddPlayers_allPairsGenerated() {
        Player p5 = new Player("Eve", "E", "ClubE", 1200);
        List<Player> players = List.of(p1, p2, p3, p4, p5);
        PairingEngine engine = new PairingEngine(players, 2, true);
        Set<String> seenPairs = new HashSet<>();

        int round = 1;
        String text = engine.generatePairings(new HashSet<>(), round);
        while (text != null) {
            for (Match m : engine.getMatches()) {
                if (m.getSecondPlayer() != null) {
                    String key = m.getFirstPlayer().getFullName() + "||" + m.getSecondPlayer().getFullName();
                    String keyRev = m.getSecondPlayer().getFullName() + "||" + m.getFirstPlayer().getFullName();
                    assertFalse(seenPairs.contains(key) || seenPairs.contains(keyRev), "Duplicate pairing detected");
                    seenPairs.add(key);
                }
            }
            engine.clearCurrentRound();
            round++;
            text = engine.generatePairings(new HashSet<>(), round);
        }
        assertEquals(10, seenPairs.size());
    }

    @Test
    public void testRoundRobin_twoPlayers_exactlyOneRound() {
        PairingEngine engine = new PairingEngine(List.of(p1, p2), 1, true);
        assertNotNull(engine.generatePairings(new HashSet<>(), 1));
        engine.clearCurrentRound();
        assertNull(engine.generatePairings(new HashSet<>(), 2));
        assertTrue(engine.isFinished());
    }

    @Test
    public void testFormatMatchesAsText_normalMatch_containsVs() {
        PairingEngine engine = new PairingEngine(List.of(p1, p2), 1, false);
        Match m = new Match(p1, p2, 3);
        String text = engine.formatMatchesAsText(List.of(m));
        assertTrue(text.contains("vs."));
        assertTrue(text.contains("Tisch 3"));
    }

    @Test
    public void testFormatMatchesAsText_byeMatch_containsFreilos() {
        PairingEngine engine = new PairingEngine(List.of(p1, p2), 1, false);
        Match bye = new Match(p1, null, -1);
        String text = engine.formatMatchesAsText(List.of(bye));
        assertTrue(text.contains("Freilos"));
        assertFalse(text.contains("vs."));
    }
}
