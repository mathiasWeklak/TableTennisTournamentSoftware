package simulation;

import controller.PairingEngine;
import controller.ScoreCalculator;
import static model.Match.MAX_SETS;

import model.Match;
import model.Player;
import model.TournamentMode;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Standalone simulation utility for stress-testing the Swiss System pairing algorithm.
 *
 * <p>Runs {@link #NUMBER_OF_SIMULATIONS} independent tournaments with randomly generated
 * players and verifies that each tournament produces at least {@link #MIN_ROUNDS} rounds.
 * Results are printed to standard output along with a summary report.</p>
 *
 * <p>This class is intended for development and QA use only and is not part of the
 * main application flow.</p>
 */
public class TournamentSimulation {
    private static final int NUMBER_OF_PLAYERS = 9;
    private static final int NUMBER_OF_SIMULATIONS = 10000;
    private static final int MIN_ROUNDS = 7;
    private static final int TABLE_COUNT = 4;
    private static final Random rand = new Random();

    /**
     * Entry point for the simulation. Runs all simulations and prints a summary report.
     *
     */
    static void main() {
        int failures = 0;
        for (int i = 0; i < NUMBER_OF_SIMULATIONS; i++) {
            List<Player> players = createPlayers();
            int rounds = runSimulation(players);
            if (rounds < MIN_ROUNDS) {
                failures++;
                System.out.println("FEHLER Simulation " + (i + 1) + ": nur " + rounds + " Runden (erwartet >= " + MIN_ROUNDS + ")");
            } else {
                System.out.println("OK Simulation " + (i + 1) + ": " + rounds + " Runden gespielt");
            }
        }
        int successes = NUMBER_OF_SIMULATIONS - failures;
        System.out.println("""

                ========================================
                          SIMULATIONSBERICHT
                ========================================
                Simulationen gesamt : %d
                Spieleranzahl       : %d
                Mindestrunden       : %d
                ----------------------------------------
                Erfolgreich         : %d (%d%%)
                Fehlgeschlagen      : %d (%d%%)
                ========================================""".formatted(
                NUMBER_OF_SIMULATIONS, NUMBER_OF_PLAYERS, MIN_ROUNDS,
                successes, successes * 100 / NUMBER_OF_SIMULATIONS,
                failures, failures * 100 / NUMBER_OF_SIMULATIONS));
    }

    /**
     * Runs a single simulated tournament for the given players using the Swiss System.
     * Generates pairings, simulates random match results, and calculates scores each round
     * until no further pairings are possible.
     *
     * @param players the list of players participating in the simulated tournament
     * @return the total number of rounds played
     */
    private static int runSimulation(List<Player> players) {
        PairingEngine engine = new PairingEngine(players, TABLE_COUNT, TournamentMode.SWISS);
        ScoreCalculator scoreCalculator = new ScoreCalculator(engine.getAllMatches());

        int round = 0;
        String pairings = engine.generatePairings(1);

        while (pairings != null) {
            round++;
            simulateMatches(engine.getMatches());

            System.out.println("Runde " + round + ":");
            printMatches(engine.getMatches());

            scoreCalculator.calculate(players);
            printCurrentTable(players);

            engine.clearCurrentRound();
            pairings = engine.generatePairings(round + 1);
        }

        return round;
    }

    /**
     * Creates a list of {@link #NUMBER_OF_PLAYERS} randomly generated players,
     * each with a random TTR value between 1000 and 2999.
     *
     * @return the generated player list
     */
    private static List<Player> createPlayers() {
        List<Player> players = new ArrayList<>();
        for (int i = 1; i <= TournamentSimulation.NUMBER_OF_PLAYERS; i++) {
            players.add(new Player("Player" + i, "Last" + i, "Club" + i, rand.nextInt(2000) + 1000));
        }
        return players;
    }

    /**
     * Simulates random results for all non-bye matches in the given list.
     * The winner is assigned 3 sets; the loser receives a random number of sets (1 or 2).
     * Individual set scores are random integers between 1 and 11.
     *
     * @param matches the list of matches to simulate
     */
    private static void simulateMatches(List<Match> matches) {
        for (Match match : matches) {
            if (match.getSecondPlayer() != null) {
                int setsFirstPlayer = rand.nextInt(3) + 1;
                int setsSecondPlayer = setsFirstPlayer == 3 ? rand.nextInt(2) + 1 : 3;

                match.setOverallResult(setsFirstPlayer + ":" + setsSecondPlayer);

                int totalSets = setsFirstPlayer + setsSecondPlayer;
                for (int i = 0; i < totalSets; i++) {
                    boolean firstWinsSet = i < setsFirstPlayer;
                    int loser = rand.nextInt(10);
                    String[] setResult = firstWinsSet
                            ? new String[]{"11", String.valueOf(loser)}
                            : new String[]{String.valueOf(loser), "11"};
                    match.setResults(i, setResult);
                }
                for (int i = totalSets; i < MAX_SETS; i++) {
                    match.setResults(i, new String[]{"", ""});
                }
            }
        }
    }

    /**
     * Prints the current round's matches to standard output.
     *
     * @param matches the list of matches to print
     */
    private static void printMatches(List<Match> matches) {
        for (Match match : matches) {
            if (match.getSecondPlayer() == null) {
                System.out.println("Freilos: " + match.getFirstPlayer().getFullName());
            } else {
                System.out.println(match.getFirstPlayer().getFullName() + " vs " + match.getSecondPlayer().getFullName() + " - Tisch " + match.getTableNumber());
            }
        }
    }

    /**
     * Prints the current standings table to standard output, sorted by points descending.
     * Includes Buchholz tiebreakers when the player count exceeds 8.
     *
     * @param players the list of players to include in the standings
     */
    private static void printCurrentTable(List<Player> players) {
        var sorted = new ArrayList<>(players);
        sorted.sort((a, b) -> b.getPoints() - a.getPoints());
        System.out.println("Aktuelle Tabelle:");
        for (int i = 0; i < sorted.size(); i++) {
            Player player = sorted.get(i);
            String tiebreakers = players.size() > 8
                    ? ", BHZ: %d, fBHZ: %d".formatted(player.getBuchholz(), player.getFeinBuchholz())
                    : "";
            System.out.println("%d. %s (%d TTR) - Punkte: %d, Spiele: %d:%d, Saetze: %d:%d, Baelle: %d:%d%s".formatted(
                    i + 1, player.getFullName(), player.getTtr(),
                    player.getPoints(), player.getWins(), player.getLosses(),
                    player.getSetsWon(), player.getSetsLost(),
                    player.getBallsWon(), player.getBallsLost(),
                    tiebreakers));
        }
    }
}
