package org.fiolino.common.reflection;

import java.text.MessageFormat;
import java.util.function.BiFunction;

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

    /**
     * Creates an exception handler that handles all exception of the given type with the new handler,
     * and all remaining exceptions still as myself.
     *
     * @param exceptionType Some exception type, possibly a subtype of my own handled exception
     * @param otherHandle This will handle all such exceptions then
     * @param <F> The subtype
     * @return A new handler
     */
    default <F extends E> ExceptionHandler<E> orIf(Class<F> exceptionType, ExceptionHandler<? super F> otherHandle) {
        return (ex, v) -> exceptionType.isInstance(ex) ? otherHandle.handle(exceptionType.cast(ex), v) : handle(ex, v);
    }

    /**
     * Creates an ExceptionHandler that will throw a new exception which includes some detailed information about the context.
     *
     * This handler will pack catched exceptions into a new exception constructed by thrownExceptionFactory. This factory gets
     * a String and a Throwable as parameters and returns the exception that will be thrown.
     *
     * The passed String is a new message string which is defined by the exceptionMessage parameter. This is passed
     * to {@link MessageFormat}, with all parameter values of the handler.
     *
     * @param thrownExceptionFactory This will be the new exception type, with the original one as the wrapped exception.
     *                               Gets the constructed message String and the catched Exception.
     * @param exceptionMessage As defined in {@link MessageFormat}
     * @return A handle of the same type as target
     */
    static <E extends Throwable> ExceptionHandler<E> rethrowException(BiFunction<? super String, ? super E, ? extends Throwable> thrownExceptionFactory,
                                                                      String exceptionMessage) {

        return (ex, v) -> {
            String thrownMessage = MessageFormat.format(exceptionMessage, v);
            throw thrownExceptionFactory.apply(thrownMessage, ex);
        };
    }

    /**
     * An {@link ExceptionHandler} that just throws the incoming exception again.
     */
    ExceptionHandler<Throwable> RETHROW = (ex, v) -> {
        throw ex;
    };
}
