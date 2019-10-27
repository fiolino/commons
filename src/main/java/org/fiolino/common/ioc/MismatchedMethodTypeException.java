package org.fiolino.common.ioc;

/**
 * This is thrown when you want to register a {@link java.lang.invoke.MethodHandle } in a {@link MethodHandleProvider}
 * which does not match the requested type.
 */
public class MismatchedMethodTypeException extends Exception {

    private static final long serialVersionUID = 4256995446264170011L;

    MismatchedMethodTypeException(String message) {
        super(message);
    }
}
