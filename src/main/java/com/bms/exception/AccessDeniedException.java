package com.bms.exception;

/** The logged-in user's role is not allowed to do this. */
public class AccessDeniedException extends BmsException {

    public AccessDeniedException(String message) {
        super(message);
    }
}
