package controller;

import model.GroupKoState;
import model.Match;
import model.Player;
import model.TournamentMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Group stage and knockout stage: group assignment, schedule, qualification, bracket and whole
 * simulated tournaments.
 */
public class GroupKnockoutTest {

    private static List<Player> players(int n) {
        List<Player> ps = new ArrayList<>();
        for (int i = 0; i < n; i++) ps.add(new Player("V" + i, "N" + i, "C" + (i % 3), 2000 - 10 * i));
        return ps;
    }

    // --- groups --------------------------------------------------------------------------------

    @Test
    public void snakeAssignment() {
        List<Player> ps = players(10);
        List<List<Player>> groups = GroupKnockout.assignGroups(ps, 4);
        assertEquals(3, groups.size(), "10 Spieler, Gruppengröße 4: 3 Gruppen");
        assertEquals(List.of(4, 3, 3), groups.stream().map(List::size).sorted(Comparator.reverseOrder()).toList());
        // snake: A gets seeds 1, 6, 7; B 2, 5, 8; C 3, 4, 9, 10
        assertEquals(List.of(ps.get(0), ps.get(5), ps.get(6)), groups.get(0));
        assertEquals(List.of(ps.get(1), ps.get(4), ps.get(7)), groups.get(1));
        assertEquals(List.of(ps.get(2), ps.get(3), ps.get(8), ps.get(9)), groups.get(2));
    }

    @ParameterizedTest
    @CsvSource({"8,4,2", "9,4,3", "12,4,3", "13,4,4", "5,5,1", "16,3,6"})
    public void groupCountAndSizes(int n, int size, int expectedGroups) {
        List<List<Player>> groups = GroupKnockout.assignGroups(players(n), size);
        assertEquals(expectedGroups, groups.size());
        assertEquals(n, groups.stream().mapToInt(List::size).sum());
        groups.forEach(g -> assertTrue(g.size() <= size && g.size() >= size - 1 || groups.size() == 1, "Größe " + g.size()));
    }

    @ParameterizedTest
    @CsvSource({"2", "3", "4", "5", "6"})
    public void roundRobinInGroupCoversEveryPairOnce(int n) {
        List<Player> group = players(n);
        Set<String> pairs = new HashSet<>();
        Map<Player, Integer> byes = new HashMap<>();
        for (int r = 1; r <= GroupKnockout.roundRobinRounds(n); r++) {
            Set<Player> inRound = new HashSet<>();
            for (Player[] p : GroupKnockout.roundRobinPairs(group, r)) {
                assertTrue(inRound.add(p[0]));
                if (p[1] == null) {
                    byes.merge(p[0], 1, Integer::sum);
                } else {
                    assertTrue(inRound.add(p[1]));
                    String key = p[0].getFullName().compareTo(p[1].getFullName()) < 0
                            ? p[0].getFullName() + p[1].getFullName() : p[1].getFullName() + p[0].getFullName();
                    assertTrue(pairs.add(key), "Paar doppelt");
                }
            }
            assertEquals(n, inRound.size());
        }
        assertEquals(n * (n - 1) / 2, pairs.size());
        assertTrue(GroupKnockout.roundRobinPairs(group, GroupKnockout.roundRobinRounds(n) + 1).isEmpty());
    }

    // --- bracket ---------------------------------------------------------------------------------

    @Test
    public void seedOrderIsStandard() {
        assertArrayEquals(new int[]{1, 2}, GroupKnockout.seedOrder(2));
        assertArrayEquals(new int[]{1, 4, 2, 3}, GroupKnockout.seedOrder(4));
        assertArrayEquals(new int[]{1, 8, 4, 5, 2, 7, 3, 6}, GroupKnockout.seedOrder(8));
        assertEquals(1, GroupKnockout.meetingRound(0, 1));
        assertEquals(2, GroupKnockout.meetingRound(0, 3));
        assertEquals(3, GroupKnockout.meetingRound(0, 7));
        assertEquals(8, GroupKnockout.bracketSize(5));
        assertEquals(3, GroupKnockout.knockoutRounds(8));
    }

    @Test
    public void bracket_byesForBestSeedsAndGroupsSeparated() {
        // 3 groups, 2 qualify each -> 6 qualifiers, bracket of 8 with 2 byes
        List<Player> ps = players(12);
        List<List<Player>> groups = GroupKnockout.assignGroups(ps, 4);
        Map<Player, Integer> groupOf = new HashMap<>();
        for (int g = 0; g < groups.size(); g++) for (Player p : groups.get(g)) groupOf.put(p, g + 1);
        List<List<Player>> ranked = groups; // seeding order as group result
        List<List<Player>> levels = GroupKnockout.qualifiers(ranked, 2, 0);
        List<Player> bracket = GroupKnockout.buildBracket(levels, groupOf);

        assertEquals(8, bracket.size());
        assertEquals(2, bracket.stream().filter(Objects::isNull).count());
        // the two best group winners get the byes
        Player w1 = levels.get(0).get(0), w2 = levels.get(0).get(1);
        int s1 = bracket.indexOf(w1), s2 = bracket.indexOf(w2);
        assertNull(bracket.get(s1 ^ 1), "Freilos für Setzplatz 1");
        assertNull(bracket.get(s2 ^ 1), "Freilos für Setzplatz 2");
        assertEquals(3, GroupKnockout.meetingRound(s1, s2), "1 und 2 erst im Finale");
        // winner and runner-up of the same group must not meet before the final
        for (int g = 1; g <= 3; g++) {
            List<Integer> slots = new ArrayList<>();
            for (int i = 0; i < 8; i++) if (bracket.get(i) != null && groupOf.get(bracket.get(i)) == g) slots.add(i);
            assertEquals(2, slots.size());
            assertTrue(GroupKnockout.meetingRound(slots.get(0), slots.get(1)) >= 2,
                    "Gruppe " + g + " trifft frühestens im Halbfinale aufeinander");
        }
    }

    @Test
    public void bracket_bestThirdDoesNotMeetOwnGroupInFirstRound() {
        // 3 groups, 2 qualify + best third -> 7 qualifiers, bracket of 8 with one bye.
        // The best third is alone in its seeding level, but must still be kept apart from his group.
        for (int bestGroup = 0; bestGroup < 3; bestGroup++) {
            List<Player> ps = players(12);
            List<List<Player>> groups = GroupKnockout.assignGroups(ps, 4);
            Map<Player, Integer> groupOf = new HashMap<>();
            for (int g = 0; g < groups.size(); g++) for (Player p : groups.get(g)) groupOf.put(p, g + 1);
            List<List<Player>> levels = new ArrayList<>(GroupKnockout.qualifiers(groups, 2, 0));
            Player third = groups.get(bestGroup).get(2);
            levels.add(new ArrayList<>(List.of(third)));
            List<Player> bracket = GroupKnockout.buildBracket(levels, groupOf);
            for (int i = 0; i < bracket.size(); i++) {
                Player p = bracket.get(i);
                if (p == null) continue;
                for (int j = i + 1; j < bracket.size(); j++) {
                    Player q = bracket.get(j);
                    if (q != null && groupOf.get(p).equals(groupOf.get(q))) {
                        assertTrue(GroupKnockout.meetingRound(i, j) >= 2,
                                "Gruppe " + groupOf.get(p) + " schon in der ersten KO-Runde: " + bracket);
                    }
                }
            }
            // group winners keep their seeded positions (seeds 1-3 by TTR)
            int[] order = GroupKnockout.seedOrder(8);
            for (int s = 0; s < 8; s++) {
                if (order[s] <= 3) assertEquals(levels.get(0).get(order[s] - 1), bracket.get(s));
            }
        }
    }

    @Test
    public void qualifiers_withBestThirds() {
        List<Player> ps = players(12);
        List<List<Player>> groups = GroupKnockout.assignGroups(ps, 4);
        // give the thirds different points per match
        groups.get(0).get(2).setPoints(2); groups.get(0).get(2).setWins(2); groups.get(0).get(2).setLosses(1);
        groups.get(1).get(2).setPoints(1); groups.get(1).get(2).setWins(1); groups.get(1).get(2).setLosses(2);
        groups.get(2).get(2).setPoints(2); groups.get(2).get(2).setWins(2); groups.get(2).get(2).setLosses(1);
        List<List<Player>> levels = GroupKnockout.qualifiers(groups, 2, 2);
        assertEquals(3, levels.size());
        assertEquals(Set.of(groups.get(0).get(2), groups.get(2).get(2)), new HashSet<>(levels.get(2)));
    }

    @Test
    public void acrossGroups_comparesPerMatch() {
        Player a = new Player("A", "A", "C", 1000), b = new Player("B", "B", "C", 1500);
        a.setPoints(2); a.setWins(2); a.setLosses(1);   // 2 of 3
        b.setPoints(3); b.setWins(3); b.setLosses(2);   // 3 of 5
        List<Player> list = new ArrayList<>(List.of(b, a));
        list.sort(GroupKnockout.acrossGroups());
        assertEquals(a, list.getFirst(), "2/3 Punkte pro Spiel schlagen 3/5");
    }

    // --- whole tournaments ---------------------------------------------------------------------

    private static void enterRandom(List<Match> round, Random rnd) {
        for (Match m : round) {
            if (m.getSecondPlayer() == null) continue;
            boolean first = rnd.nextBoolean();
            int loserSets = rnd.nextInt(3);
            int i = 0;
            for (; i < loserSets; i++) m.setResults(i, first ? new String[]{"8", "11"} : new String[]{"11", "8"});
            for (int k = 0; k < 3; k++, i++) m.setResults(i, first ? new String[]{"11", "6"} : new String[]{"6", "11"});
            m.setOverallResult(first ? "3:" + loserSets : loserSets + ":3");
        }
    }

    @ParameterizedTest
    @CsvSource({
            "8,4,2,0", "9,4,2,0", "10,4,2,1", "12,4,2,2", "12,3,1,0", "16,4,2,0", "17,4,2,3", "6,3,1,1", "5,5,2,0"})
    public void wholeTournament(int n, int groupSize, int perGroup, int bestNext) {
        for (long seed = 1; seed <= 4; seed++) {
            Random rnd = new Random(seed);
            List<Player> ps = players(n);
            PairingEngine engine = new PairingEngine(ps, 4, TournamentMode.GROUPS_KO);
            engine.setGroupKo(new GroupKoState(groupSize, perGroup, bestNext,
                    GroupKnockout.assignGroups(ps, groupSize), new ArrayList<>()));
            ScoreCalculator calc = new ScoreCalculator(engine.getAllMatches(), TournamentMode.GROUPS_KO);

            int groupRounds = engine.groupRounds();
            int expectedKo = engine.knockoutRounds();
            int round = 0;
            while (engine.generateNextRound(round + 1) != null) {
                round++;
                for (Match m : engine.getMatches()) {
                    if (round <= groupRounds) assertTrue(m.getGroupNo() > 0 && m.getKoRound() == 0);
                    else assertEquals(round - groupRounds, m.getKoRound());
                }
                enterRandom(engine.getMatches(), rnd);
                calc.calculate(ps);
                assertTrue(round < 50);
            }
            assertTrue(engine.isFinished());
            assertEquals(groupRounds + expectedKo, round, "Runden: Vorrunde + KO");

            // every group pair exactly once
            Set<String> groupPairs = new HashSet<>();
            for (Match m : engine.getAllMatches()) {
                if (m.getGroupNo() > 0 && m.getSecondPlayer() != null) {
                    assertTrue(groupPairs.add(m.getFirstPlayer().getFullName() + "|" + m.getSecondPlayer().getFullName()));
                }
            }
            int expectedPairs = engine.getGroupKo().groups().stream().mapToInt(g -> g.size() * (g.size() - 1) / 2).sum();
            assertEquals(expectedPairs, groupPairs.size());

            // knockout: final and third place decided, placing complete for the semi-finalists
            List<Match> ko = engine.getAllMatches().stream().filter(m -> m.getKoRound() > 0).toList();
            int total = engine.knockoutRounds();
            Match fin = GroupKnockout.find(ko, total, 0, false);
            assertNotNull(fin);
            assertNotNull(fin.getWinner());
            List<Map.Entry<Player, Integer>> placing = GroupKnockout.finalPlacing(ko, total);
            assertEquals(1, placing.getFirst().getValue());
            int qualified = (int) engine.getGroupKo().bracket().stream().filter(Objects::nonNull).count();
            assertEquals(qualified, placing.size(), "jeder Weitergekommene hat eine Platzierung");
            if (total >= 2) {
                Match semi0 = GroupKnockout.find(ko, total - 1, 0, false);
                Match semi1 = GroupKnockout.find(ko, total - 1, 1, false);
                boolean bothPlayed = semi0 != null && semi1 != null
                        && semi0.getSecondPlayer() != null && semi1.getSecondPlayer() != null;
                assertEquals(bothPlayed, GroupKnockout.find(ko, total, 0, true) != null,
                        "Spiel um Platz 3 genau dann, wenn beide Halbfinals gespielt wurden");
            }
            // knockout statistics do not change the group tables
            for (Player p : ps) assertTrue(p.getWins() + p.getLosses() <= groupSize - 1);
        }
    }

    @Test
    public void withoutThirdPlaceMatch_bothSemiLosersShareThirdPlace() {
        Random rnd = new Random(9);
        List<Player> ps = players(8);
        PairingEngine engine = new PairingEngine(ps, 4, TournamentMode.GROUPS_KO);
        engine.setGroupKo(new GroupKoState(4, 2, 0, false).withGroupsAndBracket(GroupKnockout.assignGroups(ps, 4), new ArrayList<>()));
        ScoreCalculator calc = new ScoreCalculator(engine.getAllMatches(), TournamentMode.GROUPS_KO);
        int round = 0;
        while (engine.generateNextRound(round + 1) != null) {
            round++;
            enterRandom(engine.getMatches(), rnd);
            calc.calculate(ps);
        }
        assertEquals(3 + 2, round, "3 Gruppenrunden, Halbfinale, Finale");
        List<Match> ko = engine.getAllMatches().stream().filter(m -> m.getKoRound() > 0).toList();
        assertNull(GroupKnockout.find(ko, 2, 0, true), "kein Spiel um Platz 3");
        assertEquals(1, engine.getMatchesOfRound(round).size(), "im Finale nur ein Spiel");
        List<Integer> places = GroupKnockout.finalPlacing(ko, 2).stream().map(Map.Entry::getValue).toList();
        assertEquals(List.of(1, 2, 3, 3), places);
        assertFalse(engine.getGroupKo().thirdPlaceMatch(), "Einstellung bleibt beim Kopieren erhalten");
    }

    @Test
    public void thirdPlaceMatchIsDefault() {
        assertTrue(new GroupKoState(4, 2, 0).thirdPlaceMatch());
        assertTrue(new GroupKoState(4, 2, 0, new ArrayList<>(), new ArrayList<>()).thirdPlaceMatch());
        assertFalse(new GroupKoState(4, 2, 0, false).thirdPlaceMatch());
        assertTrue(BracketController.buildModel(new GroupKoState(4, 2, 1), List.of()).placeholder()
                .contains("der beste Gruppen-Dritte."));
        assertTrue(BracketController.buildModel(new GroupKoState(4, 2, 2, false), List.of()).placeholder()
                .contains("die 2 besten Gruppen-Dritten. Ohne Spiel um Platz 3"));
    }

    @Test
    public void undoFirstKnockoutRoundClearsBracket() {
        Random rnd = new Random(3);
        List<Player> ps = players(8);
        PairingEngine engine = new PairingEngine(ps, 4, TournamentMode.GROUPS_KO);
        engine.setGroupKo(new GroupKoState(4, 2, 0, GroupKnockout.assignGroups(ps, 4), new ArrayList<>()));
        ScoreCalculator calc = new ScoreCalculator(engine.getAllMatches(), TournamentMode.GROUPS_KO);
        for (int r = 1; r <= 3; r++) {
            engine.generateNextRound(r);
            enterRandom(engine.getMatches(), rnd);
            calc.calculate(ps);
        }
        engine.generateNextRound(4);
        assertFalse(engine.getGroupKo().bracket().isEmpty());
        engine.undoLastRound();
        assertTrue(engine.getGroupKo().bracket().isEmpty());
        assertNotNull(engine.generateNextRound(4));
        assertEquals(4, engine.getGroupKo().bracket().size());
    }

    @Test
    public void withdrawnInKnockout_opponentAdvances() {
        Random rnd = new Random(5);
        List<Player> ps = players(8);
        PairingEngine engine = new PairingEngine(ps, 4, TournamentMode.GROUPS_KO);
        engine.setGroupKo(new GroupKoState(4, 2, 0, GroupKnockout.assignGroups(ps, 4), new ArrayList<>()));
        ScoreCalculator calc = new ScoreCalculator(engine.getAllMatches(), TournamentMode.GROUPS_KO);
        calc.setAnnulledPlayers(engine.getWithdrawn());
        for (int r = 1; r <= 3; r++) {
            engine.generateNextRound(r);
            enterRandom(engine.getMatches(), rnd);
            calc.calculate(ps);
        }
        engine.generateNextRound(4); // semi-finals (8 players, 2 groups, 2 qualify -> 4)
        enterRandom(engine.getMatches(), rnd);
        Match semi = GroupKnockout.find(engine.getMatches(), 1, 0, false);
        Player winner = semi.getWinner();
        Player otherFinalist = GroupKnockout.find(engine.getMatches(), 1, 1, false).getWinner();
        // the winner of the first semi-final withdraws before the final: walkover for the other finalist
        engine.withdraw(winner);
        assertNotNull(engine.generateNextRound(5), "Spiel um Platz 3 findet trotzdem statt");
        List<Match> finalRound = engine.getMatches();
        assertTrue(finalRound.stream().noneMatch(m -> winner.equals(m.getFirstPlayer()) || winner.equals(m.getSecondPlayer())));
        Match fin = GroupKnockout.find(finalRound, 2, 0, false);
        assertNull(fin.getSecondPlayer(), "Finale kampflos");
        assertEquals(otherFinalist, fin.getWinner());
        assertNotNull(GroupKnockout.find(finalRound, 2, 0, true));
    }

    @Test
    public void groupTable_notChangedByKnockoutMatches() {
        Player max = new Player("Max", "Bauer", "C", 3), anna = new Player("Anna", "Becker", "C", 2), kai = new Player("Kai", "Koch", "C", 1);
        // group: everybody one win; sets: Max +1, Kai +1, Anna -2; Max beat Kai
        Match m1 = groupMatch(max, kai, "3:1");
        Match m2 = groupMatch(anna, max, "3:2");
        Match m3 = groupMatch(kai, anna, "3:0");
        List<Match> matches = new ArrayList<>(List.of(m1, m2, m3));
        List<Player> group = List.of(max, anna, kai);
        new ScoreCalculator(matches, TournamentMode.GROUPS_KO).calculate(group);
        List<Player> before = Standings.compute(group, TournamentMode.ROUND_ROBIN, matches).stream().map(Standings.Entry::player).toList();
        assertEquals(List.of(max, kai, anna), before);

        // knockout: Anna beats Kai and Max - the group table must not change
        Match ko1 = new Match(anna, kai, 1);
        ko1.setKnockout(1, 0, false);
        ko1.setOverallResult("3:0");
        Match ko2 = new Match(anna, max, 1);
        ko2.setKnockout(2, 0, false);
        ko2.setOverallResult("3:0");
        matches.addAll(List.of(ko1, ko2));
        new ScoreCalculator(matches, TournamentMode.GROUPS_KO).calculate(group);
        List<Player> after = Standings.compute(group, TournamentMode.ROUND_ROBIN, matches).stream().map(Standings.Entry::player).toList();
        assertEquals(before, after);
    }

    private static Match groupMatch(Player a, Player b, String result) {
        Match m = new Match(a, b, 1);
        m.setGroup(1);
        m.setOverallResult(result);
        return m;
    }

    @Test
    public void roundNames() {
        assertEquals("Finale", GroupKnockout.roundName(3, 3));
        assertEquals("Halbfinale", GroupKnockout.roundName(2, 3));
        assertEquals("Viertelfinale", GroupKnockout.roundName(1, 3));
        assertEquals("Achtelfinale", GroupKnockout.roundName(1, 4));
        assertEquals("1. KO-Runde", GroupKnockout.roundName(1, 5));
    }

    @Test
    public void scoreCalculator_ignoresKnockoutMatches() {
        Player a = new Player("A", "A", "C", 1), b = new Player("B", "B", "C", 2);
        Match group = new Match(a, b, 1);
        group.setGroup(1);
        group.setOverallResult("3:0");
        Match ko = new Match(b, a, 1);
        ko.setKnockout(1, 0, false);
        ko.setOverallResult("3:0");
        Match koBye = new Match(a, null, -1);
        koBye.setKnockout(1, 1, false);
        new ScoreCalculator(new ArrayList<>(List.of(group, ko, koBye)), TournamentMode.GROUPS_KO).calculate(List.of(a, b));
        assertEquals(1, a.getPoints());
        assertEquals(0, b.getPoints());
        assertEquals(b, ko.getWinner());
        assertEquals(a, ko.getLoser());
        assertEquals(a, koBye.getWinner());
    }
}
