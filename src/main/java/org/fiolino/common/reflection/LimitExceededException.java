package org.fiolino.common.reflection;

/**
 * This is thrown from the {@link Reflection} class.
 */
public class LimitExceededException extends RuntimeException {
    private static final long serialVersionUID = 3037987224819299430L;

    LimitExceededException(String message) {
        super(message);
    }

    LimitExceededException(String message, Throwable cause) {
        super(message, cause);
    }
}
