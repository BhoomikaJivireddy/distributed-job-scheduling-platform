package com.jobplatform.manager.service;

public class JobNotFoundException extends RuntimeException {

    public JobNotFoundException(Long jobId) {
        super("Job " + jobId + " was not found");
    }
}
