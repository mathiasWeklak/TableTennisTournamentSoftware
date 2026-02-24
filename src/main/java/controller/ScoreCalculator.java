package controller;

import model.Match;
import model.Player;

import java.util.*;

public record ScoreCalculator(List<Match> allMatches) {

    public void calculate(List<Player> players) {
        players.forEach(player -> {
            player.setPoints(0);
            player.setWins(0);
            player.setLosses(0);
            player.setSetsWon(0);
            player.setSetsLost(0);
            player.setBallsWon(0);
            player.setBallsLost(0);
            player.setBuchholz(0);
            player.setFeinBuchholz(0);
        });

        removeDuplicateByeMatches();
        allMatches.forEach(match -> match.setEvaluated(false));

        for (Match match : allMatches) {
            if (match.isEvaluated()) continue;

            Player p1 = match.getFirstPlayer();
            Player p2 = match.getSecondPlayer();

            if (p2 == null) {
                p1.setPoints(p1.getPoints() + 1);
                p1.setWins(p1.getWins() + 1);
                p1.setSetsWon(p1.getSetsWon() + 3);
                p1.setBallsWon(p1.getBallsWon() + 33);
            } else {
                String[] result = match.getOverallResult().split(":");
                if (result.length == 2) {
                    int s1 = Integer.parseInt(result[0]);
                    int s2 = Integer.parseInt(result[1]);

                    if (s1 > s2) {
                        p1.setPoints(p1.getPoints() + 1);
                        p1.setWins(p1.getWins() + 1);
                        p2.setLosses(p2.getLosses() + 1);
                    } else if (s2 > s1) {
                        p2.setPoints(p2.getPoints() + 1);
                        p2.setWins(p2.getWins() + 1);
                        p1.setLosses(p1.getLosses() + 1);
                    }

                    p1.setSetsWon(p1.getSetsWon() + s1);
                    p1.setSetsLost(p1.getSetsLost() + s2);
                    p2.setSetsWon(p2.getSetsWon() + s2);
                    p2.setSetsLost(p2.getSetsLost() + s1);

                    String[][] results = match.getResults();
                    for (String[] set : results) {
                        if (set[0] != null && !set[0].isEmpty() && set[1] != null && !set[1].isEmpty()) {
                            int b1 = Integer.parseInt(set[0]);
                            int b2 = Integer.parseInt(set[1]);
                            p1.setBallsWon(p1.getBallsWon() + b1);
                            p1.setBallsLost(p1.getBallsLost() + b2);
                            p2.setBallsWon(p2.getBallsWon() + b2);
                            p2.setBallsLost(p2.getBallsLost() + b1);
                        }
                    }
                }
            }

            match.setEvaluated(true);
        }

        players.forEach(p -> p.setBuchholz(calculateBuchholz(p)));
        players.forEach(p -> p.setFeinBuchholz(calculateFeinBuchholz(p)));
    }

    private void removeDuplicateByeMatches() {
        Set<Player> seenPlayers = new HashSet<>();
        Iterator<Match> iterator = allMatches.iterator();

        while (iterator.hasNext()) {
            Match match = iterator.next();
            if (match.getSecondPlayer() == null) {
                Player byePlayer = match.getFirstPlayer();
                if (!seenPlayers.add(byePlayer)) {
                    iterator.remove();
                }
            }
        }
    }

    private int calculateBuchholz(Player player) {
        return allMatches.stream()
                .filter(match -> isPlayerInMatch(match, player) && match.getSecondPlayer() != null)
                .mapToInt(match -> getOpponentPoints(match, player))
                .sum();
    }

    private int calculateFeinBuchholz(Player player) {
        return allMatches.stream()
                .filter(match -> isPlayerInMatch(match, player) && match.getSecondPlayer() != null)
                .mapToInt(match -> getOpponentBuchholz(match, player))
                .sum();
    }

    private boolean isPlayerInMatch(Match match, Player player) {
        return match.getFirstPlayer().equals(player)
                || (match.getSecondPlayer() != null && match.getSecondPlayer().equals(player));
    }

    private int getOpponentPoints(Match match, Player player) {
        return match.getFirstPlayer().equals(player)
                ? match.getSecondPlayer().getPoints()
                : match.getFirstPlayer().getPoints();
    }

    private int getOpponentBuchholz(Match match, Player player) {
        return match.getFirstPlayer().equals(player)
                ? match.getSecondPlayer().getBuchholz()
                : match.getFirstPlayer().getBuchholz();
    }
}
