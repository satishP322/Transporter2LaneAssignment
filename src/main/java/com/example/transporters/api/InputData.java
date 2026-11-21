package com.example.transporters.api;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import javax.validation.Valid;
import javax.validation.constraints.DecimalMax;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.Digits;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;
import javax.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public final class InputData {
    @NotEmpty
    @Size(max = 200)
    private final List<@NotNull @Valid Lane> lanes;

    @NotEmpty
    @Size(max = 15)
    private final List<@NotNull @Valid Transporter> transporters;

    @JsonCreator
    public InputData(@JsonProperty("lanes") List<Lane> lanes,
                     @JsonProperty("transporters") List<Transporter> transporters) {
        this.lanes = lanes;
        this.transporters = transporters;
    }

    public List<Lane> getLanes() {
        return lanes;
    }

    public List<Transporter> getTransporters() {
        return transporters;
    }

    public static final class Lane {
        @NotNull
        @Positive
        private final Long id;

        @NotBlank
        @Size(max = 120)
        private final String origin;

        @NotBlank
        @Size(max = 120)
        private final String destination;

        @JsonCreator
        public Lane(@JsonProperty("id") Long id,
                    @JsonProperty("origin") String origin,
                    @JsonProperty("destination") String destination) {
            this.id = id;
            this.origin = origin;
            this.destination = destination;
        }

        public Long getId() {
            return id;
        }

        public String getOrigin() {
            return origin;
        }

        public String getDestination() {
            return destination;
        }
    }

    public static final class Transporter {
        @NotNull
        @Positive
        private final Long id;

        @NotBlank
        @Size(max = 120)
        private final String name;

        @NotNull
        @Size(max = 200)
        private final List<@NotNull @Valid LaneQuote> laneQuotes;

        @JsonCreator
        public Transporter(@JsonProperty("id") Long id,
                           @JsonProperty("name") String name,
                           @JsonProperty("laneQuotes") List<LaneQuote> laneQuotes) {
            this.id = id;
            this.name = name;
            this.laneQuotes = laneQuotes;
        }

        public Long getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public List<LaneQuote> getLaneQuotes() {
            return laneQuotes;
        }
    }

    public static final class LaneQuote {
        @NotNull
        @Positive
        private final Long laneId;

        @NotNull
        @DecimalMin("0.00")
        @DecimalMax("999999999.99")
        @Digits(integer = 9, fraction = 2)
        @Schema(description = "Non-negative amount in a common currency, with at most two decimal places",
                example = "20835")
        private final BigDecimal quote;

        @JsonCreator
        public LaneQuote(@JsonProperty("laneId") Long laneId,
                         @JsonProperty("quote") BigDecimal quote) {
            this.laneId = laneId;
            this.quote = quote;
        }

        public Long getLaneId() {
            return laneId;
        }

        public BigDecimal getQuote() {
            return quote;
        }
    }
}
