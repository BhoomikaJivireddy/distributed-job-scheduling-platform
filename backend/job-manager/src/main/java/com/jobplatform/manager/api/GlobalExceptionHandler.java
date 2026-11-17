package com.jobplatform.manager.api;

import com.jobplatform.common.domain.JobStatus;
import com.jobplatform.common.domain.JobType;
import com.jobplatform.manager.service.IdempotencyConflictException;
import com.jobplatform.manager.service.InvalidJobRequestException;
import com.jobplatform.manager.service.JobNotFoundException;
import com.jobplatform.manager.service.JobStateConflictException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException exception,
                                                     HttpServletRequest request) {
        List<ApiError.FieldIssue> issues = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new ApiError.FieldIssue(error.getField(), error.getDefaultMessage()))
                .toList();
        return respond(HttpStatus.BAD_REQUEST, "Request validation failed", request, issues);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadableBody(HttpMessageNotReadableException exception,
                                                         HttpServletRequest request) {
        String message = "Request body is missing, is not valid JSON, or contains an invalid value. jobType must be one of "
                + Arrays.toString(JobType.values()) + " and scheduledAt must be an ISO-8601 instant";
        return respond(HttpStatus.BAD_REQUEST, message, request, List.of());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException exception,
                                                       HttpServletRequest request) {
        String message = "Invalid value for parameter '" + exception.getName() + "'";
        if (exception.getRequiredType() == JobStatus.class) {
            message += "; allowed values are " + Arrays.toString(JobStatus.values());
        } else if (exception.getRequiredType() == JobType.class) {
            message += "; allowed values are " + Arrays.toString(JobType.values());
        }
        return respond(HttpStatus.BAD_REQUEST, message, request, List.of());
    }

    @ExceptionHandler(InvalidJobRequestException.class)
    public ResponseEntity<ApiError> handleInvalidRequest(InvalidJobRequestException exception,
                                                         HttpServletRequest request) {
        return respond(HttpStatus.BAD_REQUEST, exception.getMessage(), request, List.of());
    }

    @ExceptionHandler(JobNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(JobNotFoundException exception, HttpServletRequest request) {
        return respond(HttpStatus.NOT_FOUND, exception.getMessage(), request, List.of());
    }

    @ExceptionHandler({JobStateConflictException.class, IdempotencyConflictException.class})
    public ResponseEntity<ApiError> handleConflict(RuntimeException exception, HttpServletRequest request) {
        return respond(HttpStatus.CONFLICT, exception.getMessage(), request, List.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception exception, HttpServletRequest request) {
        if (exception instanceof ErrorResponse errorResponse) {
            HttpStatusCode status = errorResponse.getStatusCode();
            HttpStatus resolved = HttpStatus.resolve(status.value());
            String message = resolved != null ? resolved.getReasonPhrase() : "Request could not be processed";
            return ResponseEntity.status(status).body(new ApiError(Instant.now(), status.value(), message, message,
                    request.getRequestURI(), List.of()));
        }
        log.error("Unhandled exception on {} {}", request.getMethod(), request.getRequestURI(), exception);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred", request, List.of());
    }

    private ResponseEntity<ApiError> respond(HttpStatus status, String message, HttpServletRequest request,
                                             List<ApiError.FieldIssue> issues) {
        ApiError body = new ApiError(Instant.now(), status.value(), status.getReasonPhrase(), message,
                request.getRequestURI(), issues);
        return ResponseEntity.status(status).body(body);
    }
}
