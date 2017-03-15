package org.fiolino.common.util;

import org.fiolino.common.ioc.PostProcessor;
import org.fiolino.common.reflection.Methods;

import javax.annotation.Nullable;
import javax.annotation.PostConstruct;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;

import static java.lang.invoke.MethodHandles.lookup;
import static java.lang.invoke.MethodType.methodType;

/**
 * Creates a {@link Supplier} or {@link Function} to instantiate objects.
 * <p>
 * Instantiators may have individual {@link java.lang.invoke.MethodHandles.Lookup} instances as well as own providers.
 * <p>
 * Lookups are used to identify constructors and factory methods.
 * <p>
 * Created by kuli on 10.02.15.
 */
public final class Instantiator {

    private static final Instantiator DEFAULT = new Instantiator();

    /**
     * The default instantiator, whoch is an instance without any specific providers.
     */
    public static Instantiator getDefault() {
        return DEFAULT;
    }

    /**
     * Creates a new instance with a given list of providers.
     *
     * @param providers See the type description about possible provider types.
     * @return The instantiator
     */
    public static Instantiator withProviders(Object... providers) {
        return withProviders(lookup(), providers);
    }

    public static Instantiator withProviders(MethodHandles.Lookup lookup, Object... providers) {
        return new Instantiator(lookup, new ArrayList<>(providers.length)).registerAllTypes(providers);
    }

    public static Instantiator forLookup(MethodHandles.Lookup lookup) {
        return getDefault().withLookup(lookup);
    }

    private static abstract class HandleContainer {
        final MethodHandle handle;

        HandleContainer(MethodHandle handle) {
            this.handle = handle;
        }

        abstract MethodHandle getProviderFor(MethodType type);
    }

    private static class DirectHandleContainer extends HandleContainer {
        DirectHandleContainer(MethodHandle handle) {
            super(handle);
        }

        @Override
        public MethodHandle getProviderFor(MethodType type) {
            if (type.equals(handle.type())) return handle;
            return null;
        }
    }

    private static class GenericHandleContainer extends HandleContainer {
        private final int argumentIndex;
        private final Class<?> subscribedClass;
        private final Class<?>[] expectedArguments;

        GenericHandleContainer(MethodHandle handle, int argumentIndex, Class<?> subscribedClass) {
            super(handle);
            this.argumentIndex = argumentIndex;
            this.subscribedClass = subscribedClass;

            int argCount = handle.type().parameterCount() - 1;
            expectedArguments = new Class<?>[argCount];
            for (int i=0, j=0; j < argCount; i++) {
                if (i == argumentIndex) continue;
                expectedArguments[j++] = handle.type().parameterType(i);
            }
        }

        @Override
        public MethodHandle getProviderFor(MethodType type) {
            Class<?> requestedClass = type.returnType();
            if (!subscribedClass.isAssignableFrom(requestedClass)) {
                return null;
            }
            if (!Arrays.equals(expectedArguments, type.parameterArray())) {
                return null;
            }
            return MethodHandles.insertArguments(handle, argumentIndex, requestedClass);
        }
    }
    private final MethodHandles.Lookup lookup;
    private final List<HandleContainer> providers;

    private Instantiator(MethodHandles.Lookup lookup, List<HandleContainer> providers) {
        this.lookup = lookup;
        this.providers = providers;
    }

    private Instantiator(MethodHandles.Lookup lookup) {
        this(lookup, Collections.emptyList());
    }

    private Instantiator() {
        this(lookup());
    }

    public Instantiator withLookup(MethodHandles.Lookup lookup) {
        return new Instantiator(lookup, providers);
    }

    public Instantiator addProviders(Object... anything) {
        if (anything.length == 0) {
            return this;
        }
        return new Instantiator(lookup, new ArrayList<>(providers)).registerAllTypes(anything);
    }

    final MethodHandles.Lookup getLookup() {
        return lookup;
    }

    private Instantiator registerAllTypes(Object... providers) {
        for (Object p : providers) {
            if (p instanceof Class) {
                register((Class<?>) p);
            } else if (p instanceof MethodHandle) {
                register(makeOptional((MethodHandle) p), -1);
            } else if (p instanceof Method) {
                register((Method) p);
            } else {
                register(p);
            }
        }
        return this;
    }

    private void register(Method provider) {
        MethodHandle h;
        try {
            h = lookup.unreflect(provider);
        } catch (IllegalAccessException ex) {
            throw new IllegalArgumentException(provider + " is not accessible.", ex);
        }
        if (!Modifier.isStatic(provider.getModifiers())) {
            h = h.bindTo(instantiate(provider.getDeclaringClass()));
        }
        register(makeOptional(h), findRequestedTypeParameter(provider));
    }

    private <T> void register(Class<T> providerClass) {
        Supplier<T> metaFactory = createSupplierFor(providerClass);
        Methods.visitMethodsWithStaticContext(lookup, providerClass, metaFactory, null, (v, m, supp) -> {
            Provider annotation = m.getAnnotation(Provider.class);
            if (annotation == null) return null;

            MethodHandle provider = supp.get();
            if (annotation.optional() || m.isAnnotationPresent(Nullable.class)) {
                provider = makeOptional(provider);
            }
            register(provider, findRequestedTypeParameter(m));
            return null;
        });
    }

    private <T> void register(Object providerInstance) {
        Methods.visitMethodsWithStaticContext(lookup, providerInstance, null, (v, m, supp) -> {
            Provider annotation = m.getAnnotation(Provider.class);
            if (annotation == null) return null;

            MethodHandle provider = supp.get();
            if (annotation.optional() || m.isAnnotationPresent(Nullable.class)) {
                provider = makeOptional(provider);
            }
            register(provider, findRequestedTypeParameter(m));
            return null;
        });
    }

    private MethodHandle makeOptional(MethodHandle provider) {
        MethodType type = provider.type();
        Class<?> r = type.returnType();
        MethodHandle existing = findProviderOrGeneric(type);
        existing = MethodHandles.dropArguments(existing, 0, r); // First argument would be null anyway
        MethodHandle identity = MethodHandles.identity(r);
        identity = Methods.dropAllOf(identity, existing.type(), 1);

        MethodHandle nullCheck = Methods.nullCheck().asType(methodType(boolean.class, r));
        MethodHandle checkedExisting = MethodHandles.guardWithTest(nullCheck, existing, identity);
        checkedExisting = checkedExisting.asType(checkedExisting.type().changeParameterType(0, r));
        return MethodHandles.foldArguments(checkedExisting, provider);
    }

    private void register(MethodHandle provider, int typeIndex) {
        MethodType type = provider.type();
        if (type.returnType().isPrimitive()) {
            throw new AssertionError("Provider " + provider + " returns a primitive");
        }
        HandleContainer p = typeIndex < 0 ? new DirectHandleContainer(provider) : new GenericHandleContainer(provider, typeIndex, type.returnType());
        providers.add(0, p);
    }

    private int findRequestedParameter(Method m) {
        int i=0;
        for (Parameter p : m.getParameters()) {
            if (p.isAnnotationPresent(Requested.class)) {
                return i;
            }
            i++;
        }
        return -1;
    }

    private int findRequestedTypeParameter(Method m) {
        int arg = findRequestedParameter(m);
        if (arg >= 0) {
            Class<?> t = m.getParameterTypes()[arg];
            if (!Class.class.equals(t)) {
                throw new AssertionError("Parameter #" + arg + " of " + m + " is annotated with @Requested but of wrong type " + t);
            }
        }
        return arg;
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
        MethodHandle handle = findProvider(methodType(type));
        return createInstance(type, handle);
    }

    /**
     * Creates a {@link Supplier} that will return a new instance on every call.
     *
     * @param type The type to instantiate; needs an empty public constructor
     * @param <T>  The type
     * @return The Supplier
     */
    public <T> Supplier<T> createSupplierFor(Class<T> type) {
        @SuppressWarnings("unchecked")
        Supplier<T> supplier = createProviderFor(Supplier.class, type);
        return supplier;
    }

    /**
     * Creates a {@link Function} that will return a new instance on every call.
     *
     * @param type The type to instantiate; needs a public constructor with exactly one argument
     *             of type argumentType, or an empty one as an alternative
     * @param <T>  The type
     * @return The Function that accepts the only parameter and returns a freshly intantiated object
     */
    public <T, P> Function<P, T> createFunctionFor(Class<T> type, Class<P> argumentType) {
        @SuppressWarnings("unchecked")
        Function<P, T> function = createProviderFor(Function.class, type, argumentType);
        return function;
    }

    /**
     * Creates a provider which implements the given functional interface an calls the appropriate provider method or constructor.
     *
     * @param functionalInterface Describes the provider/constructor to use. Its method signature specifies the parameter values and return type.
     * @param <T> The interface type
     * @return A lambda or proxy
     */
    public <T> T createProviderFor(Class<T> functionalInterface) {
        Method lambdaMethod = Methods.findLambdaMethodOrFail(functionalInterface);
        MethodHandle provider = findProvider(lambdaMethod.getReturnType(), lambdaMethod.getParameterTypes());
        return Methods.lambdafy(lookup, provider, functionalInterface);
    }

    /**
     * Creates a provider which implements the given functional interface and calls the appropriate provider method or constructor.
     * This method can be used when the given interface is generic, so that the signature doesn't specify the argument and return type enough.
     *
     * @param functionalInterface Describes the provider/constructor to use. Its method signature specifies parts of the parameter values.
     * @param returnType The expected return type
     * @param <T> The interface type
     * @return A lambda or proxy
     */
    public <T> T createProviderFor(Class<T> functionalInterface, Class<?> returnType, Class<?>... parameterTypes) {
        Method lambdaMethod = Methods.findLambdaMethodOrFail(functionalInterface);

        Class<?>[] lambdaTypes = lambdaMethod.getParameterTypes();
        if (lambdaTypes.length < parameterTypes.length) {
            throw new IllegalArgumentException("Given too many parameter types: Expected " +
                    Arrays.toString(lambdaTypes) + ", given " + Arrays.toString(parameterTypes));
        }
        System.arraycopy(parameterTypes, 0, lambdaTypes, 0, parameterTypes.length);
        MethodHandle provider = findProvider(returnType, lambdaTypes);
        return Methods.lambdafy(lookup, provider, functionalInterface);
    }

    public MethodHandle findProvider(Class<?> returnType, Class<?>... parameterTypes) {
        return findProvider(methodType(returnType, parameterTypes));
    }

    private MethodHandle findProviderOrGeneric(MethodType methodType) {
        return providers.stream().map(p -> p.getProviderFor(methodType)).filter(Objects::nonNull).findFirst()
                .orElseGet(() -> findConstructor(methodType));
    }

    public MethodHandle findProvider(MethodType methodType) {
        return withPostProcessor(findProviderOrGeneric(methodType));
    }

    private MethodHandle findConstructor(MethodType methodType) {
        MethodHandle constructor;
        try {
            constructor = lookup.findConstructor(methodType.returnType(), methodType.changeReturnType(void.class));
        } catch (NoSuchMethodException | IllegalAccessException ex) {
            throw new AssertionError("No constructor with parameters " + Arrays.toString(methodType.parameterArray()) + " in " + methodType.returnType().getName());
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

    private boolean isPostProcessor(Class<?> type) {
        return PostProcessor.class.isAssignableFrom(type);
    }

    private MethodHandle withPostProcessor(MethodHandle handle) {
        MethodHandle fullConstructor;
        Class<?> type = handle.type().returnType();
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
                throw new AssertionError(m + " is annotated with @" + PostConstruct.class.getSimpleName() + " but has too many parameters.");
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
}
