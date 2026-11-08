package controller;

import model.GroupKoState;
import model.Match;
import model.Player;
import model.TournamentMode;
import org.junit.jupiter.api.Test;
import view.GroupTablesPanel;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Group tables of a group/knockout tournament: fixed group order, result matrix from the row
 * player's view, place column, qualifiers, withdrawn participants and printing.
 */
public class GroupTablesTest {

    private static Player p(String name, int ttr) {
        return new Player(name, "X", "TTC", ttr);
    }

    private static Match result(Player a, Player b, int group, String overall) {
        Match m = new Match(a, b, 1);
        m.setGroup(group);
        m.setOverallResult(overall);
        return m;
    }

    private static List<GroupTables.Table> build(List<List<Player>> groups, List<Match> matches,
                                                 Set<Player> withdrawn, Set<Player> qualified) {
        List<Player> all = groups.stream().flatMap(List::stream).toList();
        ScoreCalculator calc = new ScoreCalculator(matches, TournamentMode.GROUPS_KO);
        calc.setAnnulledPlayers(withdrawn);
        calc.calculate(all);
        List<Match> counting = ScoreCalculator.countingMatches(matches, TournamentMode.GROUPS_KO, withdrawn);
        GroupKoState state = new GroupKoState(3, 1, 0).withGroupsAndBracket(groups, new ArrayList<>());
        return GroupTables.build(state, counting, withdrawn, qualified, false);
    }

    @Test
    public void table_keepsGroupOrderAndShowsResultsAndPlace() {
        Player a = p("A", 1600);
        Player b = p("B", 1500);
        Player c = p("C", 1400);
        List<Match> matches = List.of(
                result(a, b, 1, "1:3"), result(a, c, 1, "3:0"), result(b, c, 1, "3:2"));
        List<GroupTables.Table> tables = build(List.of(List.of(a, b, c)), matches, Set.of(), Set.of(b));

        assertEquals(1, tables.size());
        GroupTables.Table t = tables.getFirst();
        assertEquals("Gruppe A", t.title());
        assertArrayEquals(new String[]{"Nr.", "Name", "1", "2", "3", "Spiele", "Sätze", "Bälle", "Punkte", "Platz"},
                t.columns());
        // rows stay in group order, the place is in the last column
        Object[] rowA = t.rows().get(0);
        Object[] rowB = t.rows().get(1);
        assertEquals(1, rowA[0]);
        assertTrue(((String) rowA[1]).startsWith("A X"));
        assertEquals("", rowA[2], "Diagonale leer");
        assertEquals("1:3", rowA[3], "A gegen B aus Sicht von A");
        assertEquals("3:0", rowA[4]);
        assertEquals("3:1", rowB[2], "B gegen A aus Sicht von B");
        assertEquals("2:0", rowB[5]);
        assertEquals(2, rowB[8]);
        assertEquals("1.", rowB[9]);
        assertEquals("2.", rowA[9]);
        assertEquals("3.", t.rows().get(2)[9]);

        assertTrue(t.isDiagonal(1, 3));
        assertFalse(t.isDiagonal(1, 2));
        assertTrue(t.isResultColumn(4));
        assertFalse(t.isResultColumn(5));
        assertArrayEquals(new boolean[]{false, true, false}, t.qualified());
    }

    @Test
    public void table_unplayedMatchIsEmpty() {
        Player a = p("A", 1600);
        Player b = p("B", 1500);
        Player c = p("C", 1400);
        List<GroupTables.Table> tables = build(List.of(List.of(a, b, c)),
                List.of(result(a, b, 1, "3:0")), Set.of(), Set.of());
        Object[] rowA = tables.getFirst().rows().get(0);
        assertEquals("3:0", rowA[3]);
        assertEquals("", rowA[4]);
    }

    @Test
    public void table_withdrawnKeepsRowWithoutPlaceAndResults() {
        Player a = p("A", 1600);
        Player b = p("B", 1500);
        Player c = p("C", 1400);
        List<Match> matches = List.of(
                result(a, b, 1, "3:0"), result(a, c, 1, "3:0"), result(b, c, 1, "3:2"));
        List<GroupTables.Table> tables = build(List.of(List.of(a, b, c)), matches, Set.of(c), Set.of(a, c));
        GroupTables.Table t = tables.getFirst();
        Object[] rowC = t.rows().get(2);
        assertTrue(((String) rowC[1]).endsWith("zurückgezogen"));
        assertEquals("–", rowC[9]);
        assertEquals("", rowC[2]);
        assertEquals("", t.rows().get(0)[4], "Spiel gegen den Zurückgezogenen gestrichen");
        assertArrayEquals(new boolean[]{false, false, true}, t.withdrawn());
        assertTrue(t.qualified()[0]);
        assertFalse(t.qualified()[2], "Zurückgezogene sind nie weiter");
    }

    @Test
    public void tables_oneForEveryGroup() {
        Player a = p("A", 1600);
        Player b = p("B", 1500);
        Player c = p("C", 1400);
        Player d = p("D", 1300);
        List<GroupTables.Table> tables = build(List.of(List.of(a, d), List.of(b, c)), List.of(), Set.of(), Set.of());
        assertEquals(List.of("Gruppe A", "Gruppe B"), tables.stream().map(GroupTables.Table::title).toList());
        assertEquals(2, tables.get(1).size());
    }

    @Test
    public void panelAndPrintable_renderAllGroups() throws Exception {
        List<List<Player>> groups = new ArrayList<>();
        List<Match> matches = new ArrayList<>();
        for (int g = 0; g < 6; g++) {
            Player x = p("X" + g, 1500 - g);
            Player y = p("Y" + g, 1400 - g);
            Player z = p("Z" + g, 1300 - g);
            groups.add(List.of(x, y, z));
            matches.add(result(x, y, g + 1, "3:1"));
        }
        List<GroupTables.Table> tables = build(groups, matches, Set.of(), Set.of());

        GroupTablesPanel panel = new GroupTablesPanel();
        panel.setTables(tables, "Legende");
        assertEquals(6, panel.getGroupCount());

        Printable printable = GroupTablesPanel.printable(tables, "Gruppen: Test");
        PageFormat pf = new PageFormat();
        BufferedImage img = new BufferedImage((int) pf.getWidth(), (int) pf.getHeight(), BufferedImage.TYPE_INT_RGB);
        int pages = 0;
        while (true) {
            Graphics2D g = img.createGraphics();
            int r = printable.print(g, pf, pages);
            g.dispose();
            if (r == Printable.NO_SUCH_PAGE) break;
            pages++;
            assertTrue(pages < 10);
        }
        assertTrue(pages >= 1);
    }
}
