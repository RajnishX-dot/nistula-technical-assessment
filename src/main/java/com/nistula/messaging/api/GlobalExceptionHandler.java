package com.nistula.messaging.api;

import com.nistula.messaging.api.dto.ErrorBodyDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorBodyDto> handleStatus(ResponseStatusException ex) {
        int status = ex.getStatusCode().value();
        String code = "error";
        if (status == 502) {
            code = "bad_gateway";
        } else if (status == 503) {
            code = "service_unavailable";
        } else if (status == 422) {
            code = "validation_error";
        }
        String reason = ex.getReason() != null ? ex.getReason() : "";
        return ResponseEntity.status(ex.getStatusCode()).body(new ErrorBodyDto(code, reason));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorBodyDto> handleValidation(MethodArgumentNotValidException ex) {
        String msg =
                ex.getBindingResult().getFieldErrors().stream()
                        .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                        .findFirst()
                        .orElse("validation failed");
        return ResponseEntity.status(422).body(new ErrorBodyDto("validation_error", msg));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorBodyDto> handleGeneric(Exception ex) {
        log.error("unhandled", ex);
        return ResponseEntity.internalServerError()
                .body(new ErrorBodyDto("internal_server_error", "internal error"));
    }
}
