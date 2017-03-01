package org.fiolino.common.util;

import org.fiolino.common.ioc.PostProcessor;
import org.fiolino.common.reflection.Methods;
import org.fiolino.data.annotation.Mandatory;

import javax.annotation.PostConstruct;
import java.lang.annotation.Annotation;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.util.function.Function;
import java.util.function.Supplier;

import static java.lang.invoke.MethodHandles.lookup;
import static java.lang.invoke.MethodType.methodType;

/**
 * Creates a {@link Supplier} or {@link Function} to instantiate objects.
 * <p>
 * Created by kuli on 10.02.15.
 */
public abstract class Instantiator<T> {

    private static final MethodHandles.Lookup ownLookup = lookup();

    private final MethodHandle constructor;
    private final Class<T> type;

    private Instantiator(Class<T> type, MethodHandle constructor) {
        this.type = type;
        this.constructor = constructor;
    }

    /**
     * Static methods to directly instantiate a type.
     * Esp. good for a one-time usage.
     *
     * @param type The type to instantiate; needs an empty public constructor
     * @param <T>  The type
     * @return The newly created instance
     */
    public static <T> T instantiate(Class<T> type) {
        return instantiate(ownLookup, type);
    }

    /**
     * Static methods to directly instantiate a type.
     * Esp. good for a one-time usage.
     *
     * @param lookup Use this to find the empty constructor
     * @param type   The type to instantiate; needs an empty constructor visible to the given Lookup
     * @param <T>    The type
     * @return The newly created instance
     */
    public static <T> T instantiate(MethodHandles.Lookup lookup, Class<T> type) {
        MethodHandle handle = findEmptyConstructor(lookup, type);
        return createInstance(type, handle);
    }

    /**
     * Creates a {@link Supplier} that will return a new instance on every call.
     *
     * @param type The type to instantiate; needs an empty public constructor
     * @param <T>  The type
     * @return The Supplier
     */
    public static <T> Supplier<T> creatorFor(Class<T> type) {
        return creatorFor(ownLookup, type);
    }

    /**
     * Creates a {@link Function} that will return a new instance on every call.
     *
     * @param type The type to instantiate; needs a public constructor with exactly one argument
     *             of type parameterType, or an empty one as an alternative
     * @param <T>  The type
     * @return The Function that accepts the only parameter and returns a freshly intantiated object
     */
    public static <T, P> Function<P, T> creatorWithArgument(Class<T> type, Class<P> parameterType) {
        return creatorWithArgument(ownLookup, type, parameterType);
    }

    /**
     * Creates a {@link Supplier} that will return a new instance on every call.
     *
     * @param lookup Use this to find the empty constructor
     * @param type   The type to instantiate; needs an empty constructor visible to the given Lookup
     * @param <T>    The type
     * @return The Supplier
     */
    public static <T> Supplier<T> creatorFor(MethodHandles.Lookup lookup, Class<T> type) {
        MethodHandle constructor = findEmptyConstructor(lookup, type);
        @SuppressWarnings("unchecked")
        Supplier<T> supplier = (Supplier<T>) Methods.lambdafy(lookup, constructor, Supplier.class);
        return supplier;
    }

    /**
     * Creates a {@link Function} that will return a new instance on every call.
     *
     * @param lookup Use this to find the constructor that must be either empty or accept exactly one argument
     *               of type parameterType; if both are available, the latter one is favored,
     * @param type   The type to instantiate; needs a constructor visible to the given Lookup with exactly one argument
     *               of type parameterType, or an empty one as an alternative
     * @param <T>    The type
     * @return The Function that accepts the only parameter and returns a freshly instantiated object
     */
    public static <T, P> Function<P, T> creatorWithArgument(MethodHandles.Lookup lookup, Class<T> type, Class<P> parameterType) {
        MethodHandle constructor = findConstructor(lookup, type, parameterType);
        @SuppressWarnings("unchecked")
        Function<P, T> function = (Function<P, T>) Methods.lambdafy(lookup, constructor, Function.class);
        return function;
    }

    /**
     * Gets the underlying type.
     */
    public final Class<T> getType() {
        return type;
    }

    private static MethodHandle findEmptyConstructor(MethodHandles.Lookup lookup, Class<?> type) {
        MethodHandle constructor;
        try {
            constructor = lookup.findConstructor(type, methodType(void.class));
        } catch (NoSuchMethodException | IllegalAccessException ex) {
            throw new AssertionError("Model class " + type.getName() + " has no empty public constructor!");
        }
        return insertPostProcessor(lookup, constructor, type);
    }

    private static <T> T createInstance(Class<T> type, MethodHandle constructor) {
        try {
            return type.cast(constructor.invoke());
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable t) {
            throw new InstantiationException("Cannot construct " + type.getName(), t);
        }
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " on " + type.getName();
    }

    @Override
    public boolean equals(Object obj) {
        return obj != null && obj.getClass().equals(getClass())
                && type.equals(((Instantiator<?>) obj).type);
    }

    @Override
    public int hashCode() {
        return type.hashCode() * 31 + 11;
    }

    private static MethodHandle findConstructor(MethodHandles.Lookup lookup, Class<?> type, Class<?> expectedParameterType) {
        Constructor<?> empty = null;
        Constructor<?> matched = null;
        int foundDistance = Integer.MAX_VALUE;

        for (Constructor<?> c : type.getDeclaredConstructors()) {
            if (!Methods.wouldBeVisible(lookup, c)) {
                continue;
            }
            Class<?>[] parameterTypes = c.getParameterTypes();
            switch (parameterTypes.length) {
                case 0:
                    empty = c;
                    continue;
                case 1:
                    Class<?> p = parameterTypes[0];
                    try {
                        int d = Types.getDistance(expectedParameterType, p);
                        if (Math.abs(d) > Math.abs(foundDistance)) {
                            continue;
                        }
                        foundDistance = d;
                        matched = c;
                    } catch (NotAssignableException ex) {
                        // continue...
                    }
            }
        }
        if (matched != null) {
            return getConstructorWithArgument(lookup, type, matched, foundDistance < 0);
        }
        if (empty == null) {
            throw new AssertionError("No public constructor found for " + type.getName());
        }

        MethodHandle constructor;
        try {
            constructor = lookup.unreflectConstructor(empty);
        } catch (IllegalAccessException ex) {
            throw new AssertionError(ex);
        }
        constructor = MethodHandles.dropArguments(constructor, 0, Object.class);
        return insertPostProcessor(lookup, constructor, type);
    }

    private static MethodHandle getConstructorWithArgument(MethodHandles.Lookup lookup, Class<?> type,
                                                           Constructor<?> c, boolean needsTypeCheck) {

        MethodHandle constructor;
        try {
            constructor = lookup.unreflectConstructor(c);
        } catch (IllegalAccessException ex) {
            throw new AssertionError(ex);
        }
        Class<?> realParameterType = c.getParameterTypes()[0];
        boolean isMandatory = hasReadOnlyAnnotation(c.getParameterAnnotations()[0]);
        if (!needsTypeCheck && (realParameterType.isPrimitive() || !isMandatory)) {
            return insertPostProcessor(lookup, constructor, type);
        }

        MethodHandle argumentGuardHandle;
        if (realParameterType.isPrimitive()) {
            ArgumentGuard argumentGuard = new ArgumentGuard(type.getName(), Types.toWrapper(realParameterType));
            argumentGuardHandle = argumentGuard.createHandleForPrimitive(realParameterType);
        } else {
            ArgumentGuard argumentGuard = new ArgumentGuard(type.getName(), realParameterType);
            argumentGuardHandle = argumentGuard.createHandle(isMandatory);
        }
        constructor = MethodHandles.filterArguments(constructor, 0, argumentGuardHandle);
        return insertPostProcessor(lookup, constructor, type);
    }

    private static boolean hasReadOnlyAnnotation(Annotation... annos) {
        for (Annotation a : annos) {
            if (a.annotationType().equals(Mandatory.class)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isPostProcessor(Class<?> type) {
        return PostProcessor.class.isAssignableFrom(type);
    }

    private static MethodHandle insertPostProcessor(MethodHandles.Lookup lookup, MethodHandle handle, Class<?> type) {
        MethodHandle fullConstructor;
        if (isPostProcessor(type)) {
            MethodHandle postConstruct;
            try {
                postConstruct = lookup.findVirtual(type, "postConstruct", methodType(void.class));
            } catch (NoSuchMethodException | IllegalAccessException ex) {
                throw new AssertionError("PostProcessor does not implement postConstruct?", ex);
            }
            MethodHandle returnItself = Methods.returnArgument(postConstruct, 0);
            fullConstructor = MethodHandles.filterReturnValue(handle, returnItself);
        } else {
            fullConstructor = handle;
        }

        return Methods.visitMethodsWithInstanceContext(lookup, type, fullConstructor, (h, m, supp) -> {
            if (!m.isAnnotationPresent(PostConstruct.class)) return h;
            MethodHandle postProcessor = supp.get();
            MethodType t = postProcessor.type();
            if (t.parameterCount() != 1) {
                throw new AssertionError(m + " is annotated with @PostCreate but has too many parameters.");
            }
            Class<?> returnType = t.returnType();
            if (returnType == void.class) {
                postProcessor = Methods.returnArgument(postProcessor, 0);
            } else if (type.isAssignableFrom(returnType)) {
                postProcessor = postProcessor.asType(t.changeReturnType(type));
            } else if (m.getDeclaringClass().isAssignableFrom(returnType)) {
                // Then this is a @PostConstruct method from a superclass with a type not matching any more - ignore it
                return h;
            } else {
                throw new AssertionError(m + " is annotated with @PostCreate but returns a type that does not match with " + type.getName());
            }
            return MethodHandles.filterReturnValue(h, postProcessor);
        });
    }

    private static class ArgumentGuard {
        private final String beanType;
        private final Class<?> expected;

        ArgumentGuard(String beanType, Class<?> expected) {
            this.beanType = beanType;
            this.expected = expected;
        }

        @SuppressWarnings("unused")
        private Object checkType(Object argument) {
            return argument == null ? null : checkMandatoryType(argument);
        }

        @SuppressWarnings("unused")
        private Object checkMandatoryType(Object argument) {
            if (!expected.isInstance(argument)) {
                throw new IllegalArgumentException(beanType + " expects " + expected.getName() + ", but argument was " + argument);
            }
            return argument;
        }

        private MethodHandle createHandle(Class<?> returnType, boolean mandatory) {
            try {
                MethodHandle checkHandle = MethodHandles.lookup().bind(this,
                        mandatory ? "checkMandatoryType" : "checkType",
                        methodType(Object.class, Object.class));
                return checkHandle.asType(checkHandle.type().changeReturnType(returnType));
            } catch (NoSuchMethodException | IllegalAccessException ex) {
                throw new AssertionError(ex);
            }
        }

        MethodHandle createHandle(boolean mandatory) {
            return createHandle(expected, mandatory);
        }

        MethodHandle createHandleForPrimitive(Class<?> primitiveType) {
            return createHandle(primitiveType, true);
        }
    }
}
