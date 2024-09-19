package com.mku.salmon.ws.fs.service.controller;

import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.io.ByteArrayInputStream;
import java.io.IOException;

@ControllerAdvice
public class RealFileExceptionHandler {
    @ExceptionHandler
    public ResponseEntity<Resource> handleException(Exception e) {
        String msg = e.getMessage();
        InputStreamResource resource = new InputStreamResource(new ByteArrayInputStream(msg.getBytes()));
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .contentLength(msg.length())
                .contentType(MediaType.APPLICATION_JSON)
                .body(resource);
    }
}
