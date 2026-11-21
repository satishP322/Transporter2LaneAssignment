package com.example.transporters.optimization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.example.transporters.api.ApiException;
import com.example.transporters.api.AssignmentResult;
import com.example.transporters.api.AssignmentResult.Assignment;
import com.example.transporters.api.InputData;
import com.example.transporters.api.InputData.Lane;
import com.example.transporters.api.InputData.LaneQuote;
import com.example.transporters.api.InputData.Transporter;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class AssignmentOptimizerTest {
    private static final Comparator<Oracle> USAGE_FIRST = Comparator.comparingInt(Oracle::getUsage).reversed()
            .thenComparingLong(Oracle::getCost);
    private final AssignmentOptimizer optimizer = new AssignmentOptimizer();

    @Test
    void findsVerifiedOptimaForEveryLimitOnTheSuppliedCsv() throws IOException {
        InputData data = suppliedCsv();
        long[] expectedTotals = {201259, 148402, 134876, 127844, 125045, 125256, 129580};
        for (int limit = 1; limit <= expectedTotals.length; limit++) {
            AssignmentResult result = optimizer.optimize(data, limit);
            assertThat(result.getTotalCost()).isEqualByComparingTo(BigDecimal.valueOf(expectedTotals[limit - 1]));
            assertThat(result.getSelectedTransporters()).hasSize(limit);
            verifyAssignments(data, result, limit);
        }
        AssignmentResult result = optimizer.optimize(data, 3);
        assertThat(result.getSelectedTransporters()).containsExactly(1L, 4L, 6L);
        assertThat(result.getAssignments()).extracting(Assignment::getTransporterId)
                .containsExactly(4L, 1L, 4L, 4L, 4L, 1L, 1L, 6L, 6L);
    }

    @Test
    void solvesTheFiveLanePdfCaseUsingItsActualQuotes() throws IOException {
        InputData full = suppliedCsv();
        InputData data = new InputData(full.getLanes().subList(0, 5), full.getTransporters().stream()
                .map(transporter -> new Transporter(transporter.getId(), transporter.getName(),
                        transporter.getLaneQuotes().subList(0, 5)))
                .collect(Collectors.toList()));

        AssignmentResult result = optimizer.optimize(data, 3);

        assertThat(result.getTotalCost()).isEqualByComparingTo("60139");
        assertThat(result.getSelectedTransporters()).containsExactly(1L, 4L, 5L);
        assertThat(result.getAssignments()).extracting(Assignment::getTransporterId)
                .containsExactly(5L, 1L, 4L, 4L, 4L);
    }

    @Test
    void maximizesActualUsageWhenCostsAreTied() {
        InputData data = fromCents(new Long[][]{{100L, 100L, 100L}, {100L, 100L, 100L}});

        AssignmentResult result = optimizer.optimize(data, 3);

        assertThat(result.getTotalCost()).isEqualByComparingTo("2.00");
        assertThat(result.getSelectedTransporters()).hasSize(2);
        verifyAssignments(data, result, 3);
    }

    @Test
    void usesTheMaximumFeasibleTransporterCountThenMinimizesCost() {
        InputData data = fromCents(new Long[][]{{100L, 500L}, {100L, 500L}});

        AssignmentResult result = optimizer.optimize(data, 2);

        assertThat(result.getTotalCost()).isEqualByComparingTo("6.00");
        assertThat(result.getSelectedTransporters()).containsExactly(1L, 2L);
        verifyAssignments(data, result, 2);
    }

    @Test
    void maximizesFeasibleUsageWhenSparseQuotesPreventUsingEveryTransporter() {
        InputData data = fromCents(new Long[][]{{100L, 200L, null}, {null, null, 300L}, {null, null, 300L}});

        AssignmentResult result = optimizer.optimize(data, 3);

        assertThat(result.getTotalCost()).isEqualByComparingTo("7.00");
        assertThat(result.getSelectedTransporters()).containsExactly(1L, 3L);
        verifyAssignments(data, result, 3);
    }

    @Test
    void findsTheMinimumCostForUsingAllSevenCsvTransporters() throws IOException {
        InputData data = suppliedCsv();

        AssignmentResult result = optimizer.optimize(data, 7);

        assertThat(result.getTotalCost()).isEqualByComparingTo("129580");
        assertThat(result.getSelectedTransporters()).containsExactly(1L, 2L, 3L, 4L, 5L, 6L, 7L);
        assertThat(result.getAssignments()).extracting(Assignment::getTransporterId)
                .containsExactly(5L, 1L, 2L, 4L, 3L, 1L, 1L, 6L, 7L);
        verifyAssignments(data, result, 7);
    }

    @Test
    void treatsZeroAndFractionalQuotesAsExactMoney() {
        InputData data = fromCents(new Long[][]{{0L, 1L}, {20L, 10L}, {10L, 20L}});

        AssignmentResult result = optimizer.optimize(data, 2);

        assertThat(result.getTotalCost()).isEqualByComparingTo("0.20");
        assertThat(result.getAssignments()).extracting(Assignment::getTransporterId).containsExactly(1L, 2L, 1L);
    }

    @Test
    void coversSparseQuotesAndCapsAnOversizedLimit() {
        InputData data = fromCents(new Long[][]{{100L, null, null}, {null, 200L, null}, {null, null, 300L}});

        AssignmentResult result = optimizer.optimize(data, 100);

        assertThat(result.getTotalCost()).isEqualByComparingTo("6.00");
        assertThat(result.getSelectedTransporters()).containsExactly(1L, 2L, 3L);
        verifyAssignments(data, result, 100);
    }

    @Test
    void reportsInfeasibilityWhenCoverageRequiresMoreTransporters() {
        InputData data = fromCents(new Long[][]{{100L, null}, {null, 200L}});

        assertThatThrownBy(() -> optimizer.optimize(data, 1)).isInstanceOf(ApiException.class)
                .hasMessageContaining("cannot cover every lane");
    }

    @Test
    void producesStableResultsRegardlessOfInputOrdering() {
        InputData data = fromCents(new Long[][]{{100L, 100L, 100L}, {100L, 100L, 100L}, {100L, 100L, 100L}});
        List<Lane> lanes = new ArrayList<>(data.getLanes());
        Collections.reverse(lanes);
        List<Transporter> transporters = new ArrayList<>();
        for (Transporter transporter : data.getTransporters()) {
            List<LaneQuote> quotes = new ArrayList<>(transporter.getLaneQuotes());
            Collections.reverse(quotes);
            transporters.add(new Transporter(transporter.getId(), transporter.getName(), quotes));
        }
        Collections.reverse(transporters);

        assertThat(optimizer.optimize(new InputData(lanes, transporters), 3))
                .usingRecursiveComparison().isEqualTo(optimizer.optimize(data, 3));
    }

    @Test
    void handlesNonContiguousIdsAndAReusableTransporter() {
        InputData data = new InputData(
                Arrays.asList(new Lane(90L, "A", "B"), new Lane(10L, "C", "D")),
                Arrays.asList(new Transporter(500L, "Shared", Arrays.asList(
                        new LaneQuote(90L, new BigDecimal("1.25")),
                        new LaneQuote(10L, new BigDecimal("2.50"))))));

        AssignmentResult result = optimizer.optimize(data, 1);

        assertThat(result.getAssignments()).extracting(Assignment::getLaneId, Assignment::getTransporterId)
                .containsExactly(tuple(10L, 500L), tuple(90L, 500L));
        assertThat(result.getTotalCost()).isEqualByComparingTo("3.75");
    }

    @Test
    void supportsMaximumMoneyWithoutIntegerOverflow() {
        Long[][] quotes = new Long[200][1];
        for (Long[] lane : quotes) {
            lane[0] = 99_999_999_999L;
        }

        AssignmentResult result = optimizer.optimize(fromCents(quotes), 1);

        assertThat(result.getTotalCost()).isEqualByComparingTo("199999999998.00");
        assertThat(result.getAssignments()).hasSize(200);
    }

    @Test
    void reconstructsAssignmentsAtTheSupportedSizeBoundary() {
        Long[][] quotes = new Long[200][15];
        for (Long[] lane : quotes) {
            Arrays.fill(lane, 1L);
        }
        InputData data = fromCents(quotes);

        AssignmentResult result = optimizer.optimize(data, 15);

        assertThat(result.getTotalCost()).isEqualByComparingTo("2.00");
        assertThat(result.getSelectedTransporters()).hasSize(15);
        verifyAssignments(data, result, 15);
    }

    @Test
    void agreesWithExhaustiveAssignmentEnumerationForRandomSparseInputs() {
        Random random = new Random(19760319);
        for (int example = 0; example < 200; example++) {
            int laneCount = random.nextInt(6) + 1;
            int transporterCount = random.nextInt(5) + 1;
            Long[][] quotes = new Long[laneCount][transporterCount];
            for (int lane = 0; lane < laneCount; lane++) {
                for (int transporter = 0; transporter < transporterCount; transporter++) {
                    if (random.nextInt(4) != 0) {
                        quotes[lane][transporter] = (long) random.nextInt(10);
                    }
                }
                quotes[lane][random.nextInt(transporterCount)] = (long) random.nextInt(10);
            }
            InputData data = fromCents(quotes);
            for (int limit = 1; limit <= transporterCount + 1; limit++) {
                Oracle optimum = enumerate(quotes, limit, 0, 0L, 0, null);
                int requestedLimit = limit;
                if (optimum == null) {
                    assertThatThrownBy(() -> optimizer.optimize(data, requestedLimit))
                            .isInstanceOf(ApiException.class);
                } else {
                    AssignmentResult result = optimizer.optimize(data, limit);
                    assertThat(result.getTotalCost()).as("example %s, limit %s", example, limit)
                            .isEqualByComparingTo(BigDecimal.valueOf(optimum.getCost(), 2));
                    assertThat(result.getSelectedTransporters()).hasSize(optimum.getUsage());
                    verifyAssignments(data, result, limit);
                }
            }
        }
    }

    private Oracle enumerate(Long[][] quotes, int limit,
                             int lane, long cost, int mask, Oracle best) {
        if (Integer.bitCount(mask) > limit) {
            return best;
        }
        if (lane == quotes.length) {
            int usage = Integer.bitCount(mask);
            Oracle candidate = new Oracle(cost, usage);
            if (best == null) {
                return candidate;
            }
            return USAGE_FIRST.compare(candidate, best) < 0 ? candidate : best;
        }
        for (int transporter = 0; transporter < quotes[lane].length; transporter++) {
            if (quotes[lane][transporter] != null) {
                best = enumerate(quotes, limit, lane + 1, cost + quotes[lane][transporter],
                        mask | (1 << transporter), best);
            }
        }
        return best;
    }

    private void verifyAssignments(InputData data, AssignmentResult result, int limit) {
        assertThat(result.getStatus()).isEqualTo("success");
        assertThat(result.getAssignments()).hasSize(data.getLanes().size());
        assertThat(result.getAssignments()).extracting(Assignment::getLaneId)
                .containsExactlyInAnyOrderElementsOf(data.getLanes().stream().map(Lane::getId).collect(Collectors.toList()));
        BigDecimal total = BigDecimal.ZERO;
        Set<Long> actualTransporters = new HashSet<>();
        for (Assignment assignment : result.getAssignments()) {
            Transporter transporter = data.getTransporters().stream()
                    .filter(candidate -> candidate.getId().equals(assignment.getTransporterId()))
                    .findFirst().orElseThrow(IllegalStateException::new);
            LaneQuote quote = transporter.getLaneQuotes().stream()
                    .filter(candidate -> candidate.getLaneId().equals(assignment.getLaneId()))
                    .findFirst().orElseThrow(IllegalStateException::new);
            total = total.add(quote.getQuote());
            actualTransporters.add(transporter.getId());
        }
        assertThat(result.getTotalCost()).isEqualByComparingTo(total);
        assertThat(result.getSelectedTransporters()).containsExactlyInAnyOrderElementsOf(actualTransporters);
        assertThat(result.getSelectedTransporters().size()).isLessThanOrEqualTo(limit);
    }

    private InputData fromCents(Long[][] quotes) {
        List<Lane> lanes = new ArrayList<>();
        for (int lane = 0; lane < quotes.length; lane++) {
            lanes.add(new Lane((long) lane + 1, "Origin " + lane, "Destination " + lane));
        }
        List<Transporter> transporters = new ArrayList<>();
        for (int transporter = 0; transporter < quotes[0].length; transporter++) {
            List<LaneQuote> laneQuotes = new ArrayList<>();
            for (int lane = 0; lane < quotes.length; lane++) {
                if (quotes[lane][transporter] != null) {
                    laneQuotes.add(new LaneQuote((long) lane + 1, BigDecimal.valueOf(quotes[lane][transporter], 2)));
                }
            }
            transporters.add(new Transporter((long) transporter + 1, "Transporter " + transporter, laneQuotes));
        }
        return new InputData(lanes, transporters);
    }

    private InputData suppliedCsv() throws IOException {
        List<String> lines = Files.readAllLines(Paths.get("transporter_assignment_data.csv"));
        Long[][] quotes = new Long[lines.size() - 1][lines.get(0).split(",").length - 1];
        for (int lane = 0; lane < quotes.length; lane++) {
            String[] values = lines.get(lane + 1).split(",");
            for (int transporter = 0; transporter < quotes[lane].length; transporter++) {
                quotes[lane][transporter] = Long.parseLong(values[transporter + 1].trim()) * 100;
            }
        }
        return fromCents(quotes);
    }

    private static final class Oracle {
        private final long cost;
        private final int usage;

        private Oracle(long cost, int usage) {
            this.cost = cost;
            this.usage = usage;
        }

        private long getCost() {
            return cost;
        }

        private int getUsage() {
            return usage;
        }
    }
}
