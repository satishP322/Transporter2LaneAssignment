package com.example.transporters.api;

import com.example.transporters.service.TransporterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import javax.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/transporters")
@Tag(name = "Transporter assignment", description = "Submit a dataset, then optimize its lane assignments.")
public class TransporterController {
    private final TransporterService service;

    public TransporterController(TransporterService service) {
        this.service = service;
    }

    @PostMapping("/input")
    @Operation(summary = "Save lanes and transporter quotes",
            description = "Atomically replaces the current shared dataset after validation. Supports up to 200 lanes "
                    + "and 15 transporters. Every lane must have a quote. Missing transporter/lane quotes mean that "
                    + "assignment is unavailable. Data persists in the configured PostgreSQL database, "
                    + "or across local H2 restarts when the data directory is retained.")
    @ApiResponse(responseCode = "200", description = "Input saved",
            content = @Content(schema = @Schema(implementation = InputResponse.class)))
    @ApiResponse(responseCode = "400", description = "Invalid JSON, fields, duplicates, references, or uncovered lanes",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public InputResponse save(@Valid @RequestBody InputData input) {
        service.save(input);
        return new InputResponse("success", "Input data saved successfully.");
    }

    @PostMapping("/assignment")
    @Operation(summary = "Compute the optimal lane assignments",
            description = "Assigns exactly one quoted transporter to every lane within the distinct-transporter limit. "
                    + "Uses the maximum feasible number of distinct transporters up to maxTransporters, "
                    + "then minimizes total cost among assignments using that many transporters. "
                    + "Results are deterministic and sorted by lane id. A limit larger than the dataset size is allowed.")
    @ApiResponse(responseCode = "200", description = "Exact optimal assignment",
            content = @Content(schema = @Schema(implementation = AssignmentResult.class)))
    @ApiResponse(responseCode = "400", description = "Invalid maxTransporters or JSON",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "404", description = "Submit input first",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "422", description = "Full coverage is impossible within the limit",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "503", description = "Two optimizations are running. Retry shortly.",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public AssignmentResult assign(@Valid @RequestBody AssignmentRequest request) {
        return service.assign(request.getMaxTransporters());
    }
}
