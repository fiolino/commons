package org.fiolino.common.reflection;

/**
 * Created by kuli on 07.01.16.
 */
public interface ExceptionHandler<E extends Throwable> {
    /**
     * This is called when a converter created by Methods().convertStringToEnum() accepts
     * an invalid value.
     *
     * @param exception The exception that is called from the converting method
     * @param valueType The accepted parameter type
     * @param outputType The return value of that method
     * @param inputValue The input value from the call
     * @throws Throwable If the original exception shall be rethrown
     */
    void handleNotExisting(E exception, Class<?> valueType, Class<?> outputType, Object inputValue) throws Throwable;
}
