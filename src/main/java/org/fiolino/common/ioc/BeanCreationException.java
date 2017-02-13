package org.fiolino.common.ioc;

/**
 * Created by kuli on 24.03.15.
 */
public class BeanCreationException extends RuntimeException {
    public BeanCreationException() {
    }

    public BeanCreationException(String message) {
        super(message);
    }

    public BeanCreationException(String message, Throwable cause) {
        super(message, cause);
    }

    public BeanCreationException(Throwable cause) {
        super(cause);
    }
}
