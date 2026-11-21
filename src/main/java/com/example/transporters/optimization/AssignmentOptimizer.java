package com.example.transporters.optimization;

import com.example.transporters.api.ApiException;
import com.example.transporters.api.AssignmentResult;
import com.example.transporters.api.AssignmentResult.Assignment;
import com.example.transporters.api.InputData;
import com.example.transporters.api.InputData.Lane;
import com.example.transporters.api.InputData.Transporter;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;

/** Maximizes feasible transporter usage within the limit, then minimizes total cost. */
public final class AssignmentOptimizer {
    private static final long UNREACHABLE = Long.MAX_VALUE;

    public AssignmentResult optimize(InputData data, int maxTransporters) {
        List<Lane> lanes = data.getLanes().stream()
                .sorted(Comparator.comparing(Lane::getId)).collect(Collectors.toList());
        List<Transporter> transporters = data.getTransporters().stream()
                .sorted(Comparator.comparing(Transporter::getId)).collect(Collectors.toList());
        int limit = Math.min(maxTransporters, Math.min(lanes.size(), transporters.size()));
        int stateCount = 1 << transporters.size();
        long[][] quotes = quoteMatrix(lanes, transporters);

        long[] previousCosts = new long[stateCount];
        Arrays.fill(previousCosts, UNREACHABLE);
        previousCosts[0] = 0;
        byte[][] predecessors = new byte[lanes.size()][stateCount];

        for (int lane = 0; lane < lanes.size(); lane++) {
            long[] nextCosts = new long[stateCount];
            Arrays.fill(nextCosts, UNREACHABLE);
            for (int previousMask = 0; previousMask < stateCount; previousMask++) {
                if (previousCosts[previousMask] == UNREACHABLE) {
                    continue;
                }
                for (int transporter = 0; transporter < transporters.size(); transporter++) {
                    if (quotes[lane][transporter] == UNREACHABLE) {
                        continue;
                    }
                    int nextMask = previousMask | (1 << transporter);
                    if (Integer.bitCount(nextMask) > limit) {
                        continue;
                    }
                    long candidateCost = previousCosts[previousMask] + quotes[lane][transporter];
                    if (candidateCost < nextCosts[nextMask]) {
                        nextCosts[nextMask] = candidateCost;
                        // A negative value records the first use of this transporter.
                        int predecessor = transporter + 1;
                        if (nextMask != previousMask) {
                            predecessor = -predecessor;
                        }
                        predecessors[lane][nextMask] = (byte) predecessor;
                    }
                }
            }
            previousCosts = nextCosts;
        }

        int bestMask = bestMask(previousCosts);
        if (bestMask == -1) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INFEASIBLE_ASSIGNMENT",
                    "The available quotes cannot cover every lane within the transporter limit.");
        }

        List<Assignment> assignments = reconstruct(lanes, transporters, predecessors, bestMask);
        List<Long> selectedTransporters = new ArrayList<>();
        for (int transporter = 0; transporter < transporters.size(); transporter++) {
            if ((bestMask & (1 << transporter)) != 0) {
                selectedTransporters.add(transporters.get(transporter).getId());
            }
        }
        return new AssignmentResult("success", BigDecimal.valueOf(previousCosts[bestMask], 2),
                Collections.unmodifiableList(assignments), Collections.unmodifiableList(selectedTransporters));
    }

    private long[][] quoteMatrix(List<Lane> lanes, List<Transporter> transporters) {
        Map<Long, Integer> laneIndexes = new HashMap<>();
        long[][] quotes = new long[lanes.size()][transporters.size()];
        for (int lane = 0; lane < lanes.size(); lane++) {
            laneIndexes.put(lanes.get(lane).getId(), lane);
            Arrays.fill(quotes[lane], UNREACHABLE);
        }
        for (int transporter = 0; transporter < transporters.size(); transporter++) {
            for (InputData.LaneQuote quote : transporters.get(transporter).getLaneQuotes()) {
                int lane = laneIndexes.get(quote.getLaneId());
                quotes[lane][transporter] = quote.getQuote().movePointRight(2).longValueExact();
            }
        }
        return quotes;
    }

    private int bestMask(long[] costs) {
        int bestMask = -1;
        long bestCost = UNREACHABLE;
        int bestUsage = -1;
        for (int mask = 1; mask < costs.length; mask++) {
            if (costs[mask] == UNREACHABLE) {
                continue;
            }
            int usage = Integer.bitCount(mask);
            if (usage > bestUsage || (usage == bestUsage && costs[mask] < bestCost)) {
                bestMask = mask;
                bestCost = costs[mask];
                bestUsage = usage;
            }
        }
        return bestMask;
    }

    private List<Assignment> reconstruct(List<Lane> lanes, List<Transporter> transporters,
                                         byte[][] predecessors, int bestMask) {
        Assignment[] assignments = new Assignment[lanes.size()];
        int mask = bestMask;
        for (int lane = lanes.size() - 1; lane >= 0; lane--) {
            int predecessor = predecessors[lane][mask];
            int transporter = Math.abs(predecessor) - 1;
            assignments[lane] = new Assignment(lanes.get(lane).getId(), transporters.get(transporter).getId());
            if (predecessor < 0) {
                mask ^= 1 << transporter;
            }
        }
        return Arrays.asList(assignments);
    }
}
