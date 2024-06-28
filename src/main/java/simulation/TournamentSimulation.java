package simulation;

import controller.TournamentRound;
import model.Player;
import model.Match;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class TournamentSimulation {
    private static final int NUMBER_OF_PLAYERS = 11;
    private static final int NUMBER_OF_SIMULATIONS = 10;
    private static final int MIN_ROUNDS = 6;
    private static final Random rand = new Random();

    public static void main(String[] args) {
        List<Player> players = createPlayers(NUMBER_OF_PLAYERS);

        for (int i = 0; i < NUMBER_OF_SIMULATIONS; i++) {
            TournamentRound tournament = new TournamentRound(players, "Test Turnier", 4, false);
            simulateTournament(tournament);
            if (tournament.getCurrentRound() < MIN_ROUNDS) {
                break;
            }
        }
    }

    private static List<Player> createPlayers(int numberOfPlayers) {
        List<Player> players = new ArrayList<>();
        for (int i = 1; i <= numberOfPlayers; i++) {
            players.add(new Player("Player" + i, "Last" + i, "Club" + i, rand.nextInt(2000) + 1000));
        }
        return players;
    }

    private static void simulateTournament(TournamentRound tournament) {
        while (!tournament.isFinished()) {
            simulateMatches(tournament);

            System.out.println("Runde " + tournament.getCurrentRound() + ":");
            printMatches(tournament);

            tournament.updateResultsTable();
            printCurrentTable(tournament);

            tournament.startNextRound();
        }
    }

    private static void simulateMatches(TournamentRound tournament) {
        for (Match match : tournament.getMatches()) {
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

    private static void printMatches(TournamentRound tournament) {
        for (Match match : tournament.getMatches()) {
            if (match.getSecondPlayer() == null) {
                System.out.println("Freilos: " + match.getFirstPlayer().getFullName());
            } else {
                System.out.println(match.getFirstPlayer().getFullName() + " vs " + match.getSecondPlayer().getFullName() + " - Tisch " + match.getTableNumber());
            }
        }
    }

    private static void printCurrentTable(TournamentRound tournament) {
        System.out.println("Aktuelle Tabelle nach Runde " + tournament.getCurrentRound() + ":");
        List<Player> players = tournament.getPlayerList().reversed();
        for (int i = 0; i < players.size(); i++) {
            Player player = players.get(i);
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
