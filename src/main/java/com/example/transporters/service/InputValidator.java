package com.example.transporters.service;

import com.example.transporters.api.ApiException;
import com.example.transporters.api.InputData;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class InputValidator {
    public void validate(InputData input) {
        Set<Long> laneIds = new HashSet<>();
        for (InputData.Lane lane : input.getLanes()) {
            if (!laneIds.add(lane.getId())) {
                throw invalid("Duplicate lane id " + lane.getId() + ".");
            }
        }

        Set<Long> transporterIds = new HashSet<>();
        Set<Long> coveredLanes = new HashSet<>();
        for (InputData.Transporter transporter : input.getTransporters()) {
            if (!transporterIds.add(transporter.getId())) {
                throw invalid("Duplicate transporter id " + transporter.getId() + ".");
            }
            Set<Long> quotedLanes = new HashSet<>();
            for (InputData.LaneQuote quote : transporter.getLaneQuotes()) {
                if (!laneIds.contains(quote.getLaneId())) {
                    throw invalid("Transporter " + transporter.getId() + " quotes unknown lane " + quote.getLaneId() + ".");
                }
                if (!quotedLanes.add(quote.getLaneId())) {
                    throw invalid("Transporter " + transporter.getId() + " has duplicate quotes for lane " + quote.getLaneId() + ".");
                }
                coveredLanes.add(quote.getLaneId());
            }
        }

        List<Long> uncovered = laneIds.stream().filter(id -> !coveredLanes.contains(id))
                .sorted().collect(Collectors.toList());
        if (!uncovered.isEmpty()) {
            throw invalid("Every lane requires at least one quote. Missing quotes for lanes " + uncovered + ".");
        }
    }

    private ApiException invalid(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message);
    }
}
