package com.amalitech.hilfe.exceptions;

import com.amalitech.hilfe.auth.ArmsAuthException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ArmsAuthException.class)
    public ResponseEntity<ProblemDetail> handleArmsAuth(ArmsAuthException ex) {
        HttpStatus status = ex.getHttpStatus() == 401
                ? HttpStatus.UNAUTHORIZED
                : HttpStatus.BAD_GATEWAY;
        log.warn("ARMS auth error [{}]: {}", status.value(), ex.getMessage());
        return ResponseEntity.status(status)
                .body(ProblemDetail.forStatusAndDetail(status, ex.getMessage()));
    }

    @ExceptionHandler(UnsupportedOperationException.class)
    public ResponseEntity<ProblemDetail> handleNotImplemented(UnsupportedOperationException ex) {
        log.warn("Not implemented: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
                .body(ProblemDetail.forStatusAndDetail(
                        HttpStatus.NOT_IMPLEMENTED, ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleGeneral(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ProblemDetail.forStatusAndDetail(
                        HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred"));
    }
}
