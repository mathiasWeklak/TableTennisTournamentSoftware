package controller;

import model.Player;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Reads and writes participant lists as CSV (e.g. from click-TT or Excel).
 *
 * <p>Accepted: separator ";", "," or tab (detected from the first line), fields in double quotes,
 * UTF-8 (with or without BOM) or Windows-1252 (Excel). With a header line the columns are found
 * by name ("Vorname", "Nachname" or "Name", "Verein", "TTR" or "Q-TTR"; for doubles the second
 * occurrence belongs to the partner); without header the order is first name, last name, club,
 * TTR (for doubles followed by the same four fields of the partner).</p>
 */
public final class ParticipantCsv {

    /**
     * Result of an import.
     *
     * @param players the valid participants, in file order
     * @param errors  one message per rejected line, e.g. "Zeile 4: ..."
     */
    public record ImportResult(List<Player> players, List<String> errors) {
    }

    private ParticipantCsv() {
    }

    /**
     * Decodes file content: UTF-8 (a BOM is removed); if that is not valid UTF-8, Windows-1252.
     *
     * @param bytes the file content
     * @return the text
     */
    public static String decode(byte[] bytes) {
        try {
            String text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
            return text.startsWith("﻿") ? text.substring(1) : text;
        } catch (CharacterCodingException e) {
            return new String(bytes, Charset.forName("windows-1252"));
        }
    }

    /**
     * Parses a participant list. Every line is checked like an entry in the player dialog
     * (required fields, TTR, no duplicate names, a person in only one pair).
     *
     * @param text     the CSV text
     * @param doubles  {@code true} for pairs (two persons per line)
     * @param existing participants already in the list
     * @return the valid participants and the errors
     */
    public static ImportResult parse(String text, boolean doubles, List<Player> existing) {
        List<Player> players = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        String[] lines = text.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        // the separator comes from the first line with content; lines of separators only are skipped
        int first = 0;
        while (first < lines.length && lines[first].replaceAll("[;,\t]", "").isBlank()) first++;
        if (first >= lines.length) return new ImportResult(players, List.of("Die Datei enthält keine Teilnehmer."));
        char separator = detectSeparator(lines[first]);

        int persons = doubles ? 2 : 1;
        int[][] columns = new int[persons][];
        List<String> header = split(lines[first], separator);
        int start = first;
        if (!doubles && (isHeader(header) ? column(header, 1, "vorname") >= 0 : hasPartnerColumns(lines, first, separator))) {
            return new ImportResult(players, List.of("Die Datei enthält Doppel (zwei Personen je Zeile). "
                    + "Bitte zuerst „Doppel“ anhaken und dann importieren."));
        }
        if (isHeader(header)) {
            for (int person = 0; person < persons; person++) {
                columns[person] = new int[]{
                        column(header, person, "vorname"),
                        column(header, person, "nachname", "name"),
                        column(header, person, "verein", "club"),
                        column(header, person, "ttr", "qttr")};
                if (columns[person][0] < 0 || columns[person][1] < 0) {
                    return new ImportResult(players, List.of("Spalten „Vorname“ und „Nachname“"
                            + (doubles ? " (für beide Personen)" : "") + " nicht gefunden."));
                }
            }
            start = first + 1;
        } else {
            for (int person = 0; person < persons; person++) {
                columns[person] = new int[]{4 * person, 4 * person + 1, 4 * person + 2, 4 * person + 3};
            }
        }

        for (int i = start; i < lines.length; i++) {
            if (lines[i].isBlank()) continue;
            List<String> fields = split(lines[i], separator);
            if (fields.stream().allMatch(String::isBlank)) continue; // e.g. ";;;" from Excel
            String[][] person = new String[persons][];
            for (int p = 0; p < persons; p++) {
                person[p] = new String[4];
                for (int f = 0; f < 4; f++) person[p][f] = field(fields, columns[p][f]);
                person[p][3] = normalizeTtr(person[p][3]);
            }
            List<Player> others = new ArrayList<>(existing);
            others.addAll(players);
            String error = doubles ? PlayerDialog.validatePair(person[0], person[1], others)
                    : PlayerDialog.validate(person[0][0], person[0][1], person[0][2], person[0][3], others);
            if (error != null) {
                errors.add("Zeile " + (i + 1) + ": " + error.replace("\n", " "));
                continue;
            }
            Player a = player(person[0]);
            players.add(doubles ? Player.pair(a, player(person[1])) : a);
        }
        return new ImportResult(players, errors);
    }

    /**
     * Writes a participant list (header line, ";" as separator), readable by {@link #parse}.
     *
     * @param players the participants
     * @return the CSV text
     */
    public static String format(List<Player> players) {
        boolean doubles = players.stream().anyMatch(Player::isDoubles);
        List<String[]> rows = new ArrayList<>();
        rows.add(doubles ? new String[]{"Vorname", "Nachname", "Verein", "TTR", "Vorname", "Nachname", "Verein", "TTR"}
                : new String[]{"Vorname", "Nachname", "Verein", "TTR"});
        for (Player p : players) {
            if (doubles) {
                rows.add(new String[]{p.getFirstName(), p.getLastName(), p.getOwnClub(), String.valueOf(p.getOwnTtr()),
                        p.getPartnerFirstName(), p.getPartnerLastName(), p.getPartnerClub(), String.valueOf(p.getPartnerTtr())});
            } else {
                rows.add(new String[]{p.getFirstName(), p.getLastName(), p.getClub(), String.valueOf(p.getTtr())});
            }
        }
        return toCsv(rows);
    }

    /**
     * Joins rows to CSV with ";" as separator (Excel in German settings); fields with separator,
     * quote or line break are quoted.
     *
     * @param rows the rows
     * @return the CSV text, lines separated by CRLF
     */
    public static String toCsv(List<String[]> rows) {
        StringBuilder sb = new StringBuilder();
        for (String[] row : rows) {
            for (int i = 0; i < row.length; i++) {
                if (i > 0) sb.append(';');
                String v = row[i] == null ? "" : row[i];
                if (v.contains(";") || v.contains("\"") || v.contains("\n") || v.contains("\r")) {
                    v = "\"" + v.replace("\"", "\"\"") + "\"";
                }
                sb.append(v);
            }
            sb.append("\r\n");
        }
        return sb.toString();
    }

    private static Player player(String[] person) {
        String ttr = person[3];
        return new Player(person[0].strip(), person[1].strip(), person[2].strip(), ttr.isEmpty() ? 0 : Integer.parseInt(ttr));
    }

    private static String field(List<String> fields, int index) {
        // non-breaking spaces (Excel) count as spaces
        return index >= 0 && index < fields.size() ? fields.get(index).replace('\u00A0', ' ').strip() : "";
    }

    /** TTR as written by Excel: "1.234" or "1 234" become "1234". */
    private static String normalizeTtr(String ttr) {
        return ttr.matches("\\d{1,3}([. ]\\d{3})+") ? ttr.replaceAll("[. ]", "") : ttr;
    }

    private static char detectSeparator(String line) {
        char best = ';';
        long bestCount = -1;
        for (char c : new char[]{';', '\t', ','}) {
            long count = line.chars().filter(ch -> ch == c).count();
            if (count > bestCount) {
                best = c;
                bestCount = count;
            }
        }
        return best;
    }

    private static boolean isHeader(List<String> fields) {
        return fields.stream().map(ParticipantCsv::key)
                .anyMatch(k -> k.equals("vorname") || k.equals("nachname") || k.equals("name"));
    }

    /** Without header: a doubles file has a first and last name of a second person in fields 5 and 6. */
    private static boolean hasPartnerColumns(String[] lines, int first, char separator) {
        for (int i = first; i < lines.length; i++) {
            List<String> fields = split(lines[i], separator);
            // names contain letters; extra columns like year of birth or licence number do not count
            if (field(fields, 4).matches(".*\\p{L}.*") && field(fields, 5).matches(".*\\p{L}.*")) return true;
        }
        return false;
    }

    /**
     * Index of the n-th (person) column with one of the names, -1 if missing: first the n-th
     * column with the same name (e.g. "TTR" and "Q-TTR" both per person), otherwise the n-th
     * column with any of the names (e.g. "Nachname" for the first and "Name" for the second person).
     */
    private static int column(List<String> header, int person, String... names) {
        for (String name : names) {
            int seen = 0;
            for (int i = 0; i < header.size(); i++) {
                if (key(header.get(i)).equals(name) && seen++ == person) return i;
            }
        }
        int seen = 0;
        for (int i = 0; i < header.size(); i++) {
            String k = key(header.get(i));
            for (String name : names) {
                if (k.equals(name)) {
                    if (seen++ == person) return i;
                    break;
                }
            }
        }
        return -1;
    }

    private static String key(String headerField) {
        String k = headerField.strip().toLowerCase(Locale.ROOT).replaceAll("[\\s_\\-.]", "");
        return k.replaceAll("\\d+$", ""); // "Vorname 2" counts as the second "Vorname"
    }

    /** Splits a CSV line; fields may be quoted ("a;b", "" for a quote). */
    static List<String> split(String line, char separator) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quoted) {
                if (c == '"' && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else if (c == '"') {
                    quoted = false;
                } else {
                    current.append(c);
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == separator) {
                fields.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        fields.add(current.toString());
        return fields;
    }
}
