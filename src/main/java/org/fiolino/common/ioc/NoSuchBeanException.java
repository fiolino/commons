package org.fiolino.common.ioc;

/**
 * Created by kuli on 24.03.15.
 */
public class NoSuchBeanException extends RuntimeException {
    public NoSuchBeanException() {
    }

    public NoSuchBeanException(String message) {
        super(message);
    }

    public NoSuchBeanException(String message, Throwable cause) {
        super(message, cause);
    }

    public NoSuchBeanException(Throwable cause) {
        super(cause);
    }
}
