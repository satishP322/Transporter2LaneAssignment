package com.example.transporters.api;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;

public final class AssignmentRequest {
    @NotNull
    @Positive
    @Schema(description = "Upper bound on distinct transporters actually assigned. Values above the dataset size are allowed.",
            example = "3")
    private final Integer maxTransporters;

    @JsonCreator
    public AssignmentRequest(@JsonProperty("maxTransporters") Integer maxTransporters) {
        this.maxTransporters = maxTransporters;
    }

    public Integer getMaxTransporters() {
        return maxTransporters;
    }
}
