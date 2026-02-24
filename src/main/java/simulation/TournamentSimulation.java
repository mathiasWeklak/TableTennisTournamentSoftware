package simulation;

import controller.PairingEngine;
import controller.ScoreCalculator;
import model.Player;
import model.Match;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;

public class TournamentSimulation {
    private static final int NUMBER_OF_PLAYERS = 9;
    private static final int NUMBER_OF_SIMULATIONS = 10000;
    private static final int MIN_ROUNDS = 7;
    private static final int TABLE_COUNT = 4;
    private static final Random rand = new Random();

    public static void main(String[] args) {
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
        System.out.println("\n========================================");
        System.out.println("          SIMULATIONSBERICHT            ");
        System.out.println("========================================");
        System.out.println("Simulationen gesamt : " + NUMBER_OF_SIMULATIONS);
        System.out.println("Spieleranzahl       : " + NUMBER_OF_PLAYERS);
        System.out.println("Mindestrunden       : " + MIN_ROUNDS);
        System.out.println("----------------------------------------");
        System.out.println("Erfolgreich         : " + successes + " (" + (successes * 100 / NUMBER_OF_SIMULATIONS) + "%)");
        System.out.println("Fehlgeschlagen      : " + failures  + " (" + (failures  * 100 / NUMBER_OF_SIMULATIONS) + "%)");
        System.out.println("========================================");
    }

    private static int runSimulation(List<Player> players) {
        PairingEngine engine = new PairingEngine(players, TABLE_COUNT, false);
        ScoreCalculator scoreCalculator = new ScoreCalculator(engine.getAllMatches());

        int round = 0;
        String pairings = engine.generatePairings(new HashSet<>(), 1);

        while (pairings != null) {
            round++;
            simulateMatches(engine.getMatches());

            System.out.println("Runde " + round + ":");
            printMatches(engine.getMatches());

            scoreCalculator.calculate(players);
            printCurrentTable(players);

            engine.clearCurrentRound();
            pairings = engine.generatePairings(new HashSet<>(), round + 1);
        }

        return round;
    }

    private static List<Player> createPlayers() {
        List<Player> players = new ArrayList<>();
        for (int i = 1; i <= TournamentSimulation.NUMBER_OF_PLAYERS; i++) {
            players.add(new Player("Player" + i, "Last" + i, "Club" + i, rand.nextInt(2000) + 1000));
        }
        return players;
    }

    private static void simulateMatches(List<Match> matches) {
        for (Match match : matches) {
            if (match.getSecondPlayer() != null) {
                int setsFirstPlayer = rand.nextInt(3) + 1;
                int setsSecondPlayer = setsFirstPlayer == 3 ? rand.nextInt(2) + 1 : 3;

                match.setOverallResult(setsFirstPlayer + ":" + setsSecondPlayer);

                for (int i = 0; i < 5; i++) {
                    String[] setResult = new String[]{String.valueOf(rand.nextInt(11) + 1), String.valueOf(rand.nextInt(11) + 1)};
                    match.setResults(i, setResult);
                }
            }
        }
    }

    private static void printMatches(List<Match> matches) {
        for (Match match : matches) {
            if (match.getSecondPlayer() == null) {
                System.out.println("Freilos: " + match.getFirstPlayer().getFullName());
            } else {
                System.out.println(match.getFirstPlayer().getFullName() + " vs " + match.getSecondPlayer().getFullName() + " - Tisch " + match.getTableNumber());
            }
        }
    }

    private static void printCurrentTable(List<Player> players) {
        List<Player> sorted = new ArrayList<>(players);
        sorted.sort((a, b) -> b.getPoints() - a.getPoints());
        System.out.println("Aktuelle Tabelle:");
        for (int i = 0; i < sorted.size(); i++) {
            Player player = sorted.get(i);
            String output = (i + 1) + ". " + player.getFullName() + " (" + player.getTtr() + " TTR)" +
                    " - Punkte: " + player.getPoints() +
                    ", Spiele: " + player.getWins() + ":" + player.getLosses() +
                    ", Saetze: " + player.getSetsWon() + ":" + player.getSetsLost() +
                    ", Baelle: " + player.getBallsWon() + ":" + player.getBallsLost();
            if (players.size() > 8) {
                output += ", BHZ: " + player.getBuchholz() +
                        ", fBHZ: " + player.getFeinBuchholz();
            }
            System.out.println(output);
        }
    }
}
