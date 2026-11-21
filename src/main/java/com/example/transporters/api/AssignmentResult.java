package com.example.transporters.api;

import java.math.BigDecimal;
import java.util.List;

public final class AssignmentResult {
    private final String status;
    private final BigDecimal totalCost;
    private final List<Assignment> assignments;
    private final List<Long> selectedTransporters;

    public AssignmentResult(String status, BigDecimal totalCost, List<Assignment> assignments,
                            List<Long> selectedTransporters) {
        this.status = status;
        this.totalCost = totalCost;
        this.assignments = assignments;
        this.selectedTransporters = selectedTransporters;
    }

    public String getStatus() {
        return status;
    }

    public BigDecimal getTotalCost() {
        return totalCost;
    }

    public List<Assignment> getAssignments() {
        return assignments;
    }

    public List<Long> getSelectedTransporters() {
        return selectedTransporters;
    }

    public static final class Assignment {
        private final Long laneId;
        private final Long transporterId;

        public Assignment(Long laneId, Long transporterId) {
            this.laneId = laneId;
            this.transporterId = transporterId;
        }

        public Long getLaneId() {
            return laneId;
        }

        public Long getTransporterId() {
            return transporterId;
        }
    }
}
