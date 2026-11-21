package com.example.transporters.api;

public final class InputResponse {
    private final String status;
    private final String message;

    public InputResponse(String status, String message) {
        this.status = status;
        this.message = message;
    }

    public String getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }
}
