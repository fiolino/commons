package org.fiolino.common.ioc;

public class MismatchedMethodTypeException extends Exception {


    public MismatchedMethodTypeException() {
    }

    public MismatchedMethodTypeException(String message) {
        super(message);
    }

    public MismatchedMethodTypeException(String message, Throwable cause) {
        super(message, cause);
    }

    public MismatchedMethodTypeException(Throwable cause) {
        super(cause);
    }
}
