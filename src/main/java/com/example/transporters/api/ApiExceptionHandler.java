package com.example.transporters.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Object> handleApiException(ApiException exception) {
        return error(exception.status(), exception.code(), exception.getMessage(), Collections.emptyList());
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException exception,
            HttpHeaders headers, HttpStatus status, WebRequest request) {
        List<ApiError.FieldError> details = exception.getBindingResult().getFieldErrors().stream()
                .map(field -> new ApiError.FieldError(field.getField(), field.getDefaultMessage()))
                .sorted(Comparator.comparing(ApiError.FieldError::getField)
                        .thenComparing(ApiError.FieldError::getMessage))
                .collect(Collectors.toList());
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Correct the invalid request fields.", details);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException exception,
            HttpHeaders headers, HttpStatus status, WebRequest request) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_JSON",
                "Provide valid JSON with the documented field names and types.", Collections.emptyList());
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception exception, Object body,
            HttpHeaders headers, HttpStatus status, WebRequest request) {
        return ResponseEntity.status(status).headers(headers).body(new ApiError("error", "HTTP_" + status.value(),
                status.getReasonPhrase(), Collections.emptyList()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleUnexpectedException(Exception exception) {
        log.error("Request failed unexpectedly", exception);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "The request could not be completed. Please retry.", Collections.emptyList());
    }

    private ResponseEntity<Object> error(HttpStatus status, String code, String message,
                                          List<ApiError.FieldError> details) {
        return ResponseEntity.status(status).body(new ApiError("error", code, message, details));
    }
}
