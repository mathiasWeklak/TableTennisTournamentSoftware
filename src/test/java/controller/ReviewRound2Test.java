package controller;

import model.GroupKoState;
import model.Match;
import model.Player;
import model.TournamentMode;
import org.junit.jupiter.api.Test;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Fixes from the second review: rounds with byes only, withdrawals in the knockout stage, ranks
 * without withdrawn players, bracket seeding, manual pairing, late registration, locked knockout
 * results and helpers.
 */
public class ReviewRound2Test {

    private static List<Player> players(int n) {
        List<Player> ps = new ArrayList<>();
        for (int i = 0; i < n; i++) ps.add(new Player("V" + i, "N" + i, "C" + (i % 3), 2000 - 10 * i));
        return ps;
    }

    /** The first player wins every match 3:0. */
    private static void enterFirstWins(List<Match> matches) {
        for (Match m : matches) {
            if (m.getSecondPlayer() == null || m.isDecided()) continue;
            for (int i = 0; i < 3; i++) m.setResults(i, new String[]{"11", "5"});
            m.setOverallResult("3:0");
        }
    }

    private static final class Cup {
        final List<Player> ps;
        final PairingEngine engine;
        final ScoreCalculator calc;
        int round;

        Cup(int n, int groupSize, int perGroup, boolean thirdPlace) {
            ps = players(n);
            engine = new PairingEngine(ps, 8, TournamentMode.GROUPS_KO);
            engine.setGroupKo(new GroupKoState(groupSize, perGroup, 0, thirdPlace)
                    .withGroupsAndBracket(GroupKnockout.assignGroups(ps, groupSize), new ArrayList<>()));
            calc = new ScoreCalculator(engine.getAllMatches(), TournamentMode.GROUPS_KO);
        }

        boolean next() {
            if (engine.generateNextRound(round + 1) == null) return false;
            round++;
            enterFirstWins(engine.getMatches());
            recalc();
            return true;
        }

        void recalc() {
            calc.setAnnulledPlayers(engine.annulledPlayers());
            calc.calculate(ps);
        }

        List<Match> ko() {
            return engine.getAllMatches().stream().filter(m -> m.getKoRound() > 0).toList();
        }
    }

    // --- rounds with byes only -------------------------------------------------------------

    @Test
    public void groupRoundWithByesOnly_doesNotEndTheTournament() {
        Cup cup = new Cup(3, 3, 2, false);
        assertTrue(cup.next());
        cup.engine.withdraw(cup.ps.get(0));
        while (cup.next()) {
            assertTrue(cup.round <= 10);
        }
        assertEquals(3 + 1, cup.round, "drei Gruppenrunden und das Finale");
        boolean byeOnlyRound = false;
        for (int r = 2; r <= 3; r++) {
            byeOnlyRound |= cup.engine.getMatchesOfRound(r).stream().allMatch(m -> m.getSecondPlayer() == null);
        }
        assertTrue(byeOnlyRound, "eine Gruppenrunde besteht nur aus spielfrei");
        Match fin = GroupKnockout.find(cup.ko(), 1, 0, false);
        assertNotNull(fin);
        assertEquals(Set.of(cup.ps.get(1), cup.ps.get(2)), Set.of(fin.getFirstPlayer(), fin.getSecondPlayer()));
    }

    @Test
    public void finalistWithdraws_finalIsAWalkoverAndHasAWinner() {
        Cup cup = new Cup(8, 4, 2, false);
        for (int i = 0; i < 4; i++) assertTrue(cup.next()); // 3 group rounds + semi-finals
        Match semi0 = GroupKnockout.find(cup.ko(), 1, 0, false);
        Match semi1 = GroupKnockout.find(cup.ko(), 1, 1, false);
        cup.engine.withdraw(semi0.getWinner());

        assertTrue(cup.next(), "das Finale wird angelegt");
        Match fin = GroupKnockout.find(cup.ko(), 2, 0, false);
        assertNotNull(fin);
        assertNull(fin.getSecondPlayer(), "kampflos");
        assertEquals(semi1.getWinner(), fin.getFirstPlayer());
        assertFalse(cup.next());
        assertTrue(cup.engine.isFinished());

        List<Map.Entry<Player, Integer>> placing = GroupKnockout.finalPlacing(cup.ko(), 2);
        assertEquals(Map.entry(semi1.getWinner(), 1), placing.getFirst());
        assertTrue(placing.contains(Map.entry(semi0.getWinner(), 2)),
                "wer vor dem Finale zurückzieht, verliert es kampflos und ist Zweiter");
        assertTrue(placing.contains(Map.entry(semi0.getLoser(), 3)));
        assertTrue(placing.contains(Map.entry(semi1.getLoser(), 3)));
    }

    @Test
    public void semiFinalLoserWithdraws_otherLoserIsThirdByWalkover() {
        Cup cup = new Cup(8, 4, 2, true);
        for (int i = 0; i < 4; i++) assertTrue(cup.next());
        Match semi0 = GroupKnockout.find(cup.ko(), 1, 0, false);
        Match semi1 = GroupKnockout.find(cup.ko(), 1, 1, false);
        cup.engine.withdraw(semi0.getLoser());

        assertTrue(cup.next());
        Match third = GroupKnockout.find(cup.ko(), 2, 0, true);
        assertNotNull(third, "Spiel um Platz 3 kampflos");
        assertEquals(semi1.getLoser(), third.getFirstPlayer());
        assertNull(third.getSecondPlayer());

        List<Map.Entry<Player, Integer>> placing = GroupKnockout.finalPlacing(cup.ko(), 2);
        assertTrue(placing.contains(Map.entry(semi1.getLoser(), 3)));
        assertEquals(1, placing.stream().filter(e -> e.getValue() == 3).count(), "nur ein Dritter");
        assertTrue(placing.contains(Map.entry(semi0.getLoser(), 4)), "wer das Spiel um Platz 3 kampflos abgibt, ist Vierter");
    }

    @Test
    public void walkoverPlacing_doesNotDependOnTheTimeOfTheWithdrawal() {
        // A: the finalist withdraws after the final was drawn, "kampflos" is entered
        Cup before = new Cup(8, 4, 2, false);
        for (int i = 0; i < 4; i++) assertTrue(before.next());
        assertNotNull(before.engine.generateNextRound(5));
        Match fin = before.engine.getMatches().getFirst();
        Player leaving = fin.getFirstPlayer();
        before.engine.withdraw(leaving);
        for (int i = 0; i < 3; i++) fin.setResults(i, new String[]{"0", "11"});
        fin.setOverallResult("0:3");
        List<Map.Entry<Player, Integer>> placingA = GroupKnockout.finalPlacing(before.ko(), 2);

        // B: the same finalist withdraws before the final is drawn (bye for the opponent)
        Cup after = new Cup(8, 4, 2, false);
        for (int i = 0; i < 4; i++) assertTrue(after.next());
        Player leavingB = after.ps.get(before.ps.indexOf(leaving));
        after.engine.withdraw(leavingB);
        assertTrue(after.next());
        List<Map.Entry<Player, Integer>> placingB = GroupKnockout.finalPlacing(after.ko(), 2);

        assertTrue(placingA.contains(Map.entry(leaving, 2)));
        assertTrue(placingB.contains(Map.entry(leavingB, 2)));
        assertEquals(placingA.stream().map(e -> e.getKey().getFullName() + "=" + e.getValue()).sorted().toList(),
                placingB.stream().map(e -> e.getKey().getFullName() + "=" + e.getValue()).sorted().toList());
    }

    @Test
    public void wholeGroupWithdraws_singleQualifierWinsByWalkover() {
        Cup cup = new Cup(4, 2, 1, false);
        assertTrue(cup.next());
        List<Player> groupA = cup.engine.getGroupKo().groups().getFirst();
        groupA.forEach(cup.engine::withdraw);
        cup.recalc();
        assertTrue(cup.next(), "die KO-Runde wird ausgelost");
        Match fin = GroupKnockout.find(cup.ko(), 1, 0, false);
        assertNotNull(fin);
        assertNull(fin.getSecondPlayer());
        assertFalse(cup.next());
        assertTrue(cup.engine.isFinished());
        List<Map.Entry<Player, Integer>> placing = GroupKnockout.finalPlacing(cup.ko(), 1);
        assertEquals(1, placing.getFirst().getValue());
        assertFalse(groupA.contains(placing.getFirst().getKey()));
    }

    @Test
    public void simulation_producesOnlyValidResults() throws Exception {
        List<Player> ps = players(8);
        List<Match> matches = new ArrayList<>();
        for (int i = 0; i < 300; i++) matches.add(new Match(ps.get(i % 8), ps.get((i + 1) % 8), 1));
        java.lang.reflect.Method simulate = simulation.TournamentSimulation.class.getDeclaredMethod("simulateMatches", List.class);
        simulate.setAccessible(true);
        simulate.invoke(null, matches);
        for (Match m : matches) {
            assertTrue(m.isDecided(), m.getOverallResult());
            assertEquals(m.getOverallResult(), model.ResultRules.overallResult(m.getResults()));
        }
    }

    @Test
    public void knockoutWithdrawal_groupResultsStayAfterTheDrawIsTakenBack() {
        Cup cup = new Cup(8, 4, 2, true);
        for (int i = 0; i < 3; i++) assertTrue(cup.next()); // group stage
        assertNotNull(cup.engine.generateNextRound(4));       // semi-finals drawn, no results yet
        cup.round = 4;
        Player semiPlayer = cup.engine.getMatches().getFirst().getFirstPlayer();
        cup.recalc();
        int groupPoints = semiPlayer.getPoints();
        assertTrue(groupPoints > 0);

        cup.engine.withdraw(semiPlayer);
        assertTrue(cup.engine.canUndoLastRound());
        cup.engine.undoLastRound();
        assertTrue(cup.engine.getGroupKo().bracket().isEmpty());
        assertFalse(cup.engine.annulledPlayers().contains(semiPlayer), "in der KO-Phase zurückgezogen");
        cup.recalc();
        assertEquals(groupPoints, semiPlayer.getPoints());

        // the new draw leaves the withdrawn player out
        assertNotNull(cup.engine.generateNextRound(4));
        assertFalse(cup.engine.getGroupKo().bracket().contains(semiPlayer));
    }

    @Test
    public void knockoutWithdrawals_surviveSaveAndLoad() throws Exception {
        Cup cup = new Cup(8, 4, 2, true);
        for (int i = 0; i < 4; i++) assertTrue(cup.next());
        Player p = GroupKnockout.find(cup.ko(), 1, 0, false).getWinner();
        cup.engine.withdraw(p);
        assertEquals(List.of(p), cup.engine.getGroupKo().knockoutWithdrawn());
        java.io.File file = java.io.File.createTempFile("kotest", ".ser");
        file.deleteOnExit();
        model.TournamentState state = new model.TournamentState(new ArrayList<>(cup.ps),
                new ArrayList<>(cup.engine.getAllMatches()), new ArrayList<>(cup.engine.getMatches()), 4, false,
                "T", 8, TournamentMode.GROUPS_KO, new ArrayList<>(cup.engine.getWithdrawn()), 0, false,
                cup.engine.getGroupKo());
        TournamentStorage.save(state, file);
        GroupKoState loaded = TournamentStorage.load(file).groupKo();
        assertEquals(1, loaded.knockoutWithdrawn().size());
        assertEquals(p, loaded.knockoutWithdrawn().getFirst());
        assertNotNull(new GroupKoState(4, 2, 0, List.of(), List.of(), false, null).knockoutWithdrawn(),
                "fehlt in alten Dateien: leere Liste");
    }

    // --- withdrawal in the knockout stage --------------------------------------------------

    @Test
    public void withdrawalInKnockoutStage_keepsGroupResults() {
        Cup cup = new Cup(8, 4, 2, true);
        for (int i = 0; i < 4; i++) assertTrue(cup.next());
        Player semiWinner = GroupKnockout.find(cup.ko(), 1, 0, false).getWinner();
        int groupPoints = semiWinner.getPoints();
        assertTrue(groupPoints > 0);
        List<List<Player>> rankedBefore = cup.engine.rankedGroups();

        cup.engine.withdraw(semiWinner);
        assertFalse(cup.engine.annulledPlayers().contains(semiWinner), "im Turnierbaum: Gruppenspiele bleiben");
        cup.recalc();
        assertEquals(groupPoints, semiWinner.getPoints());
        assertEquals(rankedBefore.stream().map(g -> g.stream().filter(p -> p != semiWinner).toList()).toList(),
                cup.engine.rankedGroups());
    }

    @Test
    public void withdrawalInGroupStage_strikesGroupMatches() {
        Cup cup = new Cup(8, 4, 2, true);
        assertTrue(cup.next());
        Player p = cup.ps.get(0);
        cup.engine.withdraw(p);
        assertTrue(cup.engine.annulledPlayers().contains(p));
        cup.recalc();
        assertEquals(0, p.getPoints());
    }

    // --- ranks without withdrawn players ---------------------------------------------------

    @Test
    public void computeWithout_ranksWithoutStruckPlayers() {
        List<Player> ps = players(4);
        Player a = ps.get(0), b = ps.get(1), c = ps.get(2), w = ps.get(3);
        List<Match> matches = new ArrayList<>();
        for (Player[] pair : new Player[][]{{a, b}, {a, c}, {b, c}, {w, a}, {w, b}, {w, c}}) {
            Match m = new Match(pair[0], pair[1], 1);
            m.setOverallResult("3:0");
            matches.add(m);
        }
        ScoreCalculator calc = new ScoreCalculator(matches, TournamentMode.ROUND_ROBIN);
        calc.setAnnulledPlayers(Set.of(w));
        calc.calculate(ps);
        List<Match> counting = ScoreCalculator.countingMatches(matches, TournamentMode.ROUND_ROBIN, Set.of(w));

        List<Standings.Entry> standings = Standings.computeWithout(ps, TournamentMode.ROUND_ROBIN, counting, Set.of(w));
        assertEquals(List.of(a, b, c, w), standings.stream().map(Standings.Entry::player).toList());
        assertEquals(List.of(1, 2, 3), standings.subList(0, 3).stream().map(Standings.Entry::rank).toList(),
                "C ist Dritter, nicht Vierter");
    }

    // --- bracket: byes stay with the better seeding level ----------------------------------

    @Test
    public void bracket_runnersUpKeepTheirByes() {
        Map<Player, Integer> groupOf = new HashMap<>();
        List<List<Player>> groups = List.of(players(3), players(4), players(3));
        List<List<Player>> named = new ArrayList<>();
        int ttr = 2000;
        for (int g = 0; g < groups.size(); g++) {
            List<Player> group = new ArrayList<>();
            for (int i = 0; i < groups.get(g).size(); i++) {
                Player p = new Player("G" + g, "P" + i, "C", ttr--);
                group.add(p);
                groupOf.put(p, g + 1);
            }
            named.add(group);
        }
        List<List<Player>> levels = new ArrayList<>();
        for (int place = 0; place < 3; place++) {
            List<Player> level = new ArrayList<>();
            for (List<Player> g : named) level.add(g.get(place));
            levels.add(level);
        }
        List<Player> bracket = GroupKnockout.buildBracket(levels, groupOf);
        assertEquals(16, bracket.size());
        for (Player runnerUp : levels.get(1)) {
            int slot = bracket.indexOf(runnerUp);
            assertNull(bracket.get(slot ^ 1), runnerUp.getFullName() + " hat ein Freilos");
        }
        for (Player winner : levels.get(0)) {
            int slot = bracket.indexOf(winner);
            assertNull(bracket.get(slot ^ 1));
        }
    }

    // --- manual pairing keeps the points before the round ----------------------------------

    @Test
    public void manualPairing_floatInformationUsesPointsBeforeTheRound() {
        List<Player> ps = players(5);
        PairingEngine engine = new PairingEngine(ps, 4, TournamentMode.SWISS);
        engine.generateNextRound(1);
        Player byePlayer = engine.getMatches().stream().filter(m -> m.getSecondPlayer() == null)
                .findFirst().orElseThrow().getFirstPlayer();
        assertEquals(ps.get(4), byePlayer, "Freilos an den Letzten der Setzliste");
        new ScoreCalculator(engine.getAllMatches(), TournamentMode.SWISS).calculate(ps); // bye point counts now
        assertEquals(1, byePlayer.getPoints());

        engine.setNewMatches(List.of(new Match(ps.get(0), ps.get(4), -1), new Match(ps.get(1), ps.get(2), -1),
                new Match(ps.get(3), null, -1)));
        for (Match m : engine.getMatches()) {
            assertEquals(1, m.getRound());
            if (m.getSecondPlayer() == null) continue;
            assertFalse(m.isUpfloatFor(m.getFirstPlayer()) || m.isUpfloatFor(m.getSecondPlayer())
                    || m.isDownfloatFor(m.getFirstPlayer()) || m.isDownfloatFor(m.getSecondPlayer()),
                    "vor Runde 1 hatten alle 0 Punkte");
        }
    }

    // --- late registration is not part of the running round --------------------------------

    @Test
    public void manualPairing_offersOnlyPlayersOfTheRunningRound() {
        List<Player> ps = new ArrayList<>(players(4));
        PairingEngine engine = new PairingEngine(ps, 4, TournamentMode.SWISS);
        engine.generateNextRound(1);
        Player late = new Player("Neu", "Spieler", "C", 1000);
        ps.add(late); // the engine shares the list
        assertEquals(4, engine.currentRoundPlayers().size());
        assertTrue(engine.calculateAllPossibleOpenMatches().stream()
                .noneMatch(m -> late.equals(m.getFirstPlayer()) || late.equals(m.getSecondPlayer())));
        assertTrue(engine.calculateAllPossibleOpenMatches().stream().noneMatch(m -> m.getSecondPlayer() == null),
                "gerade Zahl: kein Freilos");
    }

    // --- knockout result locked once the winner is drawn -----------------------------------

    @Test
    public void changesDrawnWinner_onlyForEarlierKnockoutRounds() {
        Player a = new Player("A", "A", "C", 1);
        Player b = new Player("B", "B", "C", 1);
        Match ko = new Match(a, b, 1);
        ko.setKnockout(1, 0, false);
        ko.setPairingInfo(4, 0, 0);
        for (int i = 0; i < 3; i++) ko.setResults(i, new String[]{"11", "5"});
        String[][] reversed = {{"5", "11"}, {"5", "11"}, {"5", "11"}, {null, null}, {null, null}};
        String[][] sameWinner = {{"11", "5"}, {"9", "11"}, {"11", "5"}, {"11", "7"}, {null, null}};
        String[][] cleared = {{"", ""}, {"", ""}, {"", ""}, {"", ""}, {"", ""}};

        assertTrue(ResultEntryController.changesDrawnWinner(ko, reversed, 5));
        assertTrue(ResultEntryController.changesDrawnWinner(ko, cleared, 5));
        assertFalse(ResultEntryController.changesDrawnWinner(ko, sameWinner, 5), "nur der Satzstand ändert sich");
        assertFalse(ResultEntryController.changesDrawnWinner(ko, reversed, 4), "aktuelle Runde");

        Match group = new Match(a, b, 1);
        group.setGroup(1);
        group.setPairingInfo(1, 0, 0);
        assertFalse(ResultEntryController.changesDrawnWinner(group, reversed, 5));
    }

    // --- helpers ---------------------------------------------------------------------------

    @Test
    public void uniqueShortNames_fullNameWhenInitialsAreEqual() {
        Player anna = new Player("Anna", "Müller", "C", 1);
        Player andreas = new Player("Andreas", "Müller", "C", 1);
        Player max = new Player("Max", "Müller", "C", 1);
        Map<Player, String> names = Player.uniqueShortNames(List.of(anna, andreas, max));
        assertEquals("Anna Müller", names.get(anna));
        assertEquals("Andreas Müller", names.get(andreas));
        assertEquals("M. Müller", names.get(max));
        assertEquals(3, new HashSet<>(names.values()).size());
    }

    @Test
    public void bracket_byesAlwaysGoToTheBestSeeds() {
        Random rnd = new Random(7);
        for (int run = 0; run < 2000; run++) {
            int groupCount = 2 + rnd.nextInt(5);
            int perGroup = 1 + rnd.nextInt(2);
            List<List<Player>> groups = new ArrayList<>();
            Map<Player, Integer> groupOf = new HashMap<>();
            for (int g = 0; g < groupCount; g++) {
                List<Player> group = new ArrayList<>();
                int size = 3 + rnd.nextInt(3);
                for (int i = 0; i < size; i++) {
                    Player p = new Player("G" + g, "P" + i, "C", 1000 + rnd.nextInt(1000));
                    group.add(p);
                    groupOf.put(p, g + 1);
                }
                group.sort(Comparator.comparingInt(Player::getTtr).reversed());
                groups.add(group);
            }
            int bestNext = rnd.nextInt(groupCount);
            List<List<Player>> levels = GroupKnockout.qualifiers(groups, perGroup, bestNext);
            List<Player> seeds = levels.stream().flatMap(List::stream).toList();
            List<Player> bracket = GroupKnockout.buildBracket(levels, groupOf);
            int byes = bracket.size() - seeds.size();
            Set<Player> withBye = new HashSet<>();
            for (int slot = 0; slot < bracket.size(); slot++) {
                if (bracket.get(slot) != null && bracket.get(slot ^ 1) == null) withBye.add(bracket.get(slot));
            }
            assertEquals(new HashSet<>(seeds.subList(0, byes)), withBye, "Lauf " + run);
        }
    }

    @Test
    public void uniqueShortNames_addInitialOnlyForEqualLastNames() {
        Player anna = new Player("Anna", "Müller", "C", 1);
        Player max = new Player("Max", "Müller", "C", 1);
        Player ben = new Player("Ben", "Becker", "C", 1);
        Map<Player, String> names = Player.uniqueShortNames(List.of(anna, max, ben));
        assertEquals("A. Müller", names.get(anna));
        assertEquals("M. Müller", names.get(max));
        assertEquals("Becker", names.get(ben));
    }

    @Test
    public void isSameFile_resolvesRelativeParts() {
        java.io.File a = new java.io.File("x" + java.io.File.separator + ".." + java.io.File.separator + "t.ser");
        assertTrue(TournamentStorage.isSameFile(a, new java.io.File("t.ser")));
        assertFalse(TournamentStorage.isSameFile(new java.io.File("t.ser"), new java.io.File("t_Runde_1.ser")));
    }

    @Test
    public void groupSettings_groupWithOneParticipantNamesTheGroupSize() {
        String error = TournamentController.validateGroupSettings(3, 2, 1, 0);
        assertNotNull(error);
        assertTrue(error.contains("Gruppengröße"), error);
    }

    @Test
    public void refereeSheets_headerNamesGroupOrKnockoutRound() throws Exception {
        Player a = Player.pair(new Player("Anna", "Becker", "C", 1), new Player("Lena", "Krause", "C", 1));
        Player b = Player.pair(new Player("Max", "Mustermann", "C", 1), new Player("Alexander", "Schneider", "C", 1));
        Match group = new Match(a, b, 1);
        group.setGroup(2);
        group.setPairingInfo(3, 0, 0);
        Match semi = new Match(a, b, 2);
        semi.setKnockout(1, 0, false);
        Match third = new Match(a, b, 3);
        third.setKnockout(2, 0, true);

        RefereeSheetsController sheets = new RefereeSheetsController(null, List.of(group, semi, third), "Pokal",
                "KO-Runde: Halbfinale", 2);
        sheets.close(); // no window in the test
        assertEquals("Pokal – KO-Runde: Halbfinale", sheets.getHeaderText());
        assertEquals("Pokal – Gruppe B, Runde 3", sheets.getHeaderText(group));
        assertEquals("Pokal – Halbfinale", sheets.getHeaderText(semi));
        assertEquals("Pokal – Spiel um Platz 3", sheets.getHeaderText(third));

        Printable printable = sheets.printable();
        PageFormat pf = new PageFormat();
        BufferedImage img = new BufferedImage((int) pf.getWidth(), (int) pf.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        assertEquals(Printable.PAGE_EXISTS, printable.print(g, pf, 0));
        assertEquals(Printable.NO_SUCH_PAGE, printable.print(g, pf, 1));
        g.dispose();
    }
}
