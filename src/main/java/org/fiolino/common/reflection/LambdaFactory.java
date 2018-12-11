package org.fiolino.common.reflection;

import javax.annotation.Nullable;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.UndeclaredThrowableException;

import static java.lang.invoke.MethodType.methodType;

@FunctionalInterface
public interface LambdaFactory<L> {

    L create(Object... leadingAttributes);

    static <L> LambdaFactory<L> createFor(@Nullable MethodHandles.Lookup lookup, MethodHandle targetMethod, Class<?> lambdaType) {
        MethodHandle factory = Methods.createLambdaFactory(lookup, targetMethod, lambdaType);
        if (factory == null) {
            // @TODO
            throw new UnsupportedOperationException();
        }

        MethodHandle f = factory.asSpreader(Object[].class, factory.type().parameterCount()).asType(methodType(Object.class, Object[].class));
        return a -> {
            try {
                @SuppressWarnings("unchecked")
                L lambda = (L) f.invokeExact(a);
                return lambda;
            } catch (RuntimeException | Error e) {
                throw e;
            } catch (Throwable t) {
                throw new UndeclaredThrowableException(t);
            }
        };
    }
}

class HandleProxy implements InvocationHandler {

    private final MethodHandle handle;
    private final Method method;
    private final Object[] leadingValues;

    HandleProxy(MethodHandle handle, Method method, Object[] leadingValues) {
        this.handle = handle;
        this.method = method;
        this.leadingValues = leadingValues;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (method.equals(this.method)) {
            return handle.invokeExact(leadingValues, args);
        }
        String methodName = method.getName();
        if ("toString".equals(methodName)) {
            return method.getDeclaringClass().getName() + "@" + System.identityHashCode(this);
        }
        if ("hashCode".equals(methodName)) {
            return System.identityHashCode(this);
        }
        if ("equals".equals(methodName)) {
            return this == args[0];
        }
        throw new IllegalStateException("Unknown method " + method);
    }


}
