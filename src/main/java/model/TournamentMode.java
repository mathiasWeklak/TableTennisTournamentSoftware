package model;

/**
 * The tournament format.
 */
public enum TournamentMode {
    /** Swiss System. */
    SWISS,
    /** Round Robin ("Jeder gegen Jeden"). */
    ROUND_ROBIN,
    /** Group stage (round robin within groups) followed by a knockout stage. */
    GROUPS_KO
}
