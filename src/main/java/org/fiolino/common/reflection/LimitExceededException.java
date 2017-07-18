package org.fiolino.common.reflection;

/**
 * Created by kuli on 07.07.17.
 */
public class LimitExceededException extends RuntimeException {
    public LimitExceededException() {
    }

    public LimitExceededException(String message) {
        super(message);
    }

    public LimitExceededException(String message, Throwable cause) {
        super(message, cause);
    }

    public LimitExceededException(Throwable cause) {
        super(cause);
    }
}
