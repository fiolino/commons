package org.fiolino.common.reflection;

/**
 * Functional interface to handle exception thrown from method invocation.
 *
 * Created by kuli on 07.01.16.
 */
@FunctionalInterface
public interface ExceptionHandler<E extends Throwable> {
    /**
     * This is called when some handle throws an exception.
     *
     * @param exception  The exception that is called from the converting method
     * @param parameterValues The values of the original MethodHandle call
     * @return The value that will be returned instead
     * @throws Throwable To rethrow some exception
     */
    Object handle(E exception, Object[] parameterValues) throws Throwable;
}
