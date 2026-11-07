package controller;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Compares the blossom implementation with a brute-force search on many random graphs.
 */
public class WeightedMatchingTest {

    /** {cardinality, weight} of the best matching found by brute force. */
    private static long[] bestByBruteForce(int n, boolean[][] adj, long[][] w, boolean maxCardinality) {
        long[] best = {-1, Long.MIN_VALUE};
        search(0, n, adj, w, new boolean[n], 0, 0, best, maxCardinality);
        return best;
    }

    private static void search(int v, int n, boolean[][] adj, long[][] w, boolean[] used,
                               long card, long weight, long[] best, boolean maxCardinality) {
        while (v < n && used[v]) v++;
        if (v == n) {
            boolean better = maxCardinality
                    ? (card > best[0] || (card == best[0] && weight > best[1]))
                    : weight > best[1];
            if (better) {
                best[0] = card;
                best[1] = weight;
            }
            return;
        }
        used[v] = true;
        search(v + 1, n, adj, w, used, card, weight, best, maxCardinality); // v stays unmatched
        for (int u = v + 1; u < n; u++) {
            if (!used[u] && adj[v][u]) {
                used[u] = true;
                search(v + 1, n, adj, w, used, card + 1, weight + w[v][u], best, maxCardinality);
                used[u] = false;
            }
        }
        used[v] = false;
    }

    private static void checkRandomGraph(Random rnd, int n, double density, int maxWeight, boolean maxCardinality) {
        boolean[][] adj = new boolean[n][n];
        long[][] w = new long[n][n];
        List<int[]> edges = new ArrayList<>();
        List<Long> weights = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                if (rnd.nextDouble() < density) {
                    long weight = 1 + rnd.nextInt(maxWeight);
                    adj[i][j] = adj[j][i] = true;
                    w[i][j] = w[j][i] = weight;
                    edges.add(new int[]{i, j});
                    weights.add(weight);
                }
            }
        }
        long[] wArr = weights.stream().mapToLong(Long::longValue).toArray();
        int[] mate = WeightedMatching.maxWeightMatching(n, edges.toArray(new int[0][]), wArr, maxCardinality);

        long card = 0;
        long weight = 0;
        for (int v = 0; v < n; v++) {
            if (mate[v] >= 0) {
                assertEquals(v, mate[mate[v]], "Matching nicht symmetrisch");
                assertTrue(adj[v][mate[v]], "Kante existiert nicht");
                if (v < mate[v]) {
                    card++;
                    weight += w[v][mate[v]];
                }
            }
        }
        long[] best = bestByBruteForce(n, adj, w, maxCardinality);
        String info = "n=" + n + " edges=" + edges.size() + " maxCard=" + maxCardinality + " mate=" + Arrays.toString(mate);
        if (maxCardinality) {
            assertEquals(best[0], card, "Kardinalität " + info);
        }
        assertEquals(best[1], weight, "Gewicht " + info);
    }

    @Test
    public void randomGraphs_matchBruteForce() {
        Random rnd = new Random(12345);
        for (int iter = 0; iter < 3000; iter++) {
            int n = 2 + rnd.nextInt(9);           // 2..10 vertices
            double density = 0.2 + rnd.nextDouble() * 0.8;
            int maxWeight = rnd.nextBoolean() ? 3 : 1000;   // many ties vs. distinct weights
            checkRandomGraph(rnd, n, density, maxWeight, iter % 2 == 0);
        }
    }

    @Test
    public void randomGraphs_largeWeightsLikePairingCosts() {
        Random rnd = new Random(777);
        for (int iter = 0; iter < 500; iter++) {
            int n = 4 + rnd.nextInt(7);
            checkRandomGraph(rnd, n, 0.7, 1_000_000_000, true);
        }
    }

    @Test
    public void emptyGraph() {
        int[] mate = WeightedMatching.maxWeightMatching(3, new int[0][], new long[0], true);
        assertArrayEquals(new int[]{-1, -1, -1}, mate);
    }

    @Test
    public void maxCardinalityPrefersMoreEdgesOverWeight() {
        // path 0-1-2-3: middle edge heavy; max cardinality must take the two outer edges
        int[][] edges = {{0, 1}, {1, 2}, {2, 3}};
        long[] w = {1, 100, 1};
        assertArrayEquals(new int[]{1, 0, 3, 2}, WeightedMatching.maxWeightMatching(4, edges, w, true));
        assertArrayEquals(new int[]{-1, 2, 1, -1}, WeightedMatching.maxWeightMatching(4, edges, w, false));
    }

    @Test
    public void largeCompleteGraphIsFast() {
        assertTimeoutPreemptively(java.time.Duration.ofSeconds(10), () -> {
            Random rnd = new Random(5);
            int n = 150;
            List<int[]> edges = new ArrayList<>();
            List<Long> weights = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                for (int j = i + 1; j < n; j++) {
                    edges.add(new int[]{i, j});
                    weights.add((long) rnd.nextInt(1_000_000));
                }
            }
            int[] mate = WeightedMatching.maxWeightMatching(n, edges.toArray(new int[0][]),
                    weights.stream().mapToLong(Long::longValue).toArray(), true);
            for (int v = 0; v < n; v++) assertTrue(mate[v] >= 0);
        });
    }
}
