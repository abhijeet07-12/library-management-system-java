package com.library.library_management;

import org.springframework.http.HttpStatus;

/** Business-rule failure that is reported to the client as a clean JSON error. */
public class ApiException extends RuntimeException {

    private final HttpStatus status;

    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
