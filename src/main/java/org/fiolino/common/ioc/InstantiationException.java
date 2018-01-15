package org.fiolino.common.ioc;

/**
 * Created by kuli on 09.12.15.
 */
public class InstantiationException extends RuntimeException {
    public InstantiationException() {
    }

    public InstantiationException(String message) {
        super(message);
    }

    public InstantiationException(String message, Throwable cause) {
        super(message, cause);
    }

    public InstantiationException(Throwable cause) {
        super(cause);
    }
}
