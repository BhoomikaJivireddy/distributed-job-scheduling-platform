package com.jobplatform.manager.service;

public class JobStateConflictException extends RuntimeException {

    public JobStateConflictException(String message) {
        super(message);
    }
}
