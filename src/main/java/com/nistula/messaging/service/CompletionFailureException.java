package com.nistula.messaging.service;

public class CompletionFailureException extends RuntimeException {

    public CompletionFailureException(String message) {
        super(message);
    }

    public CompletionFailureException(String message, Throwable cause) {
        super(message, cause);
    }
}
