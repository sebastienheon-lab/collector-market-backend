package com.collectormarket.backend.api.error;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.collectormarket.backend.api.Grade;

import jakarta.validation.ConstraintViolationException;

/**
 * Renders every error as an RFC 7807 {@code application/problem+json} body with two extension
 * members the frontend relies on: {@code errorCode} (a stable {@link ErrorCode}) and
 * {@code timestamp} (ISO-8601 instant). {@link ApiException} subclasses carry their own status and
 * code; framework binding/validation failures are mapped here.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ProblemDetail handleApi(ApiException ex) {
        return problem(ex.status(), ex.errorCode(), ex.getMessage());
    }

    /**
     * A failed conversion of a query/path param. An unrecognized {@link Grade} token is the common
     * case and maps to {@code INVALID_GRADE}; any other type mismatch is a generic validation error.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        if (ex.getRequiredType() == Grade.class) {
            return problem(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_GRADE,
                    "Unknown grade token: " + ex.getValue());
        }
        return problem(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR,
                "Invalid value for parameter '" + ex.getName() + "'");
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraintViolation(ConstraintViolationException ex) {
        return problem(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR, ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleNotValid(MethodArgumentNotValidException ex) {
        return problem(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR, "Request validation failed");
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ProblemDetail handleMissingParam(MissingServletRequestParameterException ex) {
        return problem(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR,
                "Missing required parameter '" + ex.getParameterName() + "'");
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        log.error("Unhandled exception serving API request", ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR,
                "An unexpected error occurred");
    }

    private ProblemDetail problem(HttpStatus status, ErrorCode errorCode, String detail) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setProperty("errorCode", errorCode.name());
        pd.setProperty("timestamp", Instant.now().toString());
        return pd;
    }
}
