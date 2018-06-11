package org.fiolino.common.reflection;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Array;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.Arrays;
import java.util.function.IntFunction;

import static java.lang.invoke.MethodHandles.publicLookup;
import static java.lang.invoke.MethodType.methodType;
import static org.fiolino.common.reflection.Methods.createLambdaFactory;
import static org.fiolino.common.reflection.Methods.lambdafy;

/**
 * Used to create a copy of an existing array with a new length.
 *
 * Like Arrays.opyOf(), but with variable array types (does allow primitive arrays).
 *
 * @param <A> The array type - int[], String[], Object[][], or whatever
 */
@FunctionalInterface
public interface ArrayCopier<A> {

    /**
     * Calls Arrays.copyOf() with the given argument.
     * Will fail if original is not of the accepted type.
     */
    A copyOf(A original, int newLength);

    /**
     * Creates an {@link ArrayCopier}.
     *
     * Calling the single copyOf() method will create a copy of the given array with the new length.
     * Internally, Arrays.copyOf() is used, but allows primitive and Object arrays.
     *
     * @param arrayType The array type to copy
     * @return A Copier
     */
    static <A> ArrayCopier<A> createFor(Class<A> arrayType) {
        if (!arrayType.isArray()) {
            throw new IllegalArgumentException(arrayType.getName() + " is expected to be an array!");
        }
        Class<?> argument = arrayType.getComponentType().isPrimitive() ? arrayType : Object[].class;
        MethodHandle copOf;
        try {
            copOf = publicLookup().findStatic(Arrays.class, "copyOf", methodType(argument, argument, int.class));
        } catch (NoSuchMethodException | IllegalAccessException ex) {
            throw new IllegalArgumentException(argument.getName() + " is not implemented in Arrays.copyOf", ex);
        }

        @SuppressWarnings("unchecked")
        ArrayCopier<A> copier = lambdafy(copOf, ArrayCopier.class);
        return copier;
    }

    /**
     * Creates an {@link IntFunction} that serves as a factory to create array of a given component type.
     *
     * The resulting function will return a new array of the called size and the specified type.
     *
     * @param componentType The component type of the created arrays
     * @param <A> The array type (should be componentType[])
     * @return A factory function
     */
    static <A> IntFunction<A> factoryForArrayOfType(Class<?> componentType) {
        try {
            @SuppressWarnings("unchecked")
            IntFunction<A> factory = (IntFunction<A>) Factory.lambdaFactory.invokeExact(componentType);
            return factory;
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable t) {
            throw new UndeclaredThrowableException(t);
        }
    }
}

class Factory {
    static final MethodHandle lambdaFactory;

    static {
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        MethodHandle factory;
        try {
            factory = lookup.findStatic(Array.class, "newInstance", MethodType.methodType(Object.class, Class.class, int.class));
        } catch (NoSuchMethodException | IllegalAccessException ex) {
            throw new AssertionError(ex);
        }
        lambdaFactory = createLambdaFactory(lookup, factory, IntFunction.class);
        if (lambdaFactory == null) {
            throw new AssertionError(factory + " should be a direct method handle!");
        }
    }
}
