package org.fiolino.common.ioc;

import org.fiolino.annotations.PostCreate;
import org.fiolino.annotations.PostProcessor;
import org.fiolino.annotations.Provider;
import org.fiolino.annotations.Requested;
import org.fiolino.common.reflection.MethodLocator;
import org.fiolino.common.reflection.Methods;
import org.fiolino.common.reflection.OneTimeExecution;
import org.fiolino.common.util.Types;

import javax.annotation.Nullable;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandleInfo;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.*;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;

import static java.lang.invoke.MethodHandles.identity;
import static java.lang.invoke.MethodHandles.lookup;
import static java.lang.invoke.MethodType.methodType;

/**
 * Creates a {@link Supplier} or {@link Function} to instantiate objects.
 * <p>
 * Instantiators may have individual {@link java.lang.invoke.MethodHandles.Lookup} instances as well as own providers.
 * A provider can be:
 * <ul>
 *     <li>A {@link Method}: Will be used as a factory method to instantiate if the return type matches.</li>
 *     <li>A {@link MethodHandle}: Like the method.</li>
 *     <li>A {@link Class}: Tries to find all methods with a @{@link org.fiolino.annotations.Provider} annotation which will serve as factory methods then.
 *     Static methods will be used directly, while for instance methods, a single factory instance gets constructed initially.</li>
 *     <li>Any object: Like the class case, but for instance methods, the given instance will be the only factory instance.</li>
 * </ul>
 * <p>
 * Lookups are used to identify constructors and factory methods.
 * <p>
 * If the instantiated instance implements {@link org.fiolino.annotations.PostProcessor}, then its postConstruct method will be called automatically.
 * <p>
 * If the used provider class, if there is any, has a method annotated with {@link PostCreate}, then this method will be
 * called after construction. The method can accept one argument, which is the constructed instance, and may return
 * another instance which will be used as a replacement.
 * <p>
 * Created by kuli on 10.02.15.
 */
public final class Instantiator {

    private static final Instantiator DEFAULT = new Instantiator();

    /**
     * The default instantiator, which is an instance without any specific providers.
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

    /**
     * Creates a new instance with a lookup and a given list of providers.
     *
     * @param lookup Used to find the constructor
     * @param providers See the type description about possible provider types.
     * @return The instantiator
     */
    public static Instantiator withProviders(MethodHandles.Lookup lookup, Object... providers) {
        return new Instantiator(lookup, new ArrayList<>(providers.length)).registerAllTypes(providers);
    }

    /**
     * Creates a new instance with a lookup.
     *
     * @param lookup Used to find the constructor
     * @return The instantiator
     */
    public static Instantiator forLookup(MethodHandles.Lookup lookup) {
        return new Instantiator(lookup);
    }

    private static abstract class HandleProvider {
        abstract Optional<MethodHandle> getProviderFor(MethodHandles.Lookup lookup, MethodType type);

        <T> Optional<T> lambdafy(MethodHandles.Lookup lookup, Class<T> functionalInterface, MethodType methodType) {
            return getProviderFor(lookup, methodType).map(h -> Methods.lambdafy(lookup, h, functionalInterface));
        }
    }

    private static class DirectHandleProvider extends HandleProvider {
        final MethodHandle handle;

        DirectHandleProvider(MethodHandle handle) {
            this.handle = handle;
        }

        @Override
        public Optional<MethodHandle> getProviderFor(MethodHandles.Lookup lookup, MethodType type) {
            if (type.equals(handle.type())) return Optional.of(handle);
            return Optional.empty();
        }
    }

    private static class HandleFromInstantiableProviderProvider extends DirectHandleProvider {
        // ()Object
        private final MethodHandle providerFactory;

        HandleFromInstantiableProviderProvider(MethodHandle handle, MethodHandle providerFactory) {
            super(handle);
            this.providerFactory = providerFactory;
        }

        @Override
        public Optional<MethodHandle> getProviderFor(MethodHandles.Lookup lookup, MethodType type) {
            if (matches(type)) {
                return Optional.of(MethodHandles.foldArguments(handle, providerFactory));
            }
            return Optional.empty();
        }

        private boolean matches(MethodType type) {
            if (!returnTypeMatches(type.returnType())) return false;
            int additionalParameterCount = additionalParameterCount();
            MethodType myType = handle.type();
            if (myType.parameterCount() != type.parameterCount() + additionalParameterCount) return false;
            for (int i = type.parameterCount(); i >= additionalParameterCount; i--) {
                if (!myType.parameterType(i).equals(type.parameterType(i-additionalParameterCount))) return false;
            }
            return true;
        }

        int additionalParameterCount() {
            return 1;
        }

        boolean returnTypeMatches(Class<?> requestedReturnType) {
            return handle.type().returnType().equals(requestedReturnType);
        }

        @Override
        <T> Optional<T> lambdafy(MethodHandles.Lookup lookup, Class<T> functionalInterface, MethodType methodType) {
            if (!matches(methodType)) return Optional.empty();
            Object provider;
            try {
                provider = providerFactory.invoke();
            } catch (RuntimeException | Error e) {
                throw e;
            } catch (Throwable t) {
                throw new UndeclaredThrowableException(t, "Instantiation of " + providerName() + " failed");
            }
            if (provider == null) {
                throw new NullPointerException(providerName());
            }
            return Optional.of(lambdafy(lookup, functionalInterface, provider, methodType.returnType()));
        }

        <T> T lambdafy(MethodHandles.Lookup lookup, Class<T> functionalInterface, Object provider, Class<?> requestedType) {
            return Methods.lambdafy(lookup, handle, functionalInterface, provider);
        }

        private String providerName() {
            return providerFactory.type().returnType().getName();
        }
    }

    private static class HandleWithTypeInfoFromInstantiableProviderProvider extends HandleFromInstantiableProviderProvider {
        private final Class<?> upperBound;

        HandleWithTypeInfoFromInstantiableProviderProvider(MethodHandle handle, MethodHandle providerFactory, Class<?> upperBound) {
            super(handle, providerFactory);
            this.upperBound = upperBound;
        }

        @Override
        boolean returnTypeMatches(Class<?> requestedReturnType) {
            return upperBound.isAssignableFrom(requestedReturnType);
        }

        @Override
        int additionalParameterCount() {
            return 2;
        }

        @Override
        <T> T lambdafy(MethodHandles.Lookup lookup, Class<T> functionalInterface, Object provider, Class<?> requestedType) {
            return Methods.lambdafy(lookup, handle, functionalInterface, provider, requestedType);
        }
    }

    private static class GenericHandleProvider extends DirectHandleProvider {
        private final int argumentIndex;
        private final Class<?> subscribedClass;
        private final Class<?>[] expectedArguments;

        GenericHandleProvider(MethodHandle handle, int argumentIndex, Class<?> subscribedClass) {
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
        public Optional<MethodHandle> getProviderFor(MethodHandles.Lookup lookup, MethodType type) {
            if (matches(type)) {
                return Optional.of(MethodHandles.insertArguments(handle, argumentIndex, type.returnType()));
            }

            return Optional.empty();
        }

        private boolean matches(MethodType type) {
            return subscribedClass.isAssignableFrom(type.returnType()) && Arrays.equals(expectedArguments, type.parameterArray());
        }

        @Override
        <T> Optional<T> lambdafy(MethodHandles.Lookup lookup, Class<T> functionalInterface, MethodType methodType) {
            if (matches(methodType) && argumentIndex == 0) {
                return Optional.of(Methods.lambdafy(lookup, handle, functionalInterface, methodType.returnType()));
            }
            return super.lambdafy(lookup, functionalInterface, methodType);
        }
    }

    private static class DynamicHandleProvider extends HandleProvider {
        private final MethodHandleProvider handleProvider;

        DynamicHandleProvider(MethodHandleProvider handleProvider) {
            this.handleProvider = handleProvider;
        }

        @Override
        Optional<MethodHandle> getProviderFor(MethodHandles.Lookup lookup, MethodType type) {
            try {
                return Optional.ofNullable(handleProvider.createFor(lookup, type)).map(h -> h.asType(type));
            } catch (NoSuchMethodException | IllegalAccessException ex) {
                return Optional.empty();
            }
        }

        @Override
        <T> Optional<T> lambdafy(MethodHandles.Lookup lookup, Class<T> functionalInterface, MethodType methodType) {
            try {
                return handleProvider.lambdafy(lookup, functionalInterface, methodType);
            } catch (NoSuchMethodException | IllegalAccessException ex) {
                return Optional.empty();
            }
        }
    }

    private final MethodHandles.Lookup lookup;
    private final List<HandleProvider> providers;

    private Instantiator(MethodHandles.Lookup lookup, List<HandleProvider> providers) {
        this.lookup = lookup;
        this.providers = providers;
    }

    private Instantiator(MethodHandles.Lookup lookup) {
        this(lookup, Collections.emptyList());
    }

    private Instantiator() {
        this(lookup());
    }

    /**
     * Creates a new Instantiator with the same settings as me, but with a different {@link java.lang.invoke.MethodHandles.Lookup}.
     *
     * @param lookup Used to find constructors and provider methods
     * @return The new Instantiator
     */
    public Instantiator withLookup(MethodHandles.Lookup lookup) {
        return new Instantiator(lookup, providers);
    }

    /**
     * Creates a new Instantiator with the same lookup and existing providers, plus the ones added here.
     *
     * @param anything As described in the class documentation: A provider class or instance, or a Method or MethodHandle.
     * @return The new Instantiator
     */
    public Instantiator addProviders(Object... anything) {
        if (anything.length == 0) {
            return this;
        }
        return new Instantiator(lookup, new ArrayList<>(providers)).registerAllTypes(anything);
    }

    /**
     * Gets the lookup that is used for this {@link Instantiator}.
     */
    final MethodHandles.Lookup getLookup() {
        return lookup;
    }

    private Instantiator registerAllTypes(Object... providers) {
        for (Object p : providers) {
            if (p instanceof Class) {
                register((Class<?>) p);
            } else if (p instanceof MethodHandle) {
                register((MethodHandle) p);
            } else if (p instanceof Method) {
                register((Method) p, isOptional((Method) p), null);
            } else if (p instanceof MethodHandleProvider) {
                register((MethodHandleProvider) p);
            } else {
                register(p);
            }
        }
        return this;
    }

    private void register(MethodHandleProvider provider) {
        providers.add(0, new DynamicHandleProvider(provider));
    }

    private void register(MethodHandle providerHandle) {
        HandleProvider handleProvider;
        try {
            MethodHandleInfo info = AccessController.doPrivileged((PrivilegedAction<MethodHandleInfo>) () -> lookup.revealDirect(providerHandle));
            try {
                Method m = info.reflectAs(Method.class, lookup);
                RequestedClass requestedClass = findRequestedClass(m);
                register(providerHandle, info.getDeclaringClass(), isOptional(m), requestedClass, Modifier.isStatic(info.getModifiers()), null);
            } catch (ClassCastException ex2) {
                // Not a method?
                register(providerHandle, info.getDeclaringClass(), false, null, Modifier.isStatic(info.getModifiers()), null);
            }
        } catch (IllegalArgumentException ex) {
            // providerHandle seems not to be direct
            handleProvider = new DirectHandleProvider(makeOptional(providerHandle));
            providers.add(0, handleProvider);
        }
    }

    private boolean isOptional(Method provider) {
        Provider annotation;
        return provider.isAnnotationPresent(Nullable.class) || ((annotation = provider.getAnnotation(Provider.class)) != null && annotation.optional());
    }

    private void register(Method provider, boolean optional, Object providerInstanceOrNull) {
        Class<?> instantiatedType = provider.getReturnType();
        checkReturnType(instantiatedType, provider);
        MethodHandle handle;
        try {
            handle = lookup.unreflect(provider);
        } catch (IllegalAccessException ex) {
            throw new IllegalArgumentException(provider + " is not accessible.", ex);
        }
        RequestedClass requestedClass = findRequestedClass(provider);
        register(handle, provider.getDeclaringClass(), optional, requestedClass, Modifier.isStatic(provider.getModifiers()), providerInstanceOrNull);
    }

    private void register(MethodHandle providerHandle, Class<?> providerClass, boolean optional, RequestedClass requestedClass, boolean isStatic, Object providerInstanceOrNull) {
        MethodHandle h = withPostProcessor(providerHandle);
        HandleProvider handleProvider;
        if (isStatic) {
            handleProvider = createStaticNondirectHandleProvider(h, optional, requestedClass);
        } else {
            if (optional) {
                handleProvider = createVirtualNondirectHandleProvider(providerClass, h, true, requestedClass);
            } else {
                if (requestedClass != null && requestedClass.parameterIndex > 1 || h != providerHandle) {
                    // Cannot be a direct lambda method
                    handleProvider = createVirtualNondirectHandleProvider(providerClass, h, false, requestedClass);
                } else {
                    // Might be direct
                    MethodHandle factory = providerInstanceOrNull == null ?
                            OneTimeExecution.createFor(findProviderHandle(providerClass)).getAccessor() : identity(providerClass).bindTo(providerInstanceOrNull);
                    handleProvider = requestedClass == null ? new HandleFromInstantiableProviderProvider(providerHandle, factory)
                            : new HandleWithTypeInfoFromInstantiableProviderProvider(providerHandle, factory, commonSubclassOf(providerHandle.type().returnType(), requestedClass.upperBound));
                }
            }
        }
        providers.add(0, handleProvider);
    }

    private HandleProvider createVirtualNondirectHandleProvider(Class<?> providerClass, MethodHandle handle, boolean optional, RequestedClass requestedClass) {
        handle = handle.bindTo(instantiate(providerClass));
        return createStaticNondirectHandleProvider(handle, optional, requestedClass);
    }

    private HandleProvider createStaticNondirectHandleProvider(MethodHandle handle, boolean optional, RequestedClass requestedClass) {
        if (optional) handle = makeOptional(handle);
        return requestedClass == null ? new DirectHandleProvider(handle) :
                new GenericHandleProvider(handle, requestedClass.parameterIndex, commonSubclassOf(handle.type().returnType(), requestedClass.upperBound));
    }

    private Class<?> commonSubclassOf(Class<?> returnType, Class<?> parameterizedArgument) {
        if (returnType.isAssignableFrom(parameterizedArgument)) return parameterizedArgument;
        if (parameterizedArgument.isAssignableFrom(returnType)) return returnType;
        throw new AssertionError("Bad provider method: Return type " + returnType.getName() + " and argument type " + parameterizedArgument.getName() + " are not compatible.");
    }

    private void registerAllProvidersFrom(MethodLocator locator, Object providerInstanceOrNull) {
        locator.methods().filter(m -> m.isAnnotationPresent(Provider.class)).forEach(m -> {
            Provider annotation = m.getAnnotation(Provider.class);
            register(m.getMethod(), annotation.optional() || m.isAnnotationPresent(Nullable.class), providerInstanceOrNull);
        });
    }

    private void register(Class<?> providerClass) {
        registerAllProvidersFrom(MethodLocator.forLocal(lookup, providerClass), null);
    }

    private void register(Object providerInstance) {
        registerAllProvidersFrom(MethodLocator.forLocal(lookup, providerInstance.getClass()), providerInstance);
    }

    /**
     * Creates a MethodHandle which calls the given one, and if it returns null, calls the existing registered handle
     * for the same type instead.
     *
     * @param provider Some handle that instantiates something
     * @return The wrapped handle
     */
    private MethodHandle makeOptional(MethodHandle provider) {
        MethodType type = provider.type();
        Class<?> returnType = type.returnType();
        if (returnType.isPrimitive()) {
            throw new IllegalArgumentException("Handle " + provider + " should create a full class.");
        }
        MethodHandle existing = findProviderOrGeneric(type);
        existing = MethodHandles.dropArguments(existing, 0, returnType); // First argument would be null anyway
        MethodHandle identity = MethodHandles.identity(returnType);
        identity = Methods.acceptThese(identity, existing.type().parameterArray());

        MethodHandle nullCheck = Methods.nullCheck(returnType);
        MethodHandle checkedExisting = MethodHandles.guardWithTest(nullCheck, existing, identity);
        checkedExisting = checkedExisting.asType(checkedExisting.type().changeParameterType(0, returnType));
        return MethodHandles.foldArguments(checkedExisting, provider);
    }

    private void checkReturnType(Class<?> type, Object provider) {
        if (type.isPrimitive()) {
            throw new AssertionError("Provider " + provider + " returns a primitive");
        }
    }

    private RequestedClass findRequestedClass(Method m) {
        int i=0;
        for (Parameter p : m.getParameters()) {
            if (p.isAnnotationPresent(Requested.class)) {
                Type t = p.getParameterizedType();
                if (!Class.class.equals(Types.erasureOf(t))) {
                    throw new AssertionError("Parameter #" + i + " of " + m + " is annotated with @Requested but of wrong type " + t);
                }
                Class<?> upperBound = t instanceof ParameterizedType ? Types.erasedArgument(t, Class.class, 0, Types.Bounded.UPPER) : Object.class;
                return new RequestedClass(i, upperBound);
            }
            i++;
        }
        return null;
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
        MethodHandle handle = findProviderHandle(methodType(type));
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
        return lambdafy(functionalInterface, lambdaMethod.getReturnType(), lambdaMethod.getParameterTypes());
    }

    /**
     * Creates a provider which implements the given functional interface and calls the appropriate provider method or constructor.
     * This method can be used when the given interface is generic, so that the signature doesn't specify the argument and return type enough.
     *
     * @param functionalInterface Describes the provider/constructor to use. Its method signature specifies parts of the parameter values.
     * @param returnType The expected return type
     * @param parameterTypes The expected parameter types; only the first ones that differ from the lambda method signature
     *                       must be given
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
        return lambdafy(functionalInterface, returnType, lambdaTypes);
    }

    private <T> T lambdafy(Class<T> functionalInterface, Class<?> returnType, Class<?>... parameterTypes) {
        MethodType methodType = methodType(returnType, parameterTypes);
        return providers.stream().map(p -> p.lambdafy(lookup, functionalInterface, methodType)).filter(Optional::isPresent)
                .map(Optional::get).findFirst().orElseGet(() -> {
                    MethodHandle h = withPostProcessor(findConstructor(methodType));
                    return Methods.lambdafy(lookup, h, functionalInterface);
                });
    }

    /**
     * Creates a handle that instantiates the given type.
     *
     * @param type Some class to instantiate
     * @param parameterTypes Some parameter types
     * @return A handle accepting the given parameter types, and returning the type
     */
    public MethodHandle findProviderHandle(Class<?> type, Class<?>... parameterTypes) {
        return findProviderHandle(methodType(type, parameterTypes));
    }

    /**
     * Creates a handle that instantiates the given type.
     *
     * @param methodType Some type which returns an instantiable class
     * @return A handle of the requested type, if the constructor or factory method was found
     */
    public MethodHandle findProviderHandle(MethodType methodType) {
        return withPostProcessor(findProviderOrGeneric(methodType));
    }

    private MethodHandle findProviderOrGeneric(MethodType methodType) {
        return providers.stream().map(p -> p.getProviderFor(lookup, methodType)).filter(Optional::isPresent).map(Optional::get)
            .findFirst().orElseGet(() -> findConstructor(methodType));
    }

    private MethodHandle findConstructor(MethodType methodType) {
        MethodHandle constructor;
        try {
            constructor = lookup.findConstructor(methodType.returnType(), methodType.changeReturnType(void.class));
        } catch (NoSuchMethodException ex) {
            throw new AssertionError("No constructor with parameters " + Arrays.toString(methodType.parameterArray()) + " in " + methodType.returnType().getName());
        } catch (IllegalAccessException ex) {
            throw new AssertionError("Constructor " + Arrays.toString(methodType.parameterArray()) + " in " + methodType.returnType().getName() + " not visible from my lookup");
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

        return MethodLocator.forLocal(lookup, type).methods().filter(m -> m.isAnnotationPresent(PostCreate.class)).
                map(m -> {
                    MethodHandle postProcessor = m.getHandle();
                    MethodType t = postProcessor.type();
                    if (t.parameterCount() != 1) {
                        throw new AssertionError(m + " is annotated with @" + PostCreate.class.getSimpleName() + " but has too many parameters.");
                    }
                    Class<?> returnType = t.returnType();
                    if (returnType == void.class) {
                        postProcessor = Methods.returnArgument(postProcessor, 0);
                    } else if (!type.isAssignableFrom(returnType)) {
                        if (m.getMethod().getDeclaringClass().isAssignableFrom(returnType)) {
                            // Then this is a @PostConstruct method from a superclass with a type not matching any more - ignore it
                            return null;
                        } else {
                            throw new AssertionError(m + " is annotated with @PostCreate but returns a type that does not match with " + type.getName());
                        }
                    }
                    return postProcessor;
                }).filter(Objects::nonNull).map(h -> h.asType(methodType(type, type))).reduce(fullConstructor, MethodHandles::filterReturnValue);
    }

    private static final class RequestedClass {
        final int parameterIndex;
        final Class<?> upperBound;

        RequestedClass(int parameterIndex, Class<?> upperBound) {
            this.parameterIndex = parameterIndex;
            this.upperBound = upperBound;
        }
    }
}
