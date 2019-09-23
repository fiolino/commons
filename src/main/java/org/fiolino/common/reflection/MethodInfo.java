package org.fiolino.common.reflection;

import org.fiolino.common.ioc.MethodHandleProvider;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.function.Supplier;

/**
 * Describes a found method from the {@link MethodLocator}.
 */
public interface MethodInfo extends AnnotatedElement, Member {
    /**
     * Gets the actual method.
     *
     * @return The method
     */
    Method getMethod();

    /**
     * The name of the method.
     *
     * @return The name
     */
    default String getName() {
        return getMethod().getName();
    }

    /**
     * The method handle to access that method.
     *
     * @return The handle
     */
    MethodHandle getHandle();

    /**
     * Returns a handle that has a static context, i.e. if the method was already static, it just returns,
     * otherwise it's being bound to the instance.
     *
     * @param instanceFactory Used to get the instance for instance methods
     * @return The static handle
     */
    default MethodHandle getStaticHandle(Supplier<?> instanceFactory) {
        if (Modifier.isStatic(getModifiers())) {
            return getHandle();
        }
        return getHandle().bindTo(instanceFactory.get());
    }

    /**
     * Creates a lambda function of that particular method.
     * The lambda will run on a static context, i.e. no addional instance is expected.
     *
     * @param functionalType The type to create
     * @param instanceFactory Used to get the instance for instance methods
     * @param additionalInitializers These will be inserted as the first parameters of my method
     * @param <T> The functional type
     * @return The created type
     */
    <T> T lambdafy(Class<T> functionalType, Supplier<Object> instanceFactory, Object... additionalInitializers);
}
