package org.fiolino.common.analyzing;

/**
 * Created by kuli on 11.01.16.
 */
public class AmbiguousTypesException extends RuntimeException {
    public AmbiguousTypesException() {
    }

    public AmbiguousTypesException(String message) {
        super(message);
    }

    public AmbiguousTypesException(String message, Throwable cause) {
        super(message, cause);
    }

    public AmbiguousTypesException(Throwable cause) {
        super(cause);
    }
}
