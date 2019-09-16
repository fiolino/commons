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
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static java.lang.invoke.MethodHandles.*;
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

    private static final Instantiator DEFAULT;
    private static final Class<?>[] NO_SPECIAL_CLASSES = {};

    static {
        MethodHandle getTime, numberToString, dateToInstant, instantToDate;
        try {
            getTime = publicLookup().findVirtual(Date.class, "getTime", methodType(long.class));
            numberToString = publicLookup().findVirtual(Number.class, "toString", methodType(String.class));
            dateToInstant = publicLookup().findVirtual(Date.class, "toInstant", methodType(Instant.class));
            instantToDate = publicLookup().findStatic(Date.class, "from", methodType(Date.class, Instant.class));
        } catch (NoSuchMethodException | IllegalAccessException ex) {
            throw new AssertionError("getTime", ex);
        }

        DEFAULT = Instantiator.withProviders(lookup().dropLookupMode(MethodHandles.Lookup.MODULE),
                (MethodHandleProvider)(i, t) -> i.getLookup().findStatic(t.returnType(), "valueOf", t),

                (MethodHandleProvider)(i, t) -> {
                    if (t.parameterCount() == 1) {
                        Class<?> returnType = t.returnType();
                        if (returnType.isPrimitive()) {
                            Class<?> argumentType = t.parameterType(0);
                            String name = returnType.getName();
                            if (String.class.equals(argumentType)) {
                                // Finds something like Integer::parseInt
                                return i.getLookup().findStatic(Types.toWrapper(returnType), "parse" + Character.toUpperCase(name.charAt(0)) + name.substring(1), t);
                            } else if (Number.class.isAssignableFrom(argumentType)) {
                                // Finds something like Integer::longValue
                                return i.getLookup().findVirtual(argumentType, name + "Value", methodType(returnType));
                            }
                        }
                    }
                    return null;
                },

                (MethodHandleProvider)(i, t) -> {
                    if (String.class.equals(t.returnType()) && t.parameterCount() == 1) {
                        Class<?> argumentType = t.parameterType(0);
                        if (argumentType.isEnum()) {
                            return i.getLookup().findVirtual(argumentType, "name", methodType(String.class));
                        }
                    }
                    return null;
                },

                getTime,
                numberToString,
                dateToInstant,
                instantToDate,

                new Object() {
                    @Provider @SuppressWarnings("unused")
                    char charFromString(String value) {
                        if (value.isEmpty()) return (char) 0;
                        return value.charAt(0);
                    }

                    @Provider @SuppressWarnings("unused")
                    java.sql.Date sqlTypeFromDate(Date origin) {
                        return new java.sql.Date(origin.getTime());
                    }

                    @Provider @SuppressWarnings("unused")
                    java.sql.Time sqlTimeFromDate(Date origin) {
                        return new java.sql.Time(origin.getTime());
                    }

                    @Provider @SuppressWarnings("unused")
                    java.sql.Timestamp sqlTimestampFromDate(Date origin) {
                        return new java.sql.Timestamp(origin.getTime());
                    }

                    @Provider @SuppressWarnings("unused")
                    LocalDateTime localDateFor(Date date) {
                        return LocalDateTime.ofInstant(Instant.ofEpochMilli(date.getTime()), ZoneId.systemDefault());
                    }

                    @Provider @SuppressWarnings("unused")
                    Date dateFrom(LocalDateTime dateTime) {
                        return Date.from(dateTime.toInstant(ZoneOffset.UTC));
                    }
                });
    }

    /**
     * The default instantiator, which is an instance without any specific providers.
     */
    public static Instantiator withDefaults(MethodHandles.Lookup lookup) {
        return DEFAULT.withLookup(lookup);
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
        abstract boolean returnTypeMatches(Class<?> returnType);
        abstract boolean argumentsMatch(Class<?>[] argumentTypes);
        abstract MethodHandle getHandle(Instantiator callback, Class<?> returnType, Class<?>[] argumentTypes);

        <T> T lambdafy(Instantiator callback, Class<T> functionalInterface, Class<?> returnType, Class<?>[] argumentTypes) {
            return Methods.lambdafy(callback.getLookup(), getHandle(callback, returnType, argumentTypes), functionalInterface);
        }
    }

    private static class ContainingHandleProvider extends HandleProvider {
        final MethodHandle handle;
        final Class<?>[] acceptedClasses;

        ContainingHandleProvider(MethodHandle handle, Class<?>[] acceptedClasses) {
            this.handle = handle;
            this.acceptedClasses = acceptedClasses;
        }

        @Override
        boolean argumentsMatch(Class<?>[] argumentTypes) {
            Class<?>[] params = parametersToCheck();
            int n = params.length;
            if (n != argumentTypes.length) return false;
            for (int i = n-1; i >=0; i--) {
                if (!params[i].isAssignableFrom(argumentTypes[i])) return false;
            }
            return true;
        }

        Class<?>[] parametersToCheck() {
            return handle.type().parameterArray();
        }

        @Override
        boolean returnTypeMatches(Class<?> returnType) {
            if (acceptedClasses.length == 0) {
                return defaultReturnTypeMatches(returnType);
            }
            for (Class<?> c : acceptedClasses) {
                if (returnType.equals(c)) return true;
            }
            return false;
        }

        boolean defaultReturnTypeMatches(Class<?> returnType) {
            return returnType.equals(handle.type().returnType());
        }

        @Override
        MethodHandle getHandle(Instantiator callback, Class<?> returnType, Class<?>[] argumentTypes) {
            return handle;
        }
    }

    private static class HandleFromInstantiableProvider extends ContainingHandleProvider {
        // ()Object
        private final MethodHandle providerFactory;

        HandleFromInstantiableProvider(MethodHandle handle, Class<?>[] acceptedClasses, MethodHandle providerFactory) {
            super(handle, acceptedClasses);
            this.providerFactory = providerFactory;
        }

        @Override
        MethodHandle getHandle(Instantiator callback, Class<?> returnType, Class<?>[] argumentTypes) {
            return MethodHandles.foldArguments(handle, providerFactory);
        }

        @Override
        boolean argumentsMatch(Class<?>[] argumentTypes) {
            int additionalParameterCount = additionalParameterCount();
            MethodType myType = handle.type();
            int n = argumentTypes.length;
            if (myType.parameterCount() != n + additionalParameterCount) return false;
            for (int i = n; i >= additionalParameterCount; i--) {
                if (!myType.parameterType(i).equals(argumentTypes[i-additionalParameterCount])) return false;
            }
            return true;
        }

        int additionalParameterCount() {
            return 1;
        }

        @Override
        <T> T lambdafy(Instantiator callback, Class<T> functionalInterface, Class<?> returnType, Class<?>[] argumentTypes) {
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
            return lambdafyWithArguments(callback.getLookup(), functionalInterface, provider, returnType);
        }

        <T> T lambdafyWithArguments(MethodHandles.Lookup lookup, Class<T> functionalInterface, Object provider, Class<?> requestedType) {
            return Methods.lambdafy(lookup, handle, functionalInterface, provider);
        }

        private String providerName() {
            return providerFactory.type().returnType().getName();
        }
    }

    private static class HandleWithTypeInfoFromInstantiableProvider extends HandleFromInstantiableProvider {
        private final Class<?> upperBound;

        HandleWithTypeInfoFromInstantiableProvider(MethodHandle handle, Class<?>[] acceptedClasses, MethodHandle providerFactory, Class<?> upperBound) {
            super(handle, acceptedClasses, providerFactory);
            this.upperBound = upperBound;
        }

        @Override
        boolean defaultReturnTypeMatches(Class<?> returnType) {
            return upperBound.isAssignableFrom(returnType);
        }

        @Override
        int additionalParameterCount() {
            return 2;
        }

        @Override
        <T> T lambdafyWithArguments(MethodHandles.Lookup lookup, Class<T> functionalInterface, Object provider, Class<?> requestedType) {
            return Methods.lambdafy(lookup, handle, functionalInterface, provider, requestedType);
        }
    }

    private static class GenericHandleProvider extends ContainingHandleProvider {
        private final int argumentIndex;
        private final Class<?> subscribedClass;
        private final Class<?>[] expectedArguments;

        GenericHandleProvider(MethodHandle handle, Class<?>[] acceptedClasses, int argumentIndex, Class<?> subscribedClass) {
            super(handle, acceptedClasses);
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
        boolean returnTypeMatches(Class<?> returnType) {
            return subscribedClass.isAssignableFrom(returnType);
        }

        @Override
        Class<?>[] parametersToCheck() {
            return expectedArguments;
        }

        @Override
        <T> T lambdafy(Instantiator callback, Class<T> functionalInterface, Class<?> returnType, Class<?>[] argumentTypes) {
            if (argumentIndex == 0) {
                return Methods.lambdafy(callback.getLookup(), handle, functionalInterface, returnType);
            }
            return super.lambdafy(callback, functionalInterface, returnType, argumentTypes);
        }
    }

    private static class DynamicHandleProvider extends HandleProvider {
        private final MethodHandleProvider handleProvider;

        DynamicHandleProvider(MethodHandleProvider handleProvider) {
            this.handleProvider = handleProvider;
        }

        @Override
        boolean returnTypeMatches(Class<?> returnType) {
            return true;
        }

        @Override
        boolean argumentsMatch(Class<?>[] argumentTypes) {
            return true;
        }

        @Override
        MethodHandle getHandle(Instantiator callback, Class<?> returnType, Class<?>[] argumentTypes) {
            MethodType type = methodType(returnType, argumentTypes);
            MethodHandle h;
            try {
                h = handleProvider.createFor(callback, type);
            } catch (NoSuchMethodException | IllegalAccessException ex) {
                return null;
            }

            return h == null ? null : h.asType(type);
        }

        @Override
        <T> T lambdafy(Instantiator callback, Class<T> functionalInterface, Class<?> returnType, Class<?>[] argumentTypes) {
            MethodType type = methodType(returnType, argumentTypes);
            try {
                return handleProvider.lambdafy(callback, functionalInterface, type);
            } catch (NoSuchMethodException | IllegalAccessException ex) {
                return null;
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
     * Creates a new Instantiator with the same lookup and existing providers, plus the ones added here.
     *
     * @param provider A concrete {@link MethodHandleProvider}
     * @return The new Instantiator
     */
    public Instantiator addMethodHandleProvider(MethodHandleProvider provider) {
        return new Instantiator(lookup, new ArrayList<>(providers)).register(provider);
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
                register((Method) p, null);
            } else if (p instanceof MethodHandleProvider) {
                register((MethodHandleProvider) p);
            } else {
                register(p);
            }
        }
        return this;
    }

    private Instantiator register(MethodHandleProvider provider) {
        providers.add(0, new DynamicHandleProvider(provider));
        return this;
    }

    private void register(MethodHandle providerHandle) {
        HandleProvider handleProvider;
        try {
            MethodHandleInfo info = AccessController.doPrivileged((PrivilegedAction<MethodHandleInfo>) () -> lookup.revealDirect(providerHandle));
            try {
                Method m = info.reflectAs(Method.class, lookup);
                registerMethodWithHandle(providerHandle, m, null);
            } catch (ClassCastException ex2) {
                // Not a method?
                register(providerHandle, info.getDeclaringClass(), false, null, NO_SPECIAL_CLASSES, Modifier.isStatic(info.getModifiers()), null);
            }
        } catch (IllegalArgumentException ex) {
            // providerHandle seems not to be direct
            handleProvider = new ContainingHandleProvider(makeOptional(providerHandle), NO_SPECIAL_CLASSES);
            providers.add(0, handleProvider);
        }
    }

    private boolean isOptional(Method provider) {
        Provider annotation;
        return provider.isAnnotationPresent(Nullable.class) || ((annotation = provider.getAnnotation(Provider.class)) != null && annotation.optional());
    }

    private void register(Method provider, Object providerInstanceOrNull) {
        MethodHandle handle;
        try {
            handle = lookup.unreflect(provider);
        } catch (IllegalAccessException ex) {
            throw new IllegalArgumentException(provider + " is not accessible.", ex);
        }
        registerMethodWithHandle(handle, provider, providerInstanceOrNull);
    }

    private void registerMethodWithHandle(MethodHandle handle, Method method, Object providerInstanceOrNull) {
        RequestedClass requestedClass = findRequestedClass(method);
        Provider annotation = method.getAnnotation(Provider.class);
        Class<?>[] acceptedClasses = annotation == null ? NO_SPECIAL_CLASSES : annotation.value();
        boolean optional = method.isAnnotationPresent(Nullable.class) || annotation != null && annotation.optional();
        register(handle, method.getDeclaringClass(), optional, requestedClass, acceptedClasses, Modifier.isStatic(method.getModifiers()), providerInstanceOrNull);
    }

    private void register(MethodHandle providerHandle, Class<?> providerClass, boolean optional, RequestedClass requestedClass, Class<?>[] acceptedTypes, boolean isStatic, Object providerInstanceOrNull) {
        MethodHandle h = withPostProcessor(providerHandle);
        HandleProvider handleProvider;
        if (isStatic) {
            handleProvider = createStaticNondirectHandleProvider(h, optional, requestedClass, acceptedTypes);
        } else {
            if (optional) {
                handleProvider = createVirtualNondirectHandleProvider(providerClass, h, true, requestedClass, acceptedTypes);
            } else {
                if (requestedClass != null && requestedClass.parameterIndex > 1 || h != providerHandle) {
                    // Cannot be a direct lambda method
                    handleProvider = createVirtualNondirectHandleProvider(providerClass, h, false, requestedClass, acceptedTypes);
                } else {
                    // Might be direct
                    MethodHandle factory = providerInstanceOrNull == null ?
                            OneTimeExecution.createFor(findProviderHandle(providerClass)).getAccessor() : identity(providerClass).bindTo(providerInstanceOrNull);
                    handleProvider = requestedClass == null ? new HandleFromInstantiableProvider(providerHandle, acceptedTypes, factory)
                            : new HandleWithTypeInfoFromInstantiableProvider(providerHandle, acceptedTypes, factory, commonSubclassOf(providerHandle.type().returnType(), requestedClass.upperBound));
                }
            }
        }
        providers.add(0, handleProvider);
    }

    private HandleProvider createVirtualNondirectHandleProvider(Class<?> providerClass, MethodHandle handle, boolean optional, RequestedClass requestedClass, Class<?>[] acceptedTypes) {
        handle = handle.bindTo(instantiate(providerClass));
        return createStaticNondirectHandleProvider(handle, optional, requestedClass, acceptedTypes);
    }

    private HandleProvider createStaticNondirectHandleProvider(MethodHandle handle, boolean optional, RequestedClass requestedClass, Class<?>[] acceptedTypes) {
        if (optional) handle = makeOptional(handle);
        return requestedClass == null ? new ContainingHandleProvider(handle, acceptedTypes) :
                new GenericHandleProvider(handle, acceptedTypes, requestedClass.parameterIndex, commonSubclassOf(handle.type().returnType(), requestedClass.upperBound));
    }

    private Class<?> commonSubclassOf(Class<?> returnType, Class<?> parameterizedArgument) {
        if (returnType.isAssignableFrom(parameterizedArgument)) return parameterizedArgument;
        if (parameterizedArgument.isAssignableFrom(returnType)) return returnType;
        throw new AssertionError("Bad provider method: Return type " + returnType.getName() + " and argument type " + parameterizedArgument.getName() + " are not compatible.");
    }

    private void registerAllProvidersFrom(MethodLocator locator, Object providerInstanceOrNull) {
        locator.methods().filter(m -> m.isAnnotationPresent(Provider.class)).forEach(m -> {
            register(m.getMethod(), providerInstanceOrNull);
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
            // Cannot be nullable
            return provider;
        }
        MethodHandle existing = findProviderOrGeneric(returnType, type.parameterArray());
        existing = MethodHandles.dropArguments(existing, 0, returnType); // First argument would be null anyway
        MethodHandle identity = MethodHandles.identity(returnType);
        identity = Methods.acceptThese(identity, existing.type().parameterArray());

        MethodHandle nullCheck = Methods.nullCheck(returnType);
        MethodHandle checkedExisting = MethodHandles.guardWithTest(nullCheck, existing, identity);
        checkedExisting = checkedExisting.asType(checkedExisting.type().changeParameterType(0, returnType));
        return MethodHandles.foldArguments(checkedExisting, provider);
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
        MethodHandle handle = findProviderHandle(type);
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
        return filteredStream(returnType, parameterTypes).map(p -> p.lambdafy(this, functionalInterface, returnType, parameterTypes))
                .filter(Objects::nonNull)
                .findFirst().orElseGet(() -> {
                    MethodHandle h = withPostProcessor(findConstructor(returnType, parameterTypes));
                    return Methods.lambdafy(lookup, h, functionalInterface);
                });
    }

    /**
     * Creates a handle that instantiates the given type.
     *
     * @param returnType Some class to instantiate
     * @param parameterTypes Some parameter types
     * @return A handle accepting the given parameter types, and returning the type
     */
    public MethodHandle findProviderHandle(Class<?> returnType, Class<?>... parameterTypes) {
        return withPostProcessor(findProviderOrGeneric(returnType, parameterTypes));
    }

    /**
     * Creates a handle that instantiates the given type.
     *
     * @param methodType Some type which returns an instantiable class
     * @return A handle of the requested type, if the constructor or factory method was found
     */
    public MethodHandle findProviderHandle(MethodType methodType) {
        return findProviderHandle(methodType.returnType(), methodType.parameterArray());
    }

    private MethodHandle findProviderOrGeneric(Class<?> returnType, Class<?>... parameterTypes) {
        return filteredStream(returnType, parameterTypes)
            .map(p -> p.getHandle(this, returnType, parameterTypes)).filter(Objects::nonNull)
            .findFirst().orElseGet(() -> findConstructor(returnType, parameterTypes));
    }

    private Stream<HandleProvider> filteredStream(Class<?> returnType, Class<?>[] parameterTypes) {
        return providers.stream().filter(p -> p.returnTypeMatches(returnType)).filter(p -> p.argumentsMatch(parameterTypes));
    }

    private MethodHandle findConstructor(Class<?> returnType, Class<?>... parameterTypes) {
        MethodHandle constructor;
        try {
            constructor = lookup.findConstructor(returnType, methodType(void.class, parameterTypes));
        } catch (NoSuchMethodException ex) {
            throw new AssertionError("No constructor with parameters " + Arrays.toString(parameterTypes) + " in " + returnType.getName());
        } catch (IllegalAccessException ex) {
            throw new AssertionError("Constructor " + Arrays.toString(parameterTypes) + " in " + returnType.getName() + " not visible from my lookup");
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
