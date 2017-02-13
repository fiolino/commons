package org.fiolino.common.util;

/**
 * Created by kuli on 28.01.15.
 */
public class NotAssignableException extends RuntimeException {
    public NotAssignableException() {
    }

    public NotAssignableException(String message) {
        super(message);
    }

    public NotAssignableException(String message, Throwable cause) {
        super(message, cause);
    }

    public NotAssignableException(Throwable cause) {
        super(cause);
    }
}
