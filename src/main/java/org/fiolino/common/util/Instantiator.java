package org.fiolino.common.util;

import org.fiolino.common.ioc.PostProcessor;
import org.fiolino.common.reflection.Methods;
import org.fiolino.data.annotation.Mandatory;

import javax.annotation.Nullable;
import javax.annotation.PostConstruct;
import java.lang.annotation.Annotation;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandleInfo;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

import static java.lang.invoke.MethodHandles.lookup;
import static java.lang.invoke.MethodType.methodType;

/**
 * Creates a {@link Supplier} or {@link Function} to instantiate objects.
 * <p>
 * Created by kuli on 10.02.15.
 */
public final class Instantiator {

    private static final Instantiator DEFAULT = new Instantiator();

    public static Instantiator getDefault() {
        return DEFAULT;
    }

    private final MethodHandles.Lookup lookup;
    private final Map<Class<?>, MethodHandle> emptyFactories = new HashMap<>();
    private final Map<Class<?>, Map<Class<?>, MethodHandle>> argumentFactories = new HashMap<>();

    public Instantiator(MethodHandles.Lookup lookup) {
        this.lookup = lookup;
    }

    public Instantiator() {
        this(lookup());
    }

    public <T> void registerFactory(Class<T> factoryClass) {
        Supplier<T> metaFactory = creatorFor(factoryClass);
        Methods.visitMethodsWithStaticContext(lookup, factoryClass, metaFactory, null, (v, m, supp) -> {
            if (!m.isAnnotationPresent(Provider.class)) return null;
            
            Class<?> r = m.getReturnType();
            if (r.isPrimitive()) {
                throw new AssertionError("Provider " + m + " returns a primitive");
            }
            switch (m.getParameterCount()) {
                case 0:
                    emptyFactories.put(r, supp.get());
                    return null;
                case 1:
                    Map<Class<?>, MethodHandle> factoryPerArgumentType = argumentFactories.computeIfAbsent(r, x -> new HashMap<>());
                    factoryPerArgumentType.put(m.getParameterTypes()[0], supp.get());
                    return null;
                default:
                    throw new AssertionError(m + " has too many parameters; currently only one is supported.");
            }
        });
    }

    /**
     * Static methods to directly instantiate a type.
     * Esp. good for a one-time usage.
     *
     * @param type The type to instantiate; needs an empty public constructor
     * @param <T>  The type
     * @return The newly created instance
     */
    public <T> T instantiate(Class<T> type) {
        MethodHandle handle = findEmptyConstructor(type);
        return createInstance(type, handle);
    }

    /**
     * Creates a {@link Supplier} that will return a new instance on every call.
     *
     * @param type The type to instantiate; needs an empty public constructor
     * @param <T>  The type
     * @return The Supplier
     */
    public <T> Supplier<T> creatorFor(Class<T> type) {
        MethodHandle constructor = findEmptyConstructor(type);
        @SuppressWarnings("unchecked")
        Supplier<T> supplier = Methods.lambdafy(lookup, constructor, Supplier.class);
        return supplier;
    }

    /**
     * Creates a {@link Function} that will return a new instance on every call.
     *
     * @param type The type to instantiate; needs a public constructor with exactly one argument
     *             of type parameterType, or an empty one as an alternative
     * @param <T>  The type
     * @return The Function that accepts the only parameter and returns a freshly intantiated object
     */
    public <T, P> Function<P, T> creatorWithArgument(Class<T> type, Class<P> parameterType) {
        MethodHandle constructor = findConstructor(type, parameterType);
        @SuppressWarnings("unchecked")
        Function<P, T> function = Methods.lambdafy(lookup, constructor, Function.class);
        return function;
    }

    private MethodHandle findEmptyConstructor(Class<?> type) {
        MethodHandle constructor = findEmptyConstructorOrNull(type);
        if (constructor == null) {
            throw new AssertionError("Model class " + type.getName() + " has no empty public constructor!");
        }
        return withPostProcessor(constructor, type);
    }

    private MethodHandle findEmptyConstructorOrNull(Class<?> type) {
        MethodHandle c = emptyFactories.get(type);
        if (c != null) return c;
        return findConstructor(type, methodType(void.class));
    }

    @Nullable
    private MethodHandle findConstructor(Class<?> type, MethodType methodType) {
        MethodHandle constructor;
        try {
            constructor = lookup.findConstructor(type, methodType);
        } catch (NoSuchMethodException | IllegalAccessException ex) {
            return null;
        }
        return constructor;
    }

    private <T> T createInstance(Class<T> type, MethodHandle constructor) {
        try {
            return type.cast(constructor.invoke());
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable t) {
            throw new InstantiationException("Cannot construct " + type.getName(), t);
        }
    }

    private MethodHandle findConstructor(Class<?> type, Class<?> expectedParameterType) {
        MethodHandle fromFactory = argumentFactories.getOrDefault(type, Collections.emptyMap()).get(expectedParameterType);
        if (fromFactory != null) return fromFactory;

        MethodHandle exactMatch = findConstructor(type, methodType(void.class, expectedParameterType));
        if (exactMatch != null) {
            MethodHandleInfo info = lookup.revealDirect(exactMatch);
            Constructor<?> c = info.reflectAs(Constructor.class, lookup);
            return checkMandatoryArgument(type, exactMatch, c, false);
        }

        MethodHandle constructorWithParameterInHierarchy = findConstructorWithParameterInHierarchy(type, expectedParameterType);
        if (constructorWithParameterInHierarchy != null) {
            return constructorWithParameterInHierarchy;
        }

        MethodHandle empty = findEmptyConstructorOrNull(type);
        if (empty == null) {
            throw new AssertionError("No public constructor found for " + type.getName());
        }
        empty = MethodHandles.dropArguments(empty, 0, expectedParameterType);
        return withPostProcessor(empty, type);
    }

    private MethodHandle findConstructorWithParameterInHierarchy(Class<?> type, Class<?> expectedParameterType) {
        Constructor<?> matched = null;
        int foundDistance = Integer.MAX_VALUE;

        for (Constructor<?> c : type.getDeclaredConstructors()) {
            if (!Methods.wouldBeVisible(lookup, c)) {
                continue;
            }
            Class<?>[] parameterTypes = c.getParameterTypes();
            if (parameterTypes.length == 1) {
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
            return getConstructorWithArgument(type, matched, foundDistance < 0);
        }
        return null;
    }

    private MethodHandle getConstructorWithArgument(Class<?> type,
                                                           Constructor<?> c, boolean needsTypeCheck) {

        MethodHandle constructor;
        try {
            constructor = lookup.unreflectConstructor(c);
        } catch (IllegalAccessException ex) {
            throw new AssertionError(ex);
        }
        return checkMandatoryArgument(type, constructor, c, needsTypeCheck);
    }

    private MethodHandle checkMandatoryArgument(Class<?> type, MethodHandle constructor, Constructor<?> c, boolean needsTypeCheck) {
        Class<?> realParameterType = c.getParameterTypes()[0];
        boolean isMandatory = hasReadOnlyAnnotation(c.getParameterAnnotations()[0]);
        if (!needsTypeCheck && (realParameterType.isPrimitive() || !isMandatory)) {
            return withPostProcessor(constructor, type);
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
        return withPostProcessor(constructor, type);
    }

    private boolean hasReadOnlyAnnotation(Annotation... annos) {
        for (Annotation a : annos) {
            if (a.annotationType().equals(Mandatory.class)) {
                return true;
            }
        }
        return false;
    }

    private boolean isPostProcessor(Class<?> type) {
        return PostProcessor.class.isAssignableFrom(type);
    }

    private MethodHandle withPostProcessor(MethodHandle handle, Class<?> type) {
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

    private static final class ArgumentGuard {
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
