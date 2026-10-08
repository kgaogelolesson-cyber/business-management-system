package com.bms.exception;

/** Login failed (wrong username, wrong password or inactive account). */
public class AuthenticationException extends BmsException {

    public AuthenticationException(String message) {
        super(message);
    }
}
