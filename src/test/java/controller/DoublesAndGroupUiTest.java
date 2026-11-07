package controller;

import model.GroupKoState;
import model.Match;
import model.Player;
import model.TournamentMode;
import model.TournamentState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Doubles participants, group settings validation, bracket display model, labels and saving of
 * group/knockout tournaments.
 */
public class DoublesAndGroupUiTest {

    @TempDir
    Path tempDir;

    private static Player pair(String a, String b, int ttrA, int ttrB) {
        return Player.pair(new Player(a, a + "mann", "TTC " + a, ttrA), new Player(b, b + "mann", "TTC " + b, ttrB));
    }

    // --- doubles ---------------------------------------------------------------------------------

    @Test
    public void pair_namesClubsAndTtr() {
        Player p = Player.pair(new Player("Anna", "Becker", "TTC Nord", 1700), new Player("Bernd", "Krause", "SV Süd", 1500));
        assertTrue(p.isDoubles());
        assertEquals("Anna Becker / Bernd Krause", p.getFullName());
        assertEquals("Becker/Krause", p.getShortName());
        assertEquals("TTC Nord / SV Süd", p.getClub());
        assertEquals(3200, p.getTtr(), "Setzung nach der Summe der TTR-Werte");
        assertEquals(1700, p.getOwnTtr());
        assertEquals(1500, p.getPartnerTtr());

        Player sameClub = Player.pair(new Player("A", "A", "TTC", 1), new Player("B", "B", "TTC", 2));
        assertEquals("TTC", sameClub.getClub());

        Player single = new Player("Anna", "Becker", "TTC Nord", 1700);
        assertFalse(single.isDoubles());
        assertEquals("Becker", single.getShortName());
        assertNotEquals(single, p);
        assertEquals(p, Player.pair(new Player("Anna", "Becker", "TTC Nord", 1700), new Player("Bernd", "Krause", "SV Süd", 1500)));
        assertThrows(IllegalArgumentException.class, () -> Player.pair(p, single));
    }

    @Test
    public void validatePair_rules() {
        Player existing = pair("Anna", "Bernd", 1, 2);
        String[] clara = {"Clara", "Claramann", "TTC", ""};
        String[] dora = {"Dora", "Doramann", "TTC", "1400"};
        assertNull(PlayerDialog.validatePair(clara, dora, List.of(existing)));
        assertNotNull(PlayerDialog.validatePair(clara, new String[]{" clara ", "CLARAMANN", "X", ""}, List.of()),
                "zweimal dieselbe Person");
        assertNotNull(PlayerDialog.validatePair(clara, new String[]{"Bernd", "Berndmann", "X", ""}, List.of(existing)),
                "Bernd spielt schon in einem Doppel");
        assertNotNull(PlayerDialog.validatePair(new String[]{"", "X", "Y", ""}, dora, List.of()));
        assertNotNull(PlayerDialog.validatePair(clara, new String[]{"Dora", "Doramann", "TTC", "-3"}, List.of()));
    }

    @Test
    public void doublesTournament_pairingWorksOnPairs() {
        List<Player> pairs = new ArrayList<>();
        for (int i = 0; i < 6; i++) pairs.add(pair("A" + i, "B" + i, 1000 + i, 1000 - i));
        PairingEngine engine = new PairingEngine(pairs, 3, TournamentMode.SWISS);
        assertNotNull(engine.generatePairings(1));
        assertEquals(3, engine.getMatches().size());
        assertTrue(engine.getMatches().stream().allMatch(m -> m.getFirstPlayer().isDoubles() && m.getSecondPlayer().isDoubles()));
    }

    @Test
    public void doublesAndGroupsSurviveSaving() throws Exception {
        List<Player> pairs = new ArrayList<>();
        for (int i = 0; i < 6; i++) pairs.add(pair("A" + i, "B" + i, 1000 + i, 900));
        List<List<Player>> groups = GroupKnockout.assignGroups(pairs, 3);
        GroupKoState groupKo = new GroupKoState(3, 1, 1, groups, new ArrayList<>(Arrays.asList(pairs.get(0), null, pairs.get(1), pairs.get(2))));
        Match m = new Match(pairs.get(0), pairs.get(3), 1);
        m.setGroup(1);
        TournamentState state = new TournamentState(new ArrayList<>(pairs), new ArrayList<>(List.of(m)), new ArrayList<>(List.of(m)),
                1, false, "Doppel", 3, TournamentMode.GROUPS_KO, new ArrayList<>(), 0, true, groupKo);
        File file = tempDir.resolve("d.ser").toFile();
        TournamentStorage.save(state, file);
        TournamentState loaded = TournamentStorage.load(file);
        assertTrue(loaded.doubles());
        assertEquals(TournamentMode.GROUPS_KO, loaded.mode());
        assertEquals(2, loaded.groupKo().groups().size());
        assertNull(loaded.groupKo().bracket().get(1), "Freilos im Turnierbaum bleibt erhalten");
        assertSame(loaded.playerList().getFirst(), loaded.groupKo().groups().getFirst().getFirst());
        assertTrue(loaded.playerList().getFirst().isDoubles());
        assertEquals(1, loaded.allMatches().getFirst().getGroupNo());

        TournamentState old = new TournamentState(List.of(), List.of(), List.of(), 1, false, "Alt", 1, TournamentMode.SWISS);
        assertFalse(old.doubles());
        assertNull(old.groupKo());
    }

    // --- group settings --------------------------------------------------------------------------

    @Test
    public void groupSettingsValidation() {
        assertNull(TournamentController.validateGroupSettings(12, 4, 2, 0));
        assertNull(TournamentController.validateGroupSettings(12, 4, 2, 2));
        assertNull(TournamentController.validateGroupSettings(10, 4, 2, 1));
        assertNotNull(TournamentController.validateGroupSettings(10, 4, 3, 0), "kleinste Gruppe hat nur 3");
        assertNotNull(TournamentController.validateGroupSettings(12, 1, 1, 0));
        assertNotNull(TournamentController.validateGroupSettings(12, 4, 0, 0));
        assertNotNull(TournamentController.validateGroupSettings(12, 4, 2, 3), "alle Dritten weiter");
        assertNotNull(TournamentController.validateGroupSettings(12, 4, 2, 4), "mehr als Gruppen");
        assertNotNull(TournamentController.validateGroupSettings(12, 4, 1, -1));
    }

    // --- bracket model and labels ----------------------------------------------------------------

    @Test
    public void bracketModel_placeholderBeforeKnockout() {
        BracketController.Model model = BracketController.buildModel(new GroupKoState(4, 2, 1), List.of());
        assertNotNull(model.placeholder());
        assertTrue(model.placeholder().contains("ersten 2"));
        assertTrue(model.rounds().isEmpty());
    }

    @Test
    public void bracketModel_roundsByesResultsAndThirdPlace() {
        Player a = new Player("A", "A", "C", 4), b = new Player("B", "B", "C", 3), c = new Player("C", "C", "C", 2);
        // bracket of 4: A vs bye, B vs C
        List<Player> bracket = new ArrayList<>(Arrays.asList(a, null, c, b));
        GroupKoState groupKo = new GroupKoState(3, 1, 1, List.of(List.of(a, c), List.of(b)), bracket);
        Match bye = new Match(a, null, -1);
        bye.setKnockout(1, 0, false);
        Match semi = new Match(c, b, 1);
        semi.setKnockout(1, 1, false);
        semi.setOverallResult("1:3");
        Match fin = new Match(a, b, 1);
        fin.setKnockout(2, 0, false);
        BracketController.Model model = BracketController.buildModel(groupKo, List.of(bye, semi, fin));

        assertEquals(List.of("Halbfinale", "Finale"), model.roundNames());
        BracketController.Box first = model.rounds().get(0).get(0);
        assertEquals("A A", first.top());
        assertEquals("Freilos", first.bottom());
        assertEquals(1, first.winner());
        BracketController.Box second = model.rounds().get(0).get(1);
        assertEquals("C C", second.top());
        assertEquals("1", second.topSets());
        assertEquals("3", second.bottomSets());
        assertEquals(2, second.winner());
        BracketController.Box finalBox = model.rounds().get(1).get(0);
        assertEquals("A A", finalBox.top());
        assertEquals("B B", finalBox.bottom());
        assertEquals(0, finalBox.winner());
        assertNull(model.thirdPlace(), "kein Spiel um Platz 3, weil ein Halbfinale ein Freilos war");
    }

    @Test
    public void pairingText_namesGroupAndKnockoutRound() {
        Player a = new Player("Anna", "A", "C", 1), b = new Player("Ben", "B", "C", 2);
        Match g = new Match(a, b, 1);
        g.setGroup(2);
        Match semi = new Match(a, b, 2);
        semi.setKnockout(2, 0, false);
        Match third = new Match(a, b, 3);
        third.setKnockout(3, 0, true);
        Match koBye = new Match(a, null, -1);
        koBye.setKnockout(1, 0, false);
        String text = TournamentRound.formatPairingsWithStatus(List.of(g, semi, third, koBye), TournamentMode.GROUPS_KO, 3);
        assertTrue(text.contains("Gruppe B  Anna A"), text);
        assertTrue(text.contains("Halbfinale  Anna A"), text);
        assertTrue(text.contains("Spiel um Platz 3  Anna A"), text);
        assertTrue(text.contains("Freilos, kommt weiter"), text);
    }
}
