package com.example.transporters.api;

import java.util.List;

public final class ApiError {
    private final String status;
    private final String code;
    private final String message;
    private final List<FieldError> details;

    public ApiError(String status, String code, String message, List<FieldError> details) {
        this.status = status;
        this.code = code;
        this.message = message;
        this.details = details;
    }

    public String getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public List<FieldError> getDetails() {
        return details;
    }

    public static final class FieldError {
        private final String field;
        private final String message;

        public FieldError(String field, String message) {
            this.field = field;
            this.message = message;
        }

        public String getField() {
            return field;
        }

        public String getMessage() {
            return message;
        }
    }
}
