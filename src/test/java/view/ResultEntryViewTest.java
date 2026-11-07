package view;

import model.ResultRules;
import org.junit.jupiter.api.Test;

import javax.swing.JTextField;

import static org.junit.jupiter.api.Assertions.*;

public class ResultEntryViewTest {

    private static JTextField[][] fields(String... values) {
        JTextField[][] f = new JTextField[5][2];
        for (int i = 0; i < 5; i++) {
            f[i][0] = new JTextField(2 * i < values.length ? values[2 * i] : "");
            f[i][1] = new JTextField(2 * i + 1 < values.length ? values[2 * i + 1] : "");
        }
        return f;
    }

    private static String[][] read(JTextField[][] f) {
        String[][] s = new String[5][2];
        for (int i = 0; i < 5; i++) {
            s[i][0] = f[i][0].getText();
            s[i][1] = f[i][1].getText();
        }
        return s;
    }

    @Test
    public void walkover_overwritesEntriesWithThreeSets() {
        JTextField[][] f = fields("5", "11", "9", "11", "11", "3", "4", "11");
        ResultEntryView.fillWalkover(f, true);
        assertArrayEquals(new int[]{3, 0}, ResultRules.countSets(read(f)));
        assertTrue(ResultRules.isComplete(read(f)));
        assertEquals("", f[3][0].getText());

        ResultEntryView.fillWalkover(f, false);
        assertArrayEquals(new int[]{0, 3}, ResultRules.countSets(read(f)));
    }
}
