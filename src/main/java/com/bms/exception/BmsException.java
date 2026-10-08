package com.bms.exception;

/**
 * Parent of all our own "expected problem" exceptions.
 * The message is always written so that it can be shown directly to the user.
 */
public class BmsException extends Exception {

    public BmsException(String message) {
        super(message);
    }

    public BmsException(String message, Throwable cause) {
        super(message, cause);
    }
}
