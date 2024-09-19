package com.mku.salmon.ws.fs.service.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.io.IOException;

@ControllerAdvice
public class RealFileExceptionHandler {
    @ExceptionHandler
    public ResponseEntity<Object> handleException(Exception e) {
        RealFileError error = new RealFileError(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
        return new ResponseEntity<Object>(error, new HttpHeaders(), error.getStatus());
    }
}
