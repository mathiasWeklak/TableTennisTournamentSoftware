package view;

/**
 * Status of a pairing in the pairing table, shown as a coloured label.
 *
 * @param text the text, e.g. "3:1", "offen", "wartet"
 * @param kind the kind, decides the colour
 */
public record PairingStatus(String text, Kind kind) {

    /** Kinds of status. */
    public enum Kind {
        /** Not played yet. */
        OPEN,
        /** Decided. */
        DONE,
        /** Waits for a free table. */
        WAITING,
        /** Struck (withdrawal). */
        STRUCK,
        /** Bye or no match. */
        BYE
    }

    @Override
    public String toString() {
        return text;
    }
}
