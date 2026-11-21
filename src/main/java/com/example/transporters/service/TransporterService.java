package com.example.transporters.service;

import com.example.transporters.api.ApiException;
import com.example.transporters.api.AssignmentResult;
import com.example.transporters.api.InputData;
import com.example.transporters.optimization.AssignmentOptimizer;
import com.example.transporters.persistence.InputRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.concurrent.Semaphore;

@Service
public class TransporterService {
    private final InputValidator validator;
    private final InputRepository repository;
    private final AssignmentOptimizer optimizer = new AssignmentOptimizer();
    private final Semaphore solverSlots = new Semaphore(2);

    public TransporterService(InputValidator validator, InputRepository repository) {
        this.validator = validator;
        this.repository = repository;
    }

    public void save(InputData input) {
        validator.validate(input);
        repository.replace(input);
    }

    public AssignmentResult assign(int maxTransporters) {
        if (!solverSlots.tryAcquire()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "SOLVER_BUSY",
                    "Two optimizations are already running. Retry shortly.");
        }
        try {
            return optimizer.optimize(repository.findCurrent(), maxTransporters);
        } finally {
            solverSlots.release();
        }
    }
}
