package org.fiolino.common.reflection;

import java.lang.reflect.Method;

/**
 * This is thrown from the {@link MethodFinderCallback} when any method was called.
 *
 * Created by Michael Kuhlmann on 14.12.2015.
 */
final class MethodFoundException extends RuntimeException {
    private final Method method;
    private final Object[] parameters;

    MethodFoundException(Method method, Object[] parameters) {
        this.method = method;
        this.parameters = parameters;
    }

    Method getMethod() {
        return method;
    }

    Object[] getParameters() {
        return parameters;
    }
}
