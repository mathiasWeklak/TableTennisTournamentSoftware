package controller;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Maximum weight matching in a general (non-bipartite) graph, Edmonds' blossom algorithm with
 * dual variables, O(n^3).
 *
 * <p>Port of the well-known reference implementation "mwmatching.py" by Joris van Rantwijk
 * (public domain), restricted to integer weights. With {@code maxCardinality = true} the result
 * is a maximum-weight matching among all matchings of maximum cardinality; in particular it is
 * a perfect matching whenever one exists.</p>
 */
public final class WeightedMatching {

    private final int nVertex;
    private final int nEdge;
    private final int[] edgeI;
    private final int[] edgeJ;
    private final long[] edgeW;
    private final boolean maxCardinality;

    private final int[] endpoint;
    private final int[][] neighbEnd;
    private final int[] mate;
    private final int[] label;
    private final int[] labelEnd;
    private final int[] inBlossom;
    private final int[] blossomParent;
    private final int[][] blossomChilds;
    private final int[] blossomBase;
    private final int[][] blossomEndps;
    private final int[] bestEdge;
    private final int[][] blossomBestEdges;
    private final List<Integer> unusedBlossoms = new ArrayList<>();
    private final long[] dualVar;
    private final boolean[] allowEdge;
    private final List<Integer> queue = new ArrayList<>();

    /**
     * Computes a maximum weight matching.
     *
     * @param nVertex        number of vertices (0 .. nVertex-1)
     * @param edges          edges as {@code {i, j}} pairs, i != j, no duplicates
     * @param weights        integer weight per edge
     * @param maxCardinality {@code true}: only consider matchings of maximum cardinality
     * @return {@code mate[v]} = matched partner of v, or -1 if unmatched
     */
    public static int[] maxWeightMatching(int nVertex, int[][] edges, long[] weights, boolean maxCardinality) {
        if (edges.length == 0) {
            int[] none = new int[nVertex];
            Arrays.fill(none, -1);
            return none;
        }
        return new WeightedMatching(nVertex, edges, weights, maxCardinality).solve();
    }

    private WeightedMatching(int nVertex, int[][] edges, long[] weights, boolean maxCardinality) {
        this.nVertex = nVertex;
        this.nEdge = edges.length;
        this.maxCardinality = maxCardinality;
        this.edgeI = new int[nEdge];
        this.edgeJ = new int[nEdge];
        this.edgeW = weights.clone();
        long maxWeight = 0;
        for (int k = 0; k < nEdge; k++) {
            edgeI[k] = edges[k][0];
            edgeJ[k] = edges[k][1];
            maxWeight = Math.max(maxWeight, weights[k]);
        }

        endpoint = new int[2 * nEdge];
        for (int p = 0; p < 2 * nEdge; p++) {
            endpoint[p] = (p % 2 == 0) ? edgeI[p / 2] : edgeJ[p / 2];
        }
        List<List<Integer>> nb = new ArrayList<>();
        for (int v = 0; v < nVertex; v++) nb.add(new ArrayList<>());
        for (int k = 0; k < nEdge; k++) {
            nb.get(edgeI[k]).add(2 * k + 1);
            nb.get(edgeJ[k]).add(2 * k);
        }
        neighbEnd = new int[nVertex][];
        for (int v = 0; v < nVertex; v++) {
            neighbEnd[v] = nb.get(v).stream().mapToInt(Integer::intValue).toArray();
        }

        mate = new int[nVertex];
        Arrays.fill(mate, -1);
        label = new int[2 * nVertex];
        labelEnd = new int[2 * nVertex];
        Arrays.fill(labelEnd, -1);
        inBlossom = new int[nVertex];
        for (int v = 0; v < nVertex; v++) inBlossom[v] = v;
        blossomParent = new int[2 * nVertex];
        Arrays.fill(blossomParent, -1);
        blossomChilds = new int[2 * nVertex][];
        blossomBase = new int[2 * nVertex];
        for (int v = 0; v < nVertex; v++) blossomBase[v] = v;
        for (int b = nVertex; b < 2 * nVertex; b++) blossomBase[b] = -1;
        blossomEndps = new int[2 * nVertex][];
        bestEdge = new int[2 * nVertex];
        Arrays.fill(bestEdge, -1);
        blossomBestEdges = new int[2 * nVertex][];
        for (int b = nVertex; b < 2 * nVertex; b++) unusedBlossoms.add(b);
        dualVar = new long[2 * nVertex];
        for (int v = 0; v < nVertex; v++) dualVar[v] = maxWeight;
        allowEdge = new boolean[nEdge];
    }

    // ---------------------------------------------------------------------------------------

    private static int idx(int j, int len) {
        return ((j % len) + len) % len; // python-style negative index
    }

    private long slack(int k) {
        return dualVar[edgeI[k]] + dualVar[edgeJ[k]] - 2 * edgeW[k];
    }

    private List<Integer> blossomLeaves(int b) {
        List<Integer> leaves = new ArrayList<>();
        collectLeaves(b, leaves);
        return leaves;
    }

    private void collectLeaves(int b, List<Integer> out) {
        if (b < nVertex) {
            out.add(b);
        } else {
            for (int t : blossomChilds[b]) collectLeaves(t, out);
        }
    }

    private void assignLabel(int w, int t, int p) {
        int b = inBlossom[w];
        label[w] = label[b] = t;
        labelEnd[w] = labelEnd[b] = p;
        bestEdge[w] = bestEdge[b] = -1;
        if (t == 1) {
            queue.addAll(blossomLeaves(b));
        } else if (t == 2) {
            int base = blossomBase[b];
            assignLabel(endpoint[mate[base]], 1, mate[base] ^ 1);
        }
    }

    private int scanBlossom(int v, int w) {
        List<Integer> path = new ArrayList<>();
        int base = -1;
        while (v != -1 || w != -1) {
            int b = inBlossom[v];
            if ((label[b] & 4) != 0) {
                base = blossomBase[b];
                break;
            }
            path.add(b);
            label[b] = 5;
            if (labelEnd[b] == -1) {
                v = -1;
            } else {
                v = endpoint[labelEnd[b]];
                b = inBlossom[v];
                v = endpoint[labelEnd[b]];
            }
            if (w != -1) {
                int tmp = v;
                v = w;
                w = tmp;
            }
        }
        for (int b : path) label[b] = 1;
        return base;
    }

    private void addBlossom(int base, int k) {
        int v = edgeI[k];
        int w = edgeJ[k];
        int bb = inBlossom[base];
        int bv = inBlossom[v];
        int bw = inBlossom[w];
        int b = unusedBlossoms.removeLast();
        blossomBase[b] = base;
        blossomParent[b] = -1;
        blossomParent[bb] = b;
        List<Integer> path = new ArrayList<>();
        List<Integer> endps = new ArrayList<>();
        while (bv != bb) {
            blossomParent[bv] = b;
            path.add(bv);
            endps.add(labelEnd[bv]);
            v = endpoint[labelEnd[bv]];
            bv = inBlossom[v];
        }
        path.add(bb);
        java.util.Collections.reverse(path);
        java.util.Collections.reverse(endps);
        endps.add(2 * k);
        while (bw != bb) {
            blossomParent[bw] = b;
            path.add(bw);
            endps.add(labelEnd[bw] ^ 1);
            w = endpoint[labelEnd[bw]];
            bw = inBlossom[w];
        }
        blossomChilds[b] = path.stream().mapToInt(Integer::intValue).toArray();
        blossomEndps[b] = endps.stream().mapToInt(Integer::intValue).toArray();
        label[b] = 1;
        labelEnd[b] = labelEnd[bb];
        dualVar[b] = 0;
        for (int leaf : blossomLeaves(b)) {
            if (label[inBlossom[leaf]] == 2) {
                queue.add(leaf);
            }
            inBlossom[leaf] = b;
        }
        int[] bestEdgeTo = new int[2 * nVertex];
        Arrays.fill(bestEdgeTo, -1);
        for (int child : blossomChilds[b]) {
            List<int[]> nbLists = new ArrayList<>();
            if (blossomBestEdges[child] == null) {
                for (int leaf : blossomLeaves(child)) {
                    int[] list = new int[neighbEnd[leaf].length];
                    for (int x = 0; x < list.length; x++) list[x] = neighbEnd[leaf][x] / 2;
                    nbLists.add(list);
                }
            } else {
                nbLists.add(blossomBestEdges[child]);
            }
            for (int[] nbList : nbLists) {
                for (int kk : nbList) {
                    int i = edgeI[kk];
                    int j = edgeJ[kk];
                    if (inBlossom[j] == b) {
                        int tmp = i;
                        i = j;
                        j = tmp;
                    }
                    int bj = inBlossom[j];
                    if (bj != b && label[bj] == 1
                            && (bestEdgeTo[bj] == -1 || slack(kk) < slack(bestEdgeTo[bj]))) {
                        bestEdgeTo[bj] = kk;
                    }
                }
            }
            blossomBestEdges[child] = null;
            bestEdge[child] = -1;
        }
        blossomBestEdges[b] = Arrays.stream(bestEdgeTo).filter(x -> x != -1).toArray();
        bestEdge[b] = -1;
        for (int kk : blossomBestEdges[b]) {
            if (bestEdge[b] == -1 || slack(kk) < slack(bestEdge[b])) {
                bestEdge[b] = kk;
            }
        }
    }

    private void expandBlossom(int b, boolean endStage) {
        for (int s : blossomChilds[b]) {
            blossomParent[s] = -1;
            if (s < nVertex) {
                inBlossom[s] = s;
            } else if (endStage && dualVar[s] == 0) {
                expandBlossom(s, endStage);
            } else {
                for (int leaf : blossomLeaves(s)) inBlossom[leaf] = s;
            }
        }
        if (!endStage && label[b] == 2) {
            int[] childs = blossomChilds[b];
            int[] endps = blossomEndps[b];
            int len = childs.length;
            int entryChild = inBlossom[endpoint[labelEnd[b] ^ 1]];
            int j = indexOf(childs, entryChild);
            int jStep;
            int endpTrick;
            if ((j & 1) != 0) {
                j -= len;
                jStep = 1;
                endpTrick = 0;
            } else {
                jStep = -1;
                endpTrick = 1;
            }
            int p = labelEnd[b];
            while (j != 0) {
                label[endpoint[p ^ 1]] = 0;
                label[endpoint[endps[idx(j - endpTrick, len)] ^ endpTrick ^ 1]] = 0;
                assignLabel(endpoint[p ^ 1], 2, p);
                allowEdge[endps[idx(j - endpTrick, len)] / 2] = true;
                j += jStep;
                p = endps[idx(j - endpTrick, len)] ^ endpTrick;
                allowEdge[p / 2] = true;
                j += jStep;
            }
            int bv = childs[idx(j, len)];
            label[endpoint[p ^ 1]] = label[bv] = 2;
            labelEnd[endpoint[p ^ 1]] = labelEnd[bv] = p;
            bestEdge[bv] = -1;
            j += jStep;
            while (childs[idx(j, len)] != entryChild) {
                bv = childs[idx(j, len)];
                if (label[bv] == 1) {
                    j += jStep;
                    continue;
                }
                int found = -1;
                for (int leaf : blossomLeaves(bv)) {
                    if (label[leaf] != 0) {
                        found = leaf;
                        break;
                    }
                }
                if (found != -1) {
                    label[found] = 0;
                    label[endpoint[mate[blossomBase[bv]]]] = 0;
                    assignLabel(found, 2, labelEnd[found]);
                }
                j += jStep;
            }
        }
        label[b] = labelEnd[b] = -1;
        blossomChilds[b] = null;
        blossomEndps[b] = null;
        blossomBase[b] = -1;
        blossomBestEdges[b] = null;
        bestEdge[b] = -1;
        unusedBlossoms.add(b);
    }

    private void augmentBlossom(int b, int v) {
        int t = v;
        while (blossomParent[t] != b) t = blossomParent[t];
        if (t >= nVertex) augmentBlossom(t, v);
        int[] childs = blossomChilds[b];
        int[] endps = blossomEndps[b];
        int len = childs.length;
        int i = indexOf(childs, t);
        int j = i;
        int jStep;
        int endpTrick;
        if ((i & 1) != 0) {
            j -= len;
            jStep = 1;
            endpTrick = 0;
        } else {
            jStep = -1;
            endpTrick = 1;
        }
        while (j != 0) {
            j += jStep;
            t = childs[idx(j, len)];
            int p = endps[idx(j - endpTrick, len)] ^ endpTrick;
            if (t >= nVertex) augmentBlossom(t, endpoint[p]);
            j += jStep;
            t = childs[idx(j, len)];
            if (t >= nVertex) augmentBlossom(t, endpoint[p ^ 1]);
            mate[endpoint[p]] = p ^ 1;
            mate[endpoint[p ^ 1]] = p;
        }
        blossomChilds[b] = rotate(childs, i);
        blossomEndps[b] = rotate(endps, i);
        blossomBase[b] = blossomBase[blossomChilds[b][0]];
    }

    private void augmentMatching(int k) {
        int[][] starts = {{edgeI[k], 2 * k + 1}, {edgeJ[k], 2 * k}};
        for (int[] start : starts) {
            int s = start[0];
            int p = start[1];
            while (true) {
                int bs = inBlossom[s];
                if (bs >= nVertex) augmentBlossom(bs, s);
                mate[s] = p;
                if (labelEnd[bs] == -1) break;
                int t = endpoint[labelEnd[bs]];
                int bt = inBlossom[t];
                s = endpoint[labelEnd[bt]];
                int j = endpoint[labelEnd[bt] ^ 1];
                if (bt >= nVertex) augmentBlossom(bt, j);
                mate[j] = labelEnd[bt];
                p = labelEnd[bt] ^ 1;
            }
        }
    }

    private int[] solve() {
        for (int t = 0; t < nVertex; t++) {
            Arrays.fill(label, 0);
            Arrays.fill(bestEdge, -1);
            for (int b = nVertex; b < 2 * nVertex; b++) blossomBestEdges[b] = null;
            Arrays.fill(allowEdge, false);
            queue.clear();
            for (int v = 0; v < nVertex; v++) {
                if (mate[v] == -1 && label[inBlossom[v]] == 0) assignLabel(v, 1, -1);
            }
            boolean augmented = false;
            while (true) {
                while (!queue.isEmpty() && !augmented) {
                    int v = queue.removeLast();
                    for (int p : neighbEnd[v]) {
                        int k = p / 2;
                        int w = endpoint[p];
                        if (inBlossom[v] == inBlossom[w]) continue;
                        long kSlack = 0;
                        if (!allowEdge[k]) {
                            kSlack = slack(k);
                            if (kSlack <= 0) allowEdge[k] = true;
                        }
                        if (allowEdge[k]) {
                            if (label[inBlossom[w]] == 0) {
                                assignLabel(w, 2, p ^ 1);
                            } else if (label[inBlossom[w]] == 1) {
                                int base = scanBlossom(v, w);
                                if (base >= 0) {
                                    addBlossom(base, k);
                                } else {
                                    augmentMatching(k);
                                    augmented = true;
                                    break;
                                }
                            } else if (label[w] == 0) {
                                label[w] = 2;
                                labelEnd[w] = p ^ 1;
                            }
                        } else if (label[inBlossom[w]] == 1) {
                            int b = inBlossom[v];
                            if (bestEdge[b] == -1 || kSlack < slack(bestEdge[b])) bestEdge[b] = k;
                        } else if (label[w] == 0) {
                            if (bestEdge[w] == -1 || kSlack < slack(bestEdge[w])) bestEdge[w] = k;
                        }
                    }
                }
                if (augmented) break;

                int deltaType = -1;
                long delta = 0;
                int deltaEdge = -1;
                int deltaBlossom = -1;
                if (!maxCardinality) {
                    deltaType = 1;
                    delta = Long.MAX_VALUE;
                    for (int v = 0; v < nVertex; v++) delta = Math.min(delta, dualVar[v]);
                }
                for (int v = 0; v < nVertex; v++) {
                    if (label[inBlossom[v]] == 0 && bestEdge[v] != -1) {
                        long d = slack(bestEdge[v]);
                        if (deltaType == -1 || d < delta) {
                            delta = d;
                            deltaType = 2;
                            deltaEdge = bestEdge[v];
                        }
                    }
                }
                for (int b = 0; b < 2 * nVertex; b++) {
                    if (blossomParent[b] == -1 && label[b] == 1 && bestEdge[b] != -1) {
                        long kSlack = slack(bestEdge[b]);
                        long d = kSlack / 2; // integer weights: slack between S-vertices is even
                        if (deltaType == -1 || d < delta) {
                            delta = d;
                            deltaType = 3;
                            deltaEdge = bestEdge[b];
                        }
                    }
                }
                for (int b = nVertex; b < 2 * nVertex; b++) {
                    if (blossomBase[b] >= 0 && blossomParent[b] == -1 && label[b] == 2
                            && (deltaType == -1 || dualVar[b] < delta)) {
                        delta = dualVar[b];
                        deltaType = 4;
                        deltaBlossom = b;
                    }
                }
                if (deltaType == -1) {
                    deltaType = 1;
                    long min = Long.MAX_VALUE;
                    for (int v = 0; v < nVertex; v++) min = Math.min(min, dualVar[v]);
                    delta = Math.max(0, min);
                }

                for (int v = 0; v < nVertex; v++) {
                    if (label[inBlossom[v]] == 1) {
                        dualVar[v] -= delta;
                    } else if (label[inBlossom[v]] == 2) {
                        dualVar[v] += delta;
                    }
                }
                for (int b = nVertex; b < 2 * nVertex; b++) {
                    if (blossomBase[b] >= 0 && blossomParent[b] == -1) {
                        if (label[b] == 1) {
                            dualVar[b] += delta;
                        } else if (label[b] == 2) {
                            dualVar[b] -= delta;
                        }
                    }
                }

                if (deltaType == 1) {
                    break;
                } else if (deltaType == 2) {
                    allowEdge[deltaEdge] = true;
                    int i = edgeI[deltaEdge];
                    int j = edgeJ[deltaEdge];
                    if (label[inBlossom[i]] == 0) {
                        i = j;
                    }
                    queue.add(i);
                } else if (deltaType == 3) {
                    allowEdge[deltaEdge] = true;
                    queue.add(edgeI[deltaEdge]);
                } else {
                    expandBlossom(deltaBlossom, false);
                }
            }
            if (!augmented) break;
            for (int b = nVertex; b < 2 * nVertex; b++) {
                if (blossomParent[b] == -1 && blossomBase[b] >= 0 && label[b] == 1 && dualVar[b] == 0) {
                    expandBlossom(b, true);
                }
            }
        }
        int[] result = new int[nVertex];
        for (int v = 0; v < nVertex; v++) {
            result[v] = mate[v] >= 0 ? endpoint[mate[v]] : -1;
        }
        return result;
    }

    private static int indexOf(int[] array, int value) {
        for (int i = 0; i < array.length; i++) {
            if (array[i] == value) return i;
        }
        throw new IllegalStateException("blossom child not found");
    }

    private static int[] rotate(int[] array, int start) {
        int[] r = new int[array.length];
        for (int x = 0; x < array.length; x++) r[x] = array[(start + x) % array.length];
        return r;
    }
}
