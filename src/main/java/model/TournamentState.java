package model;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * Represents a serializable snapshot of the complete state of a tournament.
 *
 * <p>This record is used for saving and restoring the tournament session,
 * including all relevant data such as players, match history, current round,
 * tournament configuration, and the current round's active pairings.</p>
 *
 * <p>It is intended for use with Java serialization (via {@link java.io.ObjectOutputStream})
 * and can be deserialized to fully reconstruct a {@link controller.TournamentRound}
 * using {@code fromSavedState()}.</p>
 *
 * @param playerList     the list of all players in the tournament
 * @param allMatches     the list of all matches that have been played or scheduled
 * @param matches        the current round's match pairings
 * @param currentRound   the current round number of the tournament
 * @param finished       whether the tournament is considered completed
 * @param tournamentName the name of the tournament
 * @param tableCount     the number of available tables for match scheduling
 * @param mode           the tournament mode (SWISS or ROUND_ROBIN)
 */
public record TournamentState(
        List<Player> playerList,
        List<Match> allMatches,
        List<Match> matches,
        int currentRound,
        boolean finished,
        String tournamentName,
        int tableCount,
        TournamentMode mode
) implements Serializable {
    @Serial
    private static final long serialVersionUID = 2L;
}
