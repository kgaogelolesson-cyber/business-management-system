package com.bms.exception;

/**
 * A database problem. The message is friendly; the technical SQLException
 * is kept as the "cause" for the log.
 */
public class DatabaseException extends BmsException {

    public DatabaseException(String message, Throwable cause) {
        super(message, cause);
    }
}
