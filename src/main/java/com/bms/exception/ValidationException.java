package com.bms.exception;

/** The user typed something missing or invalid. */
public class ValidationException extends BmsException {

    public ValidationException(String message) {
        super(message);
    }
}
