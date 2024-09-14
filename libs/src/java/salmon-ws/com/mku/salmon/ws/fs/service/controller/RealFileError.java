package com.mku.salmon.ws.fs.service.controller;

import org.springframework.http.HttpStatus;

/**
 * Custom request error
 */
public class RealFileError {
    private HttpStatus status;
    private String message;
    public RealFileError(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }
}
