package org.fiolino.common.ioc;

import org.fiolino.annotations.PostCreate;
import org.fiolino.annotations.PostProcessor;
import org.fiolino.annotations.Provider;
import org.fiolino.annotations.Requested;
import org.fiolino.common.reflection.*;
import org.fiolino.common.util.Types;

import javax.annotation.Nullable;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.*;
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
public abstract class FactoryFinder {

    private FactoryFinder() {}

    /**
     * Returns an empty instance. This wil find no factory handle at all, but always return an empty Optional
     * or throw an exception, depending on the used method.
     *
     * It's rather useless top directly use this instance as a finder. You can start with this and register
     * additional providers.
     */
    public static FactoryFinder empty() {
        return EMPTY;
    }

    /**
     * Returns an instance that always tried to instantiate the return type using its constructor.
     */
    public static FactoryFinder instantiator() {
        return USE_CONSTRUCTOR;
    }

    /**
     * Creates a handle that converts to the given type.
     *
     * @param returnType Some class to return
     * @param parameterTypes Some parameter types
     * @return A handle accepting the given parameter types, and returning the type
     */
    public final Optional<MethodHandle> find(Class<?> returnType, Class<?>... parameterTypes) {
        return find(lookup(), returnType, parameterTypes);
    }

    /**
     * Creates a handle that converts to the given type.
     *
     * @param lookup Use this to find method handles. Has no effect on visibility of provider methods.
     * @param returnType Some class to return
     * @param parameterTypes Some parameter types
     * @return A handle accepting the given parameter types, and returning the type
     */
    public abstract Optional<MethodHandle> find(Lookup lookup, Class<?> returnType, Class<?>... parameterTypes);

    /**
     * Creates a handle that converts to the given type.
     *
     * @param returnType Some class to return
     * @param parameterTypes Some parameter types
     * @return A handle accepting the given parameter types, and returning the type
     * @throws NoSuchProviderException If there is no provider found
     */
    public final MethodHandle findOrFail(Class<?> returnType, Class<?>... parameterTypes) {
        return failIfEmpty(find(returnType, parameterTypes), returnType, parameterTypes);
    }

    /**
     * Creates a handle that converts to the given type.
     *
     * @param lookup Use this to find method handles. Has no effect on visibility of provider methods.
     * @param returnType Some class to return
     * @param parameterTypes Some parameter types
     * @return A handle accepting the given parameter types, and returning the type
     * @throws NoSuchProviderException If there is no provider found
     */
    public final MethodHandle findOrFail(Lookup lookup, Class<?> returnType, Class<?>... parameterTypes) {
        return failIfEmpty(find(lookup, returnType, parameterTypes), returnType, parameterTypes);
    }

    /**
     * Creates a handle that instantiates the given type.
     *
     * @param methodType Some type which returns an instantiable class
     * @return A handle of the requested type, if the constructor or factory method was found
     */
    public final Optional<MethodHandle> find(MethodType methodType) {
        return find(methodType.returnType(), methodType.parameterArray());
    }

    /**
     * Returns an instance that checks the given provider before iterating over my already existing setup.
     *
     * @param provider Provides a {@link MethodHandle} for a given type
     * @return The new {@link FactoryFinder} with that extended setup
     */
    public FactoryFinder withMethodHandleProvider(MethodHandleProvider provider) {
        return new DynamicHandleFinder(this, provider);
    }

    /**
     * Returns an instance that checks the given method handle if it matches the type, and then continues with my already existing setup.
     * Adds initializers as the first arguments.
     *
     * @param provider Will be called if the given type is compatible
     * @param initializers These will be bound to the handle as its first parameters
     * @return The new {@link FactoryFinder} with that extended setup
     */
    public FactoryFinder withMethodHandle(MethodHandle provider, Object... initializers) {
        if (initializers.length == 0) {
            return new FixedHandle(this, provider);
        }
        if (initializers.length > provider.type().parameterCount()) {
            throw new IllegalArgumentException("Too many initializers: " + Arrays.toString(initializers) + " for " + provider.type());
        }
        return new WithHandleAndInitializers(this, provider, initializers);
    }

    /**
     * Returns an instance that checks the given provider before iterating over my already existing setup.
     *
     * @param method This method will be called if the type matches with the requested one.
     *               If this is an instance method, an instance of the declaring class will be injected
     * @return The new {@link FactoryFinder} with that extended setup
     */
    public FactoryFinder withMethod(Method method) {
        return withMethod(lookup(), method);
    }

    /**
     * Returns an instance that checks the given provider before iterating over my already existing setup.
     *
     * @param lookup Used to unreflect the given method. This does not change the Lookup to find factory handles
     *               in the returned instance
     * @param method This method will be called if the type matches with the requested one.
     *               If this is an instance method, an instance of the declaring class will be injected
     * @return The new {@link FactoryFinder} with that extended setup
     */
    public FactoryFinder withMethod(Lookup lookup, Method method) {
        return registerProviderMethod(lookup, method, null);
    }

    /**
     * Returns an instance that checks the given providers before iterating over my already existing setup.
     *
     * @param providerContainer All methods annotated with @Provider will be used in the returned instance
     * @return The new {@link FactoryFinder} with that extended setup
     */
    public FactoryFinder withProvidersFrom(Object providerContainer) {
        return withProvidersFrom(lookup(), providerContainer);
    }

    /**
     * Returns an instance that checks the given providers before iterating over my already existing setup.
     *
     * @param lookup Use this to identify the methods - annotated methods should be visible for this lookup.
     *               This does not change the Lookup to find factory handles in the returned instance
     * @param providerContainer All methods annotated with @Provider will be used in the returned instance
     * @return The new {@link FactoryFinder} with that extended setup
     */
    public FactoryFinder withProvidersFrom(Lookup lookup, Object providerContainer) {
        if (providerContainer instanceof Class) {
            return withProvidersFrom(lookup, (Class<?>) providerContainer);
        } else {
            return registerAllProvidersFrom(MethodLocator.forLocal(lookup, providerContainer.getClass()), providerContainer);
        }
    }

    /**
     * Returns an instance that checks the given providers before iterating over my already existing setup.
     *
     * @param providerContainer All methods annotated with @Provider will be used in the returned instance.
     *                          If the methods are not static, the class must be instantiable with an empty constructor
     * @return The new {@link FactoryFinder} with that extended setup
     */
    public FactoryFinder withProvidersFrom(Class<?> providerContainer) {
        return withProvidersFrom(lookup(), providerContainer);
    }

    /**
     * Returns an instance that checks the given providers before iterating over my already existing setup.
     *
     * @param lookup Use this to identify the methods - annotated methods should be visible for this lookup.
     *               This does not change the Lookup to find factory handles in the returned instance
     * @param providerContainer All methods annotated with @Provider will be used in the returned instance.
     *                          If the methods are not static, the class must be instantiable with an empty constructor
     * @return The new {@link FactoryFinder} with that extended setup
     */
    public FactoryFinder withProvidersFrom(Lookup lookup, Class<?> providerContainer) {
        return registerAllProvidersFrom(MethodLocator.forLocal(lookup, providerContainer), null);
    }

    /**
     * Returns a FactoryFinder that uses the given lookup as the default. It will be used for finding constructors and
     * for {@link MethodHandleProvider}s in general.
     *
     * Subsequent additions of Methods and provider classes will use this lookup as the default to unreflect to
     * MethodHandles.
     *
     * This only sets the default lookup. When a lookup is specified as a parameter, then that one will be used.
     *
     * Multiple calls will overwrite previous settings; only the last specified lookup is used to find the
     * factory handle.
     *
     * @param lookup This is used as the default to find factory handles and provider methods, if no explicit
     *               lookup is used as a parameter
     * @return An instance using this lookup
     */
    public FactoryFinder using(Lookup lookup) {
        return new LookupHolder(this, lookup);
    }

    /**
     * Tries to find a suitable transformer handle, and executes it with the given arguments.
     * Can be used to directly instantiate a type, or to convert some existing value to its target type.
     *
     * @param expectedType The target type
     * @param parameters The input values. These must not be null, because they're used to identify the transformer
     * @param <R> The target type
     * @return The converted value
     * @throws NoSuchProviderException If there is no suitable transformer
     */
    public <R> R transform(Class<R> expectedType, Object... parameters) {
        Class<R> resultType = Types.toWrapper(expectedType);
        int n = parameters.length;
        if (n == 1 && resultType.isInstance(parameters[0])) {
            return resultType.cast(parameters[0]);
        }
        Class<?>[] parameterTypes = new Class<?>[n];
        for (int i=n-1; i >= 0; i--) {
            parameterTypes[i] = parameters[i].getClass();
        }
        return failIfEmpty(find(resultType, parameterTypes).map(h -> {
            try {
                return resultType.cast(h.invokeWithArguments(parameters));
            } catch (RuntimeException | Error e) {
                throw e;
            } catch (Throwable t) {
                throw new UndeclaredThrowableException(t, "Transforming using " + h);
            }
        }), resultType, parameterTypes);
    }

    /**
     * Creates a handle with a converter that converts the return type to the new one.
     *
     * @param target     This will be called
     * @param returnType The new return type
     * @return A {@link MethodHandle} of the same type as target except that the return type
     * is of the respected parameter
     */
    public MethodHandle convertReturnTypeTo(MethodHandle target, Class<?> returnType) {
        MethodType type = target.type();
        Class<?> r = type.returnType();
        if (typesAreConvertible(returnType, r)) {
            return MethodHandles.explicitCastArguments(target, type.changeReturnType(returnType));
        }
        if (returnType == void.class) {
            return target.asType(type.changeReturnType(void.class));
        }
        return find(returnType, r)
                .map(h -> h.asType(methodType(returnType, r)))
                .map(h -> MethodHandles.filterReturnValue(target, h))
                .orElseGet(() -> MethodHandles.explicitCastArguments(target, type.changeReturnType(returnType)));
    }

    /**
     * Returns a {@link MethodHandle} that executes the given target, but first converts all parameters
     * starting from argumentNumber with the ones given in the inputTypes.
     * <p>
     * The returned MethodHandle will have the same type as target except that it accepts the given
     * inputTypes as parameters starting from argumentNumber as a replacement for the original ones.
     *
     * @param target         The target handle
     * @param argumentNumber Start converting from here
     * @param inputTypes     Accept these types (must be convertable to the original types)
     * @return The handle which accepts different parameters
     */
    public MethodHandle convertArgumentTypesTo(MethodHandle target, int argumentNumber, Class<?>... inputTypes) {
        int n = inputTypes.length;
        if (target.type().parameterCount() < argumentNumber + n) {
            throw new IllegalArgumentException(target + " does not accept " + (argumentNumber + n) + " parameters.");
        }
        return convertArgumentTypesTo(target, argumentNumber, n, inputTypes);
    }

    private MethodHandle convertArgumentTypesTo(MethodHandle target, int argumentNumber, int numberOfConversions, Class<?>[] inputTypes) {
        if (numberOfConversions == 0) {
            return target;
        }
        MethodType type = target.type();
        MethodHandle[] filters = new MethodHandle[numberOfConversions];
        MethodType casted = null;
        for (int i = 0; i < numberOfConversions; i++) {
            Class<?> arg;
            Class<?> newType = inputTypes[i];
            if (newType == null || newType.equals(arg = type.parameterType(argumentNumber + i))) {
                continue;
            }
            if (typesAreConvertible(arg, newType)) {
                if (casted == null) casted = type;
                casted = casted.changeParameterType(argumentNumber + i, newType);
                continue;
            }
            MethodHandle converter = findOrFail(arg, newType);
            converter = converter.asType(methodType(arg, newType));
            filters[i] = converter;
        }
        MethodHandle castedTarget = casted == null ? target : MethodHandles.explicitCastArguments(target, casted);
        return MethodHandles.filterArguments(castedTarget, argumentNumber, filters);
    }

    /**
     * Converts the arguments and return type of the given target handle so that it will match the given type.
     * <p>
     * The resulting handle will be of the given type. All arguments and the return value are being converted
     * via the given {@link ConverterLocator}.
     * <p>
     * The number of arguments of the given handle and the expected type don't need to match.
     * If the expected type has more arguments than the given target handle, the redundant parameters will just
     * be dropped.
     * If the expected type has less arguments, then the missing parameters will be filled with constant
     * values specified in the additionalValues parameter.
     *
     * @param target           This will be executed
     * @param type             This is the new type of the returned handle
     * @param additionalValues These are only used if the new type expects less arguments than the target handle.
     *                         For every missing parameter, one value of these is converted, if necessary,
     *                         and then added in the constant pool of the resulting handle.
     * @return A handle of the same type as specified
     * @throws NoMatchingConverterException      If one of the arguments or return type can't be converted
     * @throws TooManyArgumentsExpectedException If the target handle expects more arguments than the new type,
     *                                           but there are not enough additional values given as constant replacements
     */
    public MethodHandle convertTo(MethodHandle target, MethodType type, Object... additionalValues) {

        MethodHandle casted = convertReturnTypeTo(target, type.returnType());
        MethodType t = target.type();
        int expectedParameterSize = type.parameterCount();
        int actualParameterSize = t.parameterCount();
        int argSize = Math.min(actualParameterSize, expectedParameterSize);
        MethodHandle result = convertArgumentTypesTo(casted, 0, argSize, type.parameterArray());
        int reduceSize = actualParameterSize - expectedParameterSize;
        if (reduceSize > 0) {
            // Original target expects more parameters
            if (additionalValues.length < reduceSize) {
                throw new TooManyArgumentsExpectedException("Expects at least " + reduceSize + " additional values to map " + t
                        + " to" + type + ", but only " + additionalValues.length + " are given.");
            }
            Object[] add = new Object[reduceSize];
            for (int i = 0; i < reduceSize; i++) {
                Object a = additionalValues[i];
                Class<?> expectedType = t.parameterType(i + argSize);
                if (a == null) {
                    if (expectedType.isPrimitive()) {
                        a = expectedType == boolean.class ? false : 0;
                    }
                } else {
                    a = transform(expectedType, a);
                }
                add[i] = a;
            }
            return MethodHandles.insertArguments(result, argSize, add);
        } else if (reduceSize < 0) {
            // New type expects more arguments, ignore them
            reduceSize *= -1;
            Class<?>[] dispensableTypes = new Class<?>[reduceSize];
            System.arraycopy(type.parameterArray(), actualParameterSize, dispensableTypes, 0, reduceSize);
            return MethodHandles.dropArguments(result, actualParameterSize, dispensableTypes);
        }

        return result;
    }

    /**
     * Creates a provider which implements the given functional interface an calls the appropriate provider method or constructor.
     *
     * @param functionalInterface Describes the provider/constructor to use. Its method signature specifies the parameter values and return type.
     * @param <T>                 The interface type
     * @return A lambda or proxy
     * @throws NoSuchProviderException If there is no provider that converts from the sources to the result type
     */
    public <T> T lambdafy(Class<T> functionalInterface) {
        return lambdafy(lookup(), functionalInterface);
    }

    /**
     * Creates a provider which implements the given functional interface an calls the appropriate provider method or constructor.
     *
     * @param lookup Use this to find the factory handle and create a lambda out of it.
     *               Should be the caller's lookup.
     * @param functionalInterface Describes the provider/constructor to use. Its method signature specifies the parameter values and return type.
     * @param <T>                 The interface type
     * @return A lambda or proxy
     * @throws NoSuchProviderException If there is no provider that converts from the sources to the result type
     */
    public <T> T lambdafy(Lookup lookup, Class<T> functionalInterface) {
        Method lambdaMethod = Methods.findLambdaMethodOrFail(functionalInterface);
        return lambdafyDirect(lookup, functionalInterface, lambdaMethod.getReturnType(), lambdaMethod.getParameterTypes());
    }

    /**
     * Creates a provider which implements the given functional interface and calls the appropriate provider method or constructor.
     * This method can be used when the given interface is generic, so that the signature doesn't specify the argument and return type enough.
     *
     * @param functionalInterface Describes the provider/constructor to use. Its method signature specifies parts of the parameter values.
     * @param returnType          The expected return type
     * @param parameterTypes      The expected parameter types; only the first ones that differ from the lambda method signature
     *                            must be given
     * @param <T>                 The interface type
     * @return A lambda or proxy
     * @throws NoSuchProviderException If there is no provider that converts from the sources to the result type
     */
    public <T> T lambdafy(Class<T> functionalInterface, Class<?> returnType, Class<?>... parameterTypes) {
        return lambdafy(lookup(), functionalInterface, returnType, parameterTypes);
    }

    /**
     * Creates a provider which implements the given functional interface and calls the appropriate provider method or constructor.
     * This method can be used when the given interface is generic, so that the signature doesn't specify the argument and return type enough.
     *
     * @param lookup Use this to find the factory handle and create a lambda out of it.
     *               Should be the caller's lookup.
     * @param functionalInterface Describes the provider/constructor to use. Its method signature specifies parts of the parameter values.
     * @param returnType          The expected return type
     * @param parameterTypes      The expected parameter types; only the first ones that differ from the lambda method signature
     *                            must be given
     * @param <T>                 The interface type
     * @return A lambda or proxy
     * @throws NoSuchProviderException If there is no provider that converts from the sources to the result type
     */
    public <T> T lambdafy(Lookup lookup, Class<T> functionalInterface, Class<?> returnType, Class<?>... parameterTypes) {
        Method lambdaMethod = Methods.findLambdaMethodOrFail(functionalInterface);

        Class<?>[] lambdaTypes = lambdaMethod.getParameterTypes();
        if (lambdaTypes.length < parameterTypes.length) {
            throw new IllegalArgumentException("Given too many parameter types: Expected " +
                    Arrays.toString(lambdaTypes) + ", given " + Arrays.toString(parameterTypes));
        }
        System.arraycopy(parameterTypes, 0, lambdaTypes, 0, parameterTypes.length);
        return lambdafyDirect(lookup, functionalInterface, returnType, lambdaTypes);
    }

    abstract Lookup lookup();

    <T> T lambdafyDirect(Lookup lookup, Class<T> functionalInterface, Class<?> returnType, Class<?>[] argumentTypes) {
        return lambdafyDirect(lookup, this, functionalInterface, returnType, argumentTypes);
    }
    abstract <T> T lambdafyDirect(Lookup lookup, FactoryFinder callback, Class<T> functionalInterface, Class<?> returnType, Class<?>[] argumentTypes);

    private static final FactoryFinder EMPTY = new FactoryFinder() {
        @Override
        public Optional<MethodHandle> find(Lookup lookup, Class<?> returnType, Class<?>... parameterTypes) {
            return Optional.empty();
        }

        @Override
        Lookup lookup() {
            return publicLookup();
        }

        @Override
        <T> T lambdafyDirect(Lookup lookup, FactoryFinder callback, Class<T> functionalInterface, Class<?> returnType, Class<?>[] argumentTypes) {
            throw new NoSuchProviderException(returnType, argumentTypes, Arrays.stream(argumentTypes).map(Class::getName).reduce(
                    new StringJoiner(",", "No handle for (", ")" + returnType.getName()), StringJoiner::add, StringJoiner::merge
            ).toString());
        }
    };

    private static final FactoryFinder USE_CONSTRUCTOR = EMPTY.withMethodHandleProvider(
            (l, ff, t) -> l.findConstructor(t.returnType(), t.changeReturnType(void.class)));

    private static final Class<?>[] NO_SPECIAL_CLASSES = {};

    private static abstract class Wrapper extends FactoryFinder {
        final FactoryFinder wrapped;

        Wrapper(FactoryFinder wrapped) {
            this.wrapped = wrapped;
        }

        @Override
        Lookup lookup() {
            return wrapped.lookup();
        }

        @Override
        public Optional<MethodHandle> find(Lookup lookup, Class<?> returnType, Class<?>... parameterTypes) {
            return wrapped.find(lookup, returnType, parameterTypes);
        }

        @Override
        final <T> T lambdafyDirect(Lookup lookup, Class<T> functionalInterface, Class<?> returnType, Class<?>[] argumentTypes) {
            return lambdafyDirect(lookup, wrapped, functionalInterface, returnType, argumentTypes);
        }

        <T> T lambdafyDirect(Lookup lookup, FactoryFinder callback, Class<T> functionalInterface, Class<?> returnType, Class<?>[] argumentTypes) {
            return wrapped.lambdafyDirect(lookup, callback, functionalInterface, returnType, argumentTypes);
        }
    }

    private static class LookupHolder extends Wrapper {
        private final Lookup lookup;

        LookupHolder(FactoryFinder wrapped, Lookup lookup) {
            super(wrapped);
            this.lookup = lookup;
        }

        @Override
        Lookup lookup() {
            return lookup;
        }

        @Override
        public FactoryFinder using(Lookup lookup) {
            return new LookupHolder(wrapped, lookup);
        }
    }

    private static abstract class ConditionalWrapper extends Wrapper {
        ConditionalWrapper(FactoryFinder wrapped) {
            super(wrapped);
        }

        @Override
        public Optional<MethodHandle> find(Lookup lookup, Class<?> returnType, Class<?>... argumentTypes) {
            if (returnTypeMatches(returnType) && argumentsMatch(argumentTypes)) {
                MethodHandle h = getHandle(lookup, wrapped, returnType, argumentTypes);
                if (h != null) return Optional.of(h);
            }
            return wrapped.find(lookup, returnType, argumentTypes);
        }

        abstract boolean returnTypeMatches(Class<?> returnType);
        abstract boolean argumentsMatch(Class<?>[] argumentTypes);
        @Nullable abstract MethodHandle getHandle(Lookup lookup, FactoryFinder callback, Class<?> returnType, Class<?>[] argumentTypes);

        @Override
        <T> T lambdafyDirect(Lookup lookup, FactoryFinder callback, Class<T> functionalInterface, Class<?> returnType, Class<?>[] argumentTypes) {
            return Methods.lambdafy(lookup, getHandle(lookup, callback, returnType, argumentTypes), functionalInterface);
        }
    }

    private static class FixedHandle extends ConditionalWrapper {
        final MethodHandle handle;

        FixedHandle(FactoryFinder wrapped, MethodHandle handle) {
            super(wrapped);
            this.handle = handle;
        }

        @Override
        boolean argumentsMatch(Class<?>[] argumentTypes) {
            return argumentsMatch(argumentTypes, 0);
        }

        final boolean argumentsMatch(Class<?>[] argumentTypes, int additionalParameterCount) {
            Class<?>[] params = parametersToCheck();
            int n = params.length;
            if (argumentTypes.length + n != additionalParameterCount) return false;
            for (int i = n; i >= additionalParameterCount; i--) {
                if (!Types.isAssignableFrom(params[i], argumentTypes[i-additionalParameterCount])) return false;
            }
            return true;
        }

        Class<?>[] parametersToCheck() {
            return handle.type().parameterArray();
        }

        @Override
        boolean returnTypeMatches(Class<?> returnType) {
            return returnType.equals(handle.type().returnType());
        }

        @Override
        MethodHandle getHandle(Lookup lookup, FactoryFinder callback, Class<?> returnType, Class<?>[] argumentTypes) {
            return handle;
        }
    }

    private static class FixedHandleForSpecificTypes extends FixedHandle {
        private final Class<?>[] acceptedClasses;

        FixedHandleForSpecificTypes(FactoryFinder wrapped, MethodHandle handle, Class<?>[] acceptedClasses) {
            super(wrapped, handle);
            this.acceptedClasses = acceptedClasses;
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
            return super.returnTypeMatches(returnType);
        }

        @Override
        MethodHandle getHandle(Lookup lookup, FactoryFinder callback, Class<?> returnType, Class<?>[] argumentTypes) {
            return handle;
        }
    }

    private static class WithHandleAndInitializers extends FixedHandle {
        private final Object[] initializers;

        WithHandleAndInitializers(FactoryFinder wrapped, MethodHandle handle, Object[] initializers) {
            super(wrapped, handle);
            this.initializers = initializers;
        }

        @Override
        boolean argumentsMatch(Class<?>[] argumentTypes) {
            return argumentsMatch(argumentTypes, initializers.length);
        }

        @Override
        MethodHandle getHandle(Lookup lookup, FactoryFinder callback, Class<?> returnType, Class<?>[] argumentTypes) {
            return insertArguments(handle, 0, initializers);
        }

        @Override
        <T> T lambdafyDirect(Lookup lookup, FactoryFinder callback, Class<T> functionalInterface, Class<?> returnType, Class<?>[] argumentTypes) {
            return Methods.lambdafy(lookup, handle, functionalInterface, initializers);
        }
    }

    private static class FixedHandleWithFactory extends FixedHandleForSpecificTypes {
        // ()<ProviderType>
        private final MethodHandle providerFactory;

        FixedHandleWithFactory(FactoryFinder wrapped, MethodHandle handle, Class<?>[] acceptedClasses, MethodHandle providerFactory) {
            super(wrapped, handle, acceptedClasses);
            this.providerFactory = providerFactory;
        }

        @Override
        MethodHandle getHandle(Lookup lookup, FactoryFinder callback, Class<?> returnType, Class<?>[] argumentTypes) {
            return MethodHandles.foldArguments(handle, providerFactory);
        }

        @Override
        boolean argumentsMatch(Class<?>[] argumentTypes) {
            return argumentsMatch(argumentTypes, 1);
        }

        @Override
        <T> T lambdafyDirect(Lookup lookup, FactoryFinder callback, Class<T> functionalInterface, Class<?> returnType, Class<?>[] argumentTypes) {
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
            return lambdafyWithArguments(lookup, functionalInterface, provider, returnType);
        }

        <T> T lambdafyWithArguments(MethodHandles.Lookup lookup, Class<T> functionalInterface, Object provider, Class<?> requestedType) {
            return Methods.lambdafy(lookup, handle, functionalInterface, provider);
        }

        private String providerName() {
            return providerFactory.type().returnType().getName();
        }
    }

    private static class FixedHandleWithFactoryAndRequestedType extends FixedHandleWithFactory {
        private final Class<?> upperBound;

        FixedHandleWithFactoryAndRequestedType(FactoryFinder wrapped, MethodHandle handle, Class<?>[] acceptedClasses, MethodHandle providerFactory, Class<?> upperBound) {
            super(wrapped, handle, acceptedClasses, providerFactory);
            this.upperBound = upperBound;
        }

        @Override
        boolean defaultReturnTypeMatches(Class<?> returnType) {
            return upperBound.isAssignableFrom(returnType);
        }

        @Override
        boolean argumentsMatch(Class<?>[] argumentTypes) {
            return argumentsMatch(argumentTypes, 2);
        }

        @Override
        <T> T lambdafyWithArguments(MethodHandles.Lookup lookup, Class<T> functionalInterface, Object provider, Class<?> requestedType) {
            return Methods.lambdafy(lookup, handle, functionalInterface, provider, requestedType);
        }
    }

    private static class GenericHandleFinder extends FixedHandleForSpecificTypes {
        private final int argumentIndex;
        private final Class<?> subscribedClass;
        private final Class<?>[] expectedArguments;

        GenericHandleFinder(FactoryFinder wrapped, MethodHandle handle, Class<?>[] acceptedClasses, int argumentIndex, Class<?> subscribedClass) {
            super(wrapped, handle, acceptedClasses);
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
        <T> T lambdafyDirect(Lookup lookup, FactoryFinder callback, Class<T> functionalInterface, Class<?> returnType, Class<?>[] argumentTypes) {
            if (argumentIndex == 0) {
                return Methods.lambdafy(lookup, handle, functionalInterface, returnType);
            }
            return super.lambdafyDirect(lookup, callback, functionalInterface, returnType, argumentTypes);
        }
    }

    private static class DynamicHandleFinder extends ConditionalWrapper {
        private final MethodHandleProvider handleProvider;

        DynamicHandleFinder(FactoryFinder wrapped, MethodHandleProvider handleProvider) {
            super(wrapped);
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
        MethodHandle getHandle(Lookup lookup, FactoryFinder callback, Class<?> returnType, Class<?>[] argumentTypes) {
            MethodType type = methodType(returnType, argumentTypes);
            MethodHandle h;
            try {
                h = handleProvider.createFor(lookup, callback, type);
            } catch (NoSuchMethodException | IllegalAccessException ex) {
                return null;
            }

            return h == null ? null : h.asType(type);
        }

        @Override
        <T> T lambdafyDirect(Lookup lookup, FactoryFinder callback, Class<T> functionalInterface, Class<?> returnType, Class<?>[] argumentTypes) {
            MethodType type = methodType(returnType, argumentTypes);
            try {
                return handleProvider.lambdafy(lookup, callback, functionalInterface, type);
            } catch (NoSuchMethodException | IllegalAccessException ex) {
                return null;
            }
        }
    }

    private MethodHandle unreflect(Lookup lookup, Method method) {
        MethodHandle handle;
        try {
            handle = lookup.unreflect(method);
        } catch (IllegalAccessException ex) {
            throw new IllegalArgumentException(method + " is not accessible.", ex);
        }
        return handle;
    }

    private MethodHandle bindTo(MethodHandle h, Class<?> boundType, Object probablyExisting) {
        if (probablyExisting != null) {
            return h.bindTo(probablyExisting);
        }
        MethodHandle factory = OneTimeExecution.createFor(findOrFail(boundType)).getAccessor();
        return foldArguments(h, factory);
    }

    private FactoryFinder staticNondirect(MethodHandle handle, boolean optional, RequestedClass requestedClass, Class<?>[] acceptedTypes) {
        if (optional) handle = makeOptional(handle);
        return requestedClass == null ? new FixedHandleForSpecificTypes(this, handle, acceptedTypes) :
                new GenericHandleFinder(this, handle, acceptedTypes, requestedClass.parameterIndex, commonSubclassOf(handle.type().returnType(), requestedClass.upperBound));
    }

    private Class<?> commonSubclassOf(Class<?> returnType, Class<?> parameterizedArgument) {
        if (returnType.isAssignableFrom(parameterizedArgument)) return parameterizedArgument;
        if (parameterizedArgument.isAssignableFrom(returnType)) return returnType;
        throw new AssertionError("Bad provider method: Return type " + returnType.getName() + " and argument type " + parameterizedArgument.getName() + " are not compatible.");
    }

    private FactoryFinder registerAllProvidersFrom(MethodLocator locator, Object providerInstanceOrNull) {
        return locator.methods().filter(m -> m.isAnnotationPresent(Provider.class))
                .reduce(this, (ff, m) -> registerProviderMethod(locator.lookup(), m.getMethod(), providerInstanceOrNull), (ff1, ff2) -> {
            throw new UnsupportedOperationException();
        });
    }

    private FactoryFinder registerProviderMethod(Lookup lookup, Method method, Object providerInstanceOrNull) {
        MethodHandle handle = unreflect(lookup, method);

        RequestedClass requestedClass = findRequestedClass(method);
        Provider annotation = method.getAnnotation(Provider.class);
        Class<?>[] acceptedTypes = annotation == null ? NO_SPECIAL_CLASSES : annotation.value();
        boolean optional = method.isAnnotationPresent(Nullable.class) || annotation != null && annotation.optional();

        MethodHandle h = withPostProcessor(lookup, handle);
        if (Modifier.isStatic(method.getModifiers())) {
            return staticNondirect(h, optional, requestedClass, acceptedTypes);
        } else {
            if (optional || requestedClass != null && requestedClass.parameterIndex > 1 || h != handle) {
                // Cannot be a direct lambda method
                return staticNondirect(bindTo(h, method.getDeclaringClass(), providerInstanceOrNull), optional, requestedClass, acceptedTypes);
            } else {
                // Might be direct
                MethodHandle factory = providerInstanceOrNull == null ?
                        OneTimeExecution.createFor(findOrFail(method.getDeclaringClass())).getAccessor() : identity(method.getDeclaringClass()).bindTo(providerInstanceOrNull);
                return requestedClass == null ? new FixedHandleWithFactory(this, h, acceptedTypes, factory)
                        : new FixedHandleWithFactoryAndRequestedType(this, h, acceptedTypes, factory, commonSubclassOf(handle.type().returnType(), requestedClass.upperBound));
            }
        }
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
        MethodHandle existing = find(returnType, type.parameterArray()).orElseThrow(() -> new NoSuchProviderException(
                returnType, type.parameterArray(), "No default provider available for optional " + provider));
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

    private MethodHandle withPostProcessor(Lookup lookup, MethodHandle handle) {
        MethodHandle fullConstructor;
        Class<?> type = handle.type().returnType();
        if (PostProcessor.class.isAssignableFrom(type)) {
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

    private <T> T failIfEmpty(Optional<T> value, Class<?> returnType, Class<?>... parameterTypes) {
        return value.orElseThrow(() -> new NoSuchProviderException(returnType, parameterTypes, "Cannot transform " + Arrays.toString(parameterTypes) + " to " + returnType.getName()));
    }

    private boolean typesAreConvertible(Class<?> t1, Class<?> t2) {
        return Types.isAssignableFrom(t1, t2) || Types.isAssignableFrom(t2, t1);
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
