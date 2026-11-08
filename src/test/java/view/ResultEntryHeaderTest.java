package view;

import model.Player;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Column headers of the result entry: single players on one line, pairs on two lines.
 */
public class ResultEntryHeaderTest {

    @Test
    public void singlePlayer_plainName() {
        assertEquals("Anna Becker", ResultEntryView.headerText(new Player("Anna", "Becker", "C", 1)));
    }

    @Test
    public void pair_twoLinesWithEscapedNames() {
        Player pair = Player.pair(new Player("Anna", "Becker", "C", 1), new Player("Lena", "<Krause>", "C", 1));
        String header = ResultEntryView.headerText(pair);
        assertTrue(header.startsWith("<html>"));
        assertTrue(header.contains("Anna Becker<br>Lena &lt;Krause&gt;"), header);
    }
}
