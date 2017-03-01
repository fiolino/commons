package org.fiolino.common.reflection;

/**
 * This is thrown when there were multiple methods detected that match with same comparison rank.
 * <p>
 * Created by Kuli on 6/2/2016.
 */
public class AmbiguousMethodException extends RuntimeException {

    private static final long serialVersionUID = -2883393890604050516L;

    public AmbiguousMethodException() {
    }

    public AmbiguousMethodException(String message) {
        super(message);
    }

    public AmbiguousMethodException(String message, Throwable cause) {
        super(message, cause);
    }

    public AmbiguousMethodException(Throwable cause) {
        super(cause);
    }
}
