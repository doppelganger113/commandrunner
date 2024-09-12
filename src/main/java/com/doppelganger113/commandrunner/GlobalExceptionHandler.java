package com.doppelganger113.commandrunner;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.server.ResponseStatusException;

@ControllerAdvice
public class GlobalExceptionHandler {

    public record BadRequestResponse(String message) {
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<BadRequestResponse> handleBadRequestException(ResponseStatusException ex) {
        if(ex.getStatusCode().is4xxClientError()) {
            return ResponseEntity.status(ex.getStatusCode()).body(new BadRequestResponse(ex.getMessage()));
        }

        return ResponseEntity.status(ex.getStatusCode()).build();
    }
}
