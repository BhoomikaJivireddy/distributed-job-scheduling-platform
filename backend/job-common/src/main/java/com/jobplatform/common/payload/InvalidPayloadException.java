package com.jobplatform.common.payload;

public class InvalidPayloadException extends NonRetryableJobException {

    public InvalidPayloadException(String message) {
        super(message);
    }
}
