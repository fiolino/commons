package org.fiolino.common.ioc;

import org.fiolino.annotations.PostCreate;
import org.fiolino.annotations.PostProcessor;
import org.fiolino.annotations.Provider;
import org.fiolino.annotations.Requested;
import org.fiolino.common.reflection.*;
import org.fiolino.common.util.CharSet;
import org.fiolino.common.util.Types;

import javax.annotation.Nullable;
import java.lang.invoke.*;
import java.lang.reflect.*;
import java.text.DateFormat;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.time.temporal.Temporal;
import java.util.*;
import java.util.function.*;
import java.util.stream.Stream;

import static java.lang.invoke.MethodHandles.dropArguments;
import static java.lang.invoke.MethodHandles.filterReturnValue;
import static java.lang.invoke.MethodHandles.foldArguments;
import static java.lang.invoke.MethodHandles.insertArguments;
import static java.lang.invoke.MethodHandles.publicLookup;
import static java.lang.invoke.MethodHandles.Lookup;
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
     * Returns an empty instance. This will find no factory handle at all, but always return an empty Optional
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
     *
     * @implNote All created lambdas will be direct ones
     */
    public static FactoryFinder instantiator() {
        return USE_CONSTRUCTOR;
    }

    /**
     * Returns an instance that always tried to instantiate the return type using its constructor.
     * Can also create private instances as long as they are visible from this constructor.
     *
     * @implNote All created lambdas will be direct ones
     */
    public static FactoryFinder instantiator(Lookup lookup) {
        return instantiator().using(lookup);
    }

    /**
     * Returns an instance that converts the following types:
     * <ul>
     *     <li>If there is a matching static {@code valueOf} method, then this will be used;</li>
     *     <li>Strings will be parsed to numbers and vice versa</li>
     *     <li>wrapper types can be converted to any primitive</li>
     *     <li>Enums and Strings are convertible as well using the enum's names</li>
     *     <li>in any other case, the constructor is called</li>
     * </ul>
     *
     * @implNote All created lambdas will be direct ones
     */
    public static FactoryFinder minimal() {
        return Defaults.MINIMAL;
    }

    /**
     * Returns an instance that converts all types like the minimal instance, plus the  following:
     * <ul>
     *     <li>{@code Date} instances are converted to their millisecond values</li>
     *     <li>{@code Date} instances are converted to {@code Instant} or {@code LocalDateTime} instances (using UTC) and vice versa</li>
     *     <li>{@code Date} instances are converted to all three java.sql date types</li>
     *     <li>Strings are converted to char primitives using their first character</li>
     *     <li>a char of 't', 'y', 'w' (case insensitive) and '1' is {@code true}, all others are {@code false}</li>
     * </ul>
     *
     * @implNote All created lambdas will be direct ones
     */
    public static FactoryFinder full() {
        return Defaults.FULL;
    }

    /**
     * Creates a handle that converts to the given type.
     *
     * @param returnType Some class to return
     * @param parameterTypes Some parameter types
     * @return A handle accepting the given parameter types, and returning the type
     */
    public final Optional<MethodHandle> find(Class<?> returnType, Class<?>... parameterTypes) {
        return find(null, returnType, parameterTypes);
    }

    /**
     * Creates a handle that converts to the given type.
     *
     * @param lookup Use this to find method handles. Has no effect on visibility of provider methods.
     * @param returnType Some class to return
     * @param parameterTypes Some parameter types
     * @return A handle accepting the given parameter types, and returning the type
     */
    public final Optional<MethodHandle> find(@Nullable Lookup lookup, Class<?> returnType, Class<?>... parameterTypes) {
        return findDirect(lookup, this, x -> true, returnType, parameterTypes)
                .map(h -> runPostProcessor(lookup, returnType).map(f -> filterReturnValue(h, f)).orElse(h));
    }

    /**
     * Creates a handle that converts to the given type.
     *
     * @param returnType Some class to return
     * @param parameterTypes Some parameter types
     * @return A handle accepting the given parameter types, and returning the type
     * @throws NoMatchingFactoryException If there is no provider found
     */
    public final MethodHandle findOrFail(Class<?> returnType, Class<?>... parameterTypes) {
        return find(returnType, parameterTypes).orElseThrow(() -> exceptionFor(returnType, parameterTypes));
    }

    /**
     * Creates a handle that converts to the given type.
     *
     * @param lookup Use this to find method handles. Has no effect on visibility of provider methods.
     * @param returnType Some class to return
     * @param parameterTypes Some parameter types
     * @return A handle accepting the given parameter types, and returning the type
     * @throws NoMatchingFactoryException If there is no provider found
     */
    public final MethodHandle findOrFail(Lookup lookup, Class<?> returnType, Class<?>... parameterTypes) {
        return find(lookup, returnType, parameterTypes).orElseThrow(() -> exceptionFor(returnType, parameterTypes));
    }

    /**
     * Creates a handle that converts the given type.
     *
     * @param methodType Some type which returns an instantiable class
     * @return A handle of the requested type, if the constructor or factory method was found
     */
    public final Optional<MethodHandle> find(MethodType methodType) {
        return find(methodType.returnType(), methodType.parameterArray());
    }

    /**
     * Creates a handle that converts the given type.
     *
     * @param lookup Use this to find method handles. Has no effect on visibility of provider methods.
     * @param methodType Some type which returns an instantiable class
     * @return A handle of the requested type, if the constructor or factory method was found
     */
    public final Optional<MethodHandle> find(Lookup lookup, MethodType methodType) {
        return find(lookup, methodType.returnType(), methodType.parameterArray());
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
     * @param handle Will be called if the given type is compatible
     * @param initializers These will be bound to the handle as its first parameters
     * @return The new {@link FactoryFinder} with that extended setup
     */
    public FactoryFinder withMethodHandle(MethodHandle handle, Object... initializers) {
        if (initializers.length == 0) {
            return new FixedHandle(this, handle);
        }
        if (initializers.length > handle.type().parameterCount()) {
            throw new IllegalArgumentException("Too many initializers: " + Arrays.toString(initializers) + " for " + handle.type());
        }
        return new WithHandleAndInitializers(this, handle, NO_SPECIAL_CLASSES, null, initializers);
    }

    /**
     * Returns an instance that checks the given provider before iterating over my already existing setup.
     *
     * @param method This method will be called if the type matches with the requested one.
     *               If this is an instance method, an instance of the declaring class will be injected
     * @return The new {@link FactoryFinder} with that extended setup
     */
    public FactoryFinder withMethod(Method method) {
        return withMethod(lookupForProviders(), method);
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
        return withProvidersFrom(lookupForProviders(), providerContainer);
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
        return withProvidersFrom(lookupForProviders(), providerContainer);
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
     * Please note the this given lookup is visible at least in {@link MethodHandleProvider}s that might be added later.
     * For security reasons, you should keep the returned instance either in a private environment, or call
     * secured() before returning it.
     *
     * @param lookup This is used as the default to find factory handles and provider methods, if no explicit
     *               lookup is used as a parameter
     * @return An instance using this lookup
     */
    public FactoryFinder using(Lookup lookup) {
        return new LookupHolder(this, lookup);
    }

    /**
     * Returns a FactoryFinder that will still use its lookup for already registered providers (e.g. for calling
     * the constructor, or for creating lambdas), but will use a lookup with only public visibility for providers
     * that get registered hereafter.
     *
     * This only sets the default lookup. When a lookup is specified as a parameter, then that one will be used.
     *
     * The returned instance then cannot be used to  peek into private members of the used lookup instance.
     *
     * @return An instance that is safe to return to foreign code
     */
    public FactoryFinder secured() {
        if ((lookupForProviders().lookupModes() ^ Lookup.PUBLIC) == 0) {
            // Then it was already save anyway
            return this;
        }
        return new Secured(this);
    }

    /**
     * Returns a FactoryFinder that converts Strings to the given temporal type and vice versa using the given formatter.
     *
     * @implNote The created lambdas for these converters will be proxy instances
     *
     * @param type Which temporal to convert - LocaleDate, LocaleTime, etc.
     * @param formatter Use this
     * @return The FactoryFinder that uses these converters on top of my already existing ones
     */
    public FactoryFinder formatting(Class<? extends Temporal> type, DateTimeFormatter formatter) {
        MethodHandle parse, format;
        try {
            parse = publicLookup().findStatic(type, "parse", methodType(type, CharSequence.class, DateTimeFormatter.class));
            format = publicLookup().findVirtual(type, "format", methodType(String.class, DateTimeFormatter.class));
        } catch (NoSuchMethodException | IllegalAccessException ex) {
            throw new IllegalArgumentException(type.getName() + " cannot use the formatter", ex);
        }

        parse = insertArguments(parse, 1, formatter);
        format = insertArguments(format, 1, formatter);
        return withMethodHandle(parse).withMethodHandle(format);
    }

    /**
     * Returns a FactoryFinder that converts Strings to the given temporal type and vice versa using the given format string.
     *
     * @implNote The created lambdas for these converters will be proxy instances
     *
     * @param type Which tempporal to convert - LocaleDate, LocaleTime, etc.
     * @param dateTimeFormat Use this
     * @return The FactoryFinder that uses these converters on top of my already existing ones
     */
    public FactoryFinder formatting(Class<? extends Temporal> type, String dateTimeFormat) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(dateTimeFormat);
        return formatting(type, formatter);
    }

    /**
     * Returns a FactoryFinder that converts Strings to the given temporal type and vice versa using the given format
     * string and locale.
     *
     * @implNote The created lambdas for these converters will be proxy instances
     *
     * @param type Which temporal to convert - LocaleDate, LocaleTime, etc.
     * @param dateTimeFormat Use this
     * @param locale Used to create the formatter
     * @return The FactoryFinder that uses these converters on top of my already existing ones
     */
    public FactoryFinder formatting(Class<? extends Temporal> type, String dateTimeFormat, Locale locale) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(dateTimeFormat, locale);
        return formatting(type, formatter);
    }

    /**
     * Returns a FactoryFinder that converts between Strings and legacy Dates using the given date format.
     *
     * @implNote The created lambdas will be direct ones
     *
     * @param dateFormat Use this
     * @return The FactoryFinder that uses these converters on top of my already existing ones
     */
    public FactoryFinder formatting(DateFormat dateFormat) {
        MethodHandle parse, format;
        try {
            parse = publicLookup().findVirtual(DateFormat.class, "parse", methodType(Date.class, String.class));
            format = publicLookup().findVirtual(DateFormat.class, "format", methodType(String.class, Date.class));
        } catch (NoSuchMethodException | IllegalAccessException ex) {
            throw new AssertionError(ex);
        }

        return withMethodHandle(parse, dateFormat).withMethodHandle(format, dateFormat);
    }

    /**
     * Returns a FactoryFinder that converts between Strings and all standard JDK date classes:
     * <ul>
     *     <li>{@link Date}</li>
     *     <li>{@link LocalDate}</li>
     *     <li>{@link LocalTime}</li>
     *     <li>{@link LocalDateTime}</li>
     * </ul>
     *
     * The default format depending on the given locale is used.
     *
     * @implNote The created lambdas for the Temporal classes will be proxy instances, for the legacy Date class will be direct
     *
     * @param style Which style to use for the format
     * @param locale The locale
     * @return The new FactoryFinder
     */
    public FactoryFinder formatting(FormatStyle style, Locale locale) {
        FactoryFinder result = formatting(LocalDate.class, DateTimeFormatter.ofLocalizedDate(style).localizedBy(locale))
            .formatting(LocalTime.class, DateTimeFormatter.ofLocalizedTime(style).localizedBy(locale))
            .formatting(LocalDateTime.class, DateTimeFormatter.ofLocalizedDateTime(style).localizedBy(locale));

        int oldStyle = FormatStyleConverter.oldDateFormatStyleFor(style);
        DateFormat df = DateFormat.getDateTimeInstance(oldStyle, oldStyle, locale);
        return result.formatting(df);
    }

    /**
     * Returns a FactoryFinder that converts between Strings and all standard JDK date classes:
     * <ul>
     *     <li>{@link Date}</li>
     *     <li>{@link LocalDate}</li>
     *     <li>{@link LocalTime}</li>
     *     <li>{@link LocalDateTime}</li>
     * </ul>
     *
     * The default format based on the default locale is used.
     *
     * @implNote The created lambdas for the Temporal classes will be proxy instances, for the legacy Date class will be direct
     *
     * @param style Which style to use for the format
     * @return The new FactoryFinder
     */
    public FactoryFinder formatting(FormatStyle style) {
        return formatting(style, Locale.getDefault(Locale.Category.FORMAT));
    }

    /**
     * Tries to find a suitable transformer handle, and executes it with the given arguments.
     * Can be used to directly instantiate a type, or to convert some existing value to its target type.
     *
     * @param expectedType The target type
     * @param parameters The input values. These must not be null, because they're used to identify the transformer
     * @param <R> The target type
     * @return The converted value
     * @throws NoMatchingFactoryException If there is no suitable transformer
     */
    public <R> R transform(Class<R> expectedType, Object... parameters) {
        Class<R> castType = Types.toWrapper(expectedType);
        int n = parameters.length;
        if (n == 1 && castType.isInstance(parameters[0])) {
            return castType.cast(parameters[0]);
        }
        Class<?>[] parameterTypes = new Class<?>[n];
        for (int i=n-1; i >= 0; i--) {
            parameterTypes[i] = parameters[i].getClass();
        }
        return find(expectedType, parameterTypes)
                .or(() -> {
                    Class<?> primitive;
                    if (parameterTypes.length == 1 && (primitive = Types.asPrimitive(parameterTypes[0])) != null) {
                        return find(expectedType, primitive);
                    } else {
                        return Optional.empty();
                    }
                })
                .map(h -> {
                    try {
                        return castType.cast(h.invokeWithArguments(parameters));
                    } catch (RuntimeException | Error e) {
                        throw e;
                    } catch (Throwable t) {
                        throw new UndeclaredThrowableException(t, "Transforming using " + h);
                    }
                }).orElseThrow(() -> exceptionFor(expectedType, parameterTypes));
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
        return convertReturnTypeTo0(target, returnType)
                .orElseGet(() -> MethodHandles.explicitCastArguments(target, target.type().changeReturnType(returnType)));
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
        return convertArgumentTypesTo(target, argumentNumber, n, inputTypes, true);
    }

    private Optional<MethodHandle> convertReturnTypeTo0(MethodHandle target, Class<?> returnType) {
        MethodType type = target.type();
        Class<?> r = type.returnType();
        if (typesAreInHierarchy(returnType, r)) {
            return Optional.empty();
        }
        if (returnType == void.class) {
            return Optional.of(target.asType(type.changeReturnType(void.class)));
        }
        return find(returnType, r)
                .map(h -> h.asType(methodType(returnType, r)))
                .map(h -> MethodHandles.filterReturnValue(target, h));
    }

    private MethodHandle convertArgumentTypesTo(MethodHandle target, int argumentNumber, int numberOfConversions, Class<?>[] inputTypes, boolean castType) {
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
            if (typesAreInHierarchy(arg, newType)) {
                if (castType) {
                    if (casted == null) casted = type;
                    casted = casted.changeParameterType(argumentNumber + i, newType);
                }
                continue;
            }
            Optional<MethodHandle> converter = find(arg, newType).map(h -> h.asType(methodType(arg, newType)));
            if (converter.isPresent()) {
                filters[i] = converter.get();
                continue;
            }
            if (arg.isPrimitive() && newType.isPrimitive()) {
                if (casted == null) casted = type;
                casted = casted.changeParameterType(argumentNumber + i, newType);
                continue;
            }
            throw new NoMatchingFactoryException(arg, new Class<?>[] { newType} , "Cannot convert parameter #" + i + " of " + target + " to " + newType.getName());
        }
        MethodHandle castedTarget = casted == null ? target : MethodHandles.explicitCastArguments(target, casted);
        return MethodHandles.filterArguments(castedTarget, argumentNumber, filters);
    }

    /**
     * Converts the arguments and return type of the given target handle so that it will match the given type.
     * <p>
     * The resulting handle will be of the given type. All arguments and the return value are being converted
     * via this instance.
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
     *                         and then added as trailing arguments to the resulting handle.
     * @return A handle of the same type as specified
     * @throws NoMatchingFactoryException If one of the arguments or return type can't be converted
     * @throws IllegalArgumentException   If the target handle expects more arguments than the new type,
     *                                           but there are not enough additional values given as constant replacements
     */
    public MethodHandle convertTo(MethodHandle target, MethodType type, Object... additionalValues) {

        MethodHandle casted = convertReturnTypeTo(target, type.returnType());
        MethodType t = target.type();
        int expectedParameterSize = type.parameterCount();
        int actualParameterSize = t.parameterCount();
        int argSize = Math.min(actualParameterSize, expectedParameterSize);
        MethodHandle result = convertArgumentTypesTo(casted, 0, argSize, type.parameterArray(), true);
        int reduceSize = actualParameterSize - expectedParameterSize;
        if (reduceSize > 0) {
            // Original target expects more parameters
            if (additionalValues.length < reduceSize) {
                throw new IllegalArgumentException("Expects at least " + reduceSize + " additional values to map " + t
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
            return insertArguments(result, argSize, add);
        } else if (reduceSize < 0) {
            // New type expects more arguments, ignore them
            reduceSize *= -1;
            Class<?>[] dispensableTypes = new Class<?>[reduceSize];
            System.arraycopy(type.parameterArray(), actualParameterSize, dispensableTypes, 0, reduceSize);
            return dropArguments(result, actualParameterSize, dispensableTypes);
        }

        return result;
    }

    /**
     * Creates a provider which implements the given functional interface an calls the appropriate provider method or constructor.
     *
     * @param functionalInterface Describes the provider/constructor to use. Its method signature specifies the parameter values and return type.
     * @param <T>                 The interface type
     * @return A lambda or proxy
     * @throws NoMatchingFactoryException If there is no provider that converts from the sources to the result type
     */
    public <T> T createLambda(Class<T> functionalInterface) {
        return createLambda(lookupForEvaluation(), functionalInterface);
    }

    /**
     * Creates a provider which implements the given functional interface an calls the appropriate provider method or constructor.
     *
     * @param lookup Use this to find the factory handle and create a lambda out of it.
     *               Should be the caller's lookup.
     * @param functionalInterface Describes the provider/constructor to use. Its method signature specifies the parameter values and return type.
     * @param <T>                 The interface type
     * @return A lambda or proxy
     * @throws NoMatchingFactoryException If there is no provider that converts from the sources to the result type
     */
    public <T> T createLambda(Lookup lookup, Class<T> functionalInterface) {
        Method lambdaMethod = Methods.findLambdaMethodOrFail(functionalInterface);
        Class<?> returnType = lambdaMethod.getReturnType();
        Class<?>[] parameterTypes = lambdaMethod.getParameterTypes();
        return createLambdaUnchecked(lookup, functionalInterface, returnType, parameterTypes);
    }

    /**
     * Creates the lambda. Assumes that the parameters are already validated.
     */
    private <T> T createLambdaUnchecked(Lookup lookup, Class<T> functionalInterface, Class<?> returnType, Class<?>[] parameterTypes) {
        if (functionalInterface.equals(Function.class)) {
            T lambda = lambdafyDirect(lookup, functionalInterface, returnType, parameterTypes);
            @SuppressWarnings("unchecked")
            T lambdaFunction = (T) runPostProcessor(lookup, returnType, (Function<?, ?>) lambda);
            return lambdaFunction;
        }
        if (functionalInterface.equals(Supplier.class)) {
            T lambda = lambdafyDirect(lookup, functionalInterface, returnType, parameterTypes);
            @SuppressWarnings("unchecked")
            T lambdaFunction = (T) runPostProcessor(lookup, returnType, (Supplier<?>) lambda);
            return lambdaFunction;
        }
        Optional<MethodHandle> postProcessor = runPostProcessor(lookup, returnType);
        if (postProcessor.isEmpty()) {
            // No post processor
            return lambdafyDirect(lookup, functionalInterface, returnType, parameterTypes);
        }
        return postProcessor
                .map(f -> findDirect(lookup, this, x -> true, returnType, parameterTypes).map(h -> filterReturnValue(h, f)).orElseThrow(() -> exceptionFor(returnType, parameterTypes)))
                .map(h -> Methods.lambdafy(lookup, h, functionalInterface))
                .orElseGet(() -> lambdafyDirect(lookup, functionalInterface, returnType, parameterTypes));
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
     * @throws NoMatchingFactoryException If there is no provider that converts from the sources to the result type
     */
    public <T> T createLambda(Class<T> functionalInterface, Class<?> returnType, Class<?>... parameterTypes) {
        return createLambda(lookupForEvaluation(), functionalInterface, returnType, parameterTypes);
    }

    /**
     * Creates a provider which implements the given functional interface and calls the appropriate provider method or constructor.
     * This method can be used when the given interface is generic, so that the signature doesn't specify the argument and return type enough.
     *
     * @param lookup Use this to find the factory handle and create a lambda out of it.
     *               Should be the caller's lookup.
     * @param functionalInterface Describes the provider/constructor to use. Its method signature specifies parts of the parameter values.
     * @param returnType          The expected return type
     * @param argumentTypes      The expected parameter types; only the first ones that differ from the lambda method signature
     *                            must be given
     * @param <T>                 The interface type
     * @return A lambda or proxy
     * @throws NoMatchingFactoryException If there is no provider that converts from the sources to the result type
     */
    public <T> T createLambda(Lookup lookup, Class<T> functionalInterface, Class<?> returnType, Class<?>... argumentTypes) {
        Method lambdaMethod = Methods.findLambdaMethodOrFail(functionalInterface);

        Class<?>[] lambdaTypes = lambdaMethod.getParameterTypes();
        if (lambdaTypes.length < argumentTypes.length) {
            throw new IllegalArgumentException(classArrayToNames("Given too many parameter types: Expected ", ",", lambdaTypes)
                    + classArrayToNames(" given ", "", argumentTypes));
        }
        System.arraycopy(argumentTypes, 0, lambdaTypes, 0, argumentTypes.length);
        return createLambdaUnchecked(lookup, functionalInterface, returnType, lambdaTypes);
    }

    /**
     * Creates a {@link Supplier} that will return a new instance on every call.
     *
     * @param type The type to instantiate; needs an empty public constructor
     * @param <T>  The type
     * @return The Supplier
     */
    public  <T> Supplier<T> createSupplierFor(Class<T> type) {
        @SuppressWarnings("unchecked")
        Supplier<T> supplier = createLambda(Supplier.class, type);
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
        Function<P, T> function = createLambda(Function.class, type, argumentType);
        return function;
    }

    /**
     * Creates a {@link Supplier} that will return a new instance on every call.
     *
     * @param lookup Use this to find the factory handle and create a lambda out of it.
     *               Should be the caller's lookup.
     * @param type The type to instantiate; needs an empty public constructor
     * @param <T>  The type
     * @return The Supplier
     */
    public  <T> Supplier<T> createSupplierFor(Lookup lookup, Class<T> type) {
        @SuppressWarnings("unchecked")
        Supplier<T> supplier = createLambda(lookup, Supplier.class, type);
        return supplier;
    }

    /**
     * Creates a {@link Function} that will return a new instance on every call.
     *
     * @param lookup Use this to find the factory handle and create a lambda out of it.
     *               Should be the caller's lookup.
     * @param type The type to instantiate; needs a public constructor with exactly one argument
     *             of type argumentType, or an empty one as an alternative
     * @param <T>  The type
     * @return The Function that accepts the only parameter and returns a freshly intantiated object
     */
    public <T, P> Function<P, T> createFunctionFor(Lookup lookup, Class<T> type, Class<P> argumentType) {
        @SuppressWarnings("unchecked")
        Function<P, T> function = createLambda(lookup, Function.class, type, argumentType);
        return function;
    }

//    public <T> T lambdafy(Lookup lookup, MethodHandle target, Class<T> functionalType, Object... initializers) {
//
//    }

    abstract Lookup lookupForProviders();
    abstract Lookup lookupForEvaluation();

    abstract Optional<MethodHandle> findDirect(@Nullable Lookup lookup, FactoryFinder starter, Predicate<FactoryFinder> allowed, Class<?> returnType, Class<?>... argumentTypes);

    final <T> T lambdafyDirect(Lookup lookup, Class<T> functionalInterface, Class<?> returnType, Class<?>[] argumentTypes) {
        return lambdafyDirect(lookup, functionalInterface, this, x -> true, returnType, argumentTypes);
    }

    abstract <T> T lambdafyDirect(Lookup lookup, Class<T> functionalInterface, FactoryFinder starter, Predicate<FactoryFinder> allowed, Class<?> returnType, Class<?>[] argumentTypes);

    private static final FactoryFinder EMPTY = new FactoryFinder() {
        @Override
        Optional<MethodHandle> findDirect(Lookup lookup, FactoryFinder starter, Predicate<FactoryFinder> allowed, Class<?> returnType, Class<?>... argumentTypes) {
            return Optional.empty();
        }

        @Override
        Lookup lookupForProviders() {
            return publicLookup();
        }

        @Override
        Lookup lookupForEvaluation() {
            return publicLookup();
        }

        @Override
        <T> T lambdafyDirect(Lookup lookup, Class<T> functionalInterface, FactoryFinder starter, Predicate<FactoryFinder> allowed, Class<?> returnType, Class<?>[] argumentTypes) {
            throw exceptionFor(returnType, argumentTypes);
        }
    };

    private static final FactoryFinder USE_CONSTRUCTOR = EMPTY.withMethodHandleProvider(
            (r, t) -> r.register(r.lookup().findConstructor(t.returnType(), t.changeReturnType(void.class))));

    private static final Class<?>[] NO_SPECIAL_CLASSES = {};

    private static abstract class Wrapper extends FactoryFinder {
        final FactoryFinder wrapped;

        Wrapper(FactoryFinder wrapped) {
            this.wrapped = wrapped;
        }

        @Override
        Lookup lookupForProviders() {
            return wrapped.lookupForProviders();
        }

        @Override
        Lookup lookupForEvaluation() {
            return wrapped.lookupForEvaluation();
        }

        @Override
        final Optional<MethodHandle> findDirect(Lookup lookup, FactoryFinder starter, Predicate<FactoryFinder> allowed, Class<?> returnType, Class<?>... argumentTypes) {
            if (allowed.test(this)) {
                Optional<MethodHandle> handle = findDirectX(lookup, starter, allowed, returnType, argumentTypes);
                if (handle.isPresent()) return handle;
            }
            return nextFinder(lookup, starter, allowed, returnType, argumentTypes);
        }

        Optional<MethodHandle> nextFinder(Lookup lookup, FactoryFinder starter, Predicate<FactoryFinder> allowed, Class<?> returnType, Class<?>[] argumentTypes) {
            return wrapped.findDirect(lookup, starter, allowed, returnType, argumentTypes);
        }

        abstract Optional<MethodHandle> findDirectX(Lookup lookup, FactoryFinder starter, Predicate<FactoryFinder> allowed, Class<?> returnType, Class<?>... argumentTypes);

        <T> T lambdafyDirect(Lookup lookup, Class<T> functionalInterface, FactoryFinder starter, Predicate<FactoryFinder> allowed, Class<?> returnType, Class<?>[] argumentTypes) {
            return wrapped.lambdafyDirect(lookup, functionalInterface, starter, allowed, returnType, argumentTypes);
        }
    }

    private static class LookupHolder extends Wrapper {
        private final Lookup lookup;

        LookupHolder(FactoryFinder wrapped, Lookup lookup) {
            super(wrapped);
            this.lookup = lookup;
        }

        @Override
        Lookup lookupForProviders() {
            return lookup;
        }

        @Override
        Lookup lookupForEvaluation() {
            return lookup;
        }

        @Override
        public FactoryFinder using(Lookup lookup) {
            return new LookupHolder(wrapped, lookup);
        }

        @Override
        Optional<MethodHandle> nextFinder(Lookup lookup, FactoryFinder starter, Predicate<FactoryFinder> allowed, Class<?> returnType, Class<?>[] argumentTypes) {
            Lookup l = lookup == null ? lookupForEvaluation() : lookup;
            return super.nextFinder(l, starter, allowed, returnType, argumentTypes);
        }

        @Override
        Optional<MethodHandle> findDirectX(Lookup lookup, FactoryFinder starter, Predicate<FactoryFinder> allowed, Class<?> returnType, Class<?>... argumentTypes) {
            return Optional.empty();
        }
    }

    private static class Secured extends Wrapper {
        Secured(FactoryFinder wrapped) {
            super(wrapped);
        }

        @Override
        Lookup lookupForProviders() {
            return super.lookupForProviders().dropLookupMode(Lookup.MODULE);
        }

        @Override
        public FactoryFinder using(Lookup lookup) {
            return new LookupHolder(wrapped, lookup);
        }

        @Override
        Optional<MethodHandle> findDirectX(Lookup lookup, FactoryFinder starter, Predicate<FactoryFinder> allowed, Class<?> returnType, Class<?>... argumentTypes) {
            return Optional.empty();
        }
    }

    private static abstract class ConditionalWrapper extends Wrapper {
        ConditionalWrapper(FactoryFinder wrapped) {
            super(wrapped);
        }

        @Override
        Optional<MethodHandle> findDirectX(Lookup lookup, FactoryFinder starter, Predicate<FactoryFinder> allowed, Class<?> returnType, Class<?>... argumentTypes) {
            if (returnTypeMatches(returnType) && argumentsMatch(argumentTypes)) {
                MethodHandle h = getHandle(returnType, argumentTypes);
                if (h != null) return Optional.of(h);
            }
            return Optional.empty();
        }

        abstract boolean returnTypeMatches(Class<?> returnType);
        abstract boolean argumentsMatch(Class<?>[] argumentTypes);
        @Nullable abstract MethodHandle getHandle(Class<?> returnType, Class<?>[] argumentTypes);

        @Override
        <T> T lambdafyDirect(Lookup lookup, Class<T> functionalInterface, FactoryFinder starter, Predicate<FactoryFinder> allowed, Class<?> returnType, Class<?>[] argumentTypes) {
            if (returnTypeMatches(returnType) && argumentsMatch(argumentTypes)) {
                T result = lambdafyVerified(lookup, functionalInterface, returnType, argumentTypes);
                if (result != null)
                    return result;
            }

            return super.lambdafyDirect(lookup, functionalInterface, starter, allowed, returnType, argumentTypes);
        }

        <T> T lambdafyVerified(Lookup lookup, Class<T> functionalInterface, Class<?> returnType, Class<?>[] argumentTypes) {
            MethodHandle h = getHandle(returnType, argumentTypes);
            return h == null ? null : Methods.lambdafy(lookup, h, functionalInterface);
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
            if (argumentTypes.length + additionalParameterCount != n) return false;
            for (int i = n-1; i >= additionalParameterCount; i--) {
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
        MethodHandle getHandle(Class<?> returnType, Class<?>[] argumentTypes) {
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
    }

    private static class GenericHandleFinder extends FixedHandleForSpecificTypes {
        final RequestedClass requestedClass;
        private final Class<?>[] expectedArguments;

        GenericHandleFinder(FactoryFinder wrapped, MethodHandle handle, Class<?>[] acceptedClasses, RequestedClass requestedClass) {
            super(wrapped, handle, acceptedClasses);
            this.requestedClass = requestedClass;

            if (requestedClass == null) {
                expectedArguments = handle.type().parameterArray();
            } else {
                int argCount = handle.type().parameterCount() - 1;
                expectedArguments = new Class<?>[argCount];
                for (int i=0, j=0; j < argCount; i++) {
                    if (i == requestedClass.parameterIndex) continue;
                    expectedArguments[j++] = handle.type().parameterType(i);
                }
            }
        }

        @Override
        boolean defaultReturnTypeMatches(Class<?> returnType) {
            if (requestedClass == null) {
                return super.defaultReturnTypeMatches(returnType);
            }
            return requestedClass.upperBound.isAssignableFrom(returnType);
        }

        @Override
        Class<?>[] parametersToCheck() {
            return expectedArguments;
        }

        @Override
        MethodHandle getHandle(Class<?> returnType, Class<?>[] argumentTypes) {
            MethodHandle h = getBasicHandle(returnType, argumentTypes);
            return requestedClass == null ? h : insertArguments(h, requestedClass.parameterIndex, returnType);
        }

        MethodHandle getBasicHandle(Class<?> returnType, Class<?>[] argumentTypes) {
            return handle;
        }

        @Override
        final <T> T lambdafyVerified(Lookup lookup, Class<T> functionalInterface, Class<?> returnType, Class<?>[] argumentTypes) {
            if (requestedClass == null) {
                return lambdafyWithoutReturnType(lookup, functionalInterface, returnType);
            }
            if (requestedClass.parameterIndex == 0) {
                return lambdafyWithReturnType(lookup, functionalInterface, returnType);
            }
            return super.lambdafyVerified(lookup, functionalInterface, returnType, argumentTypes);
        }

        <T> T lambdafyWithoutReturnType(Lookup lookup, Class<T> functionalInterface, Class<?> returnType) {
            return Methods.lambdafy(lookup, handle, functionalInterface);
        }

        <T> T lambdafyWithReturnType(Lookup lookup, Class<T> functionalInterface, Class<?> returnType) {
            return Methods.lambdafy(lookup, handle, functionalInterface, returnType);
        }
    }

    private static class WithHandleAndInitializers extends GenericHandleFinder {
        private final Object[] initializers;

        WithHandleAndInitializers(FactoryFinder wrapped, MethodHandle handle, Class<?>[] acceptedClasses, RequestedClass requestedClass, Object... initializers) {
            super(wrapped, handle, acceptedClasses, requestedClass);
            this.initializers = initializers;
        }

        @Override
        boolean argumentsMatch(Class<?>[] argumentTypes) {
            return argumentsMatch(argumentTypes, initializers.length);
        }

        @Override
        MethodHandle getBasicHandle(Class<?> returnType, Class<?>[] argumentTypes) {
            return insertArguments(handle, 0, initializers);
        }

        @Override
        <T> T lambdafyWithoutReturnType(Lookup lookup, Class<T> functionalInterface, Class<?> returnType) {
            return Methods.lambdafy(lookup, handle, functionalInterface, initializers);
        }

        @Override
        <T> T lambdafyWithReturnType(Lookup lookup, Class<T> functionalInterface, Class<?> returnType) {
            int n = initializers.length;
            Object[] allInitializers = Arrays.copyOf(initializers, n+1);
            allInitializers[n] = returnType;
            return Methods.lambdafy(lookup, handle, functionalInterface, allInitializers);
        }
    }

    private static class FixedHandleWithFactory extends GenericHandleFinder {
        // ()<ProviderType>
        private final MethodHandle providerFactory;

        FixedHandleWithFactory(FactoryFinder wrapped, MethodHandle handle, Class<?>[] acceptedClasses, RequestedClass requestedClass, MethodHandle providerFactory) {
            super(wrapped, handle, acceptedClasses, requestedClass);
            this.providerFactory = providerFactory;
        }

        @Override
        MethodHandle getHandle(Class<?> returnType, Class<?>[] argumentTypes) {
            return MethodHandles.foldArguments(handle, providerFactory);
        }

        @Override
        boolean argumentsMatch(Class<?>[] argumentTypes) {
            return argumentsMatch(argumentTypes, 1);
        }


        private Object createFactoryInstance() {
            Object provider;
            try {
                provider = providerFactory.invoke();
            } catch (RuntimeException | Error e) {
                throw e;
            } catch (Throwable t) {
                throw new UndeclaredThrowableException(t, "Instantiation of " + providerFactory.type().returnType().getName()
                        + " failed");
            }
            if (provider == null) {
                throw new NullPointerException(providerFactory.type().returnType().getName());
            }
            return provider;
        }

        @Override
        <T> T lambdafyWithoutReturnType(Lookup lookup, Class<T> functionalInterface, Class<?> returnType) {
            Object instance = createFactoryInstance();
            return Methods.lambdafy(lookup, handle, functionalInterface, instance);
        }

        @Override
        <T> T lambdafyWithReturnType(Lookup lookup, Class<T> functionalInterface, Class<?> returnType) {
            Object instance = createFactoryInstance();
            return Methods.lambdafy(lookup, handle, functionalInterface, instance, returnType);
        }
    }

    private static class DynamicHandleFinder extends Wrapper {
        private final MethodHandleProvider handleProvider;

        private class MyRegistry implements MethodHandleRegistry {
            private final Lookup lookup;
            private final FactoryFinder starter;
            private final Predicate<FactoryFinder> allowed;
            private final MethodType methodType;
            private MethodHandle registeredHandle;
            private Object[] initializers;

            MyRegistry(Lookup lookup, FactoryFinder starter, Predicate<FactoryFinder> allowed, MethodType methodType) {
                this.lookup = lookup;
                this.starter = starter;
                this.allowed = allowed.and(x -> x != DynamicHandleFinder.this);
                this.methodType = methodType;
            }

            @Override
            public void register(MethodHandle handle, Object... initializers) throws MismatchedMethodTypeException {
                if (registeredHandle != null) {
                    throw new IllegalStateException("handle alfready registered");
                }
                MethodType type = handle.type().dropParameterTypes(0, initializers.length);
                if (!isConvertible(type, methodType)) {
                    throw new MismatchedMethodTypeException(type + " does not match expected " + methodType);
                }
                registeredHandle = handle;
                this.initializers = initializers;
            }

            private boolean isConvertible(Class<?> c1, Class<?> c2) {
                return c1.isPrimitive() && c2.isPrimitive()
                        || Types.toWrapper(c1).equals(Types.toWrapper(c2))
                        || c1.isAssignableFrom(c2)
                        || c2.isAssignableFrom(c1);
            }

            private boolean isConvertible(MethodType t1, MethodType t2) {
                int n = t1.parameterCount();
                if (n != t2.parameterCount()
                        || !isConvertible(t1.returnType(), t2.returnType())) return false;

                for (int i=0; i < n; i++) {
                    if (!isConvertible(t1.parameterType(i), t2.parameterType(i))) return false;
                }

                return true;
            }

            @Override
            public Lookup lookup() {
                return lookup;
            }

            @Override
            public MethodType type() {
                return methodType;
            }

            @Override
            public Optional<MethodHandle> findExisting() {
                return findExisting(methodType);
            }

            @Override
            public Optional<MethodHandle> findExisting(MethodType methodType) {
                return starter.findDirect(lookup, starter, allowed, methodType.returnType(), methodType.parameterArray());
            }

            MethodHandle getRegisteredHandle() {
                return registeredHandle;
            }

            Object[] getInitializers() {
                return initializers;
            }
        }

        private class MyLambdaRegistry<T> extends MyRegistry implements LambdaRegistry<T> {

            private final Class<T> functionalType;
            private T lambda;

            MyLambdaRegistry(Lookup lookup, FactoryFinder starter, Predicate<FactoryFinder> allowed, MethodType methodType, Class<T> functionalType) {
                super(lookup, starter, allowed, methodType);
                this.functionalType = functionalType;
            }

            @Override
            public Class<T> functionalType() {
                return functionalType;
            }

            @Override
            public void register(T lambda) throws MismatchedMethodTypeException {
                if (!functionalType.isInstance(lambda)) {
                    throw new MismatchedMethodTypeException("Expected " + functionalType.getName() + " but got " + lambda);
                }
                this.lambda = lambda;
            }

            T getLambda() {
                return lambda;
            }
        }

        DynamicHandleFinder(FactoryFinder wrapped, MethodHandleProvider handleProvider) {
            super(wrapped);
            this.handleProvider = handleProvider;
        }

        @Override
        public Optional<MethodHandle> findDirectX(Lookup lookup, FactoryFinder starter, Predicate<FactoryFinder> allowed, Class<?> returnType, Class<?>... argumentTypes) {
            Lookup l = lookup == null ? lookupForProviders() : lookup;
            MethodType type = methodType(returnType, argumentTypes);
            MyRegistry reg = new MyRegistry(l, starter, allowed, type);
            try {
                handleProvider.create(reg, type);
            } catch (NoSuchMethodException | IllegalAccessException | MismatchedMethodTypeException ex) {
                return Optional.empty();
            }
            return Optional.ofNullable(reg.getRegisteredHandle()).map(h -> insertArguments(h, 0, reg.getInitializers()));
        }

        @Override
        <T> T lambdafyDirect(Lookup lookup, Class<T> functionalInterface, FactoryFinder starter, Predicate<FactoryFinder> allowed, Class<?> returnType, Class<?>[] argumentTypes) {
            Lookup l = lookup == null ? lookupForProviders() : lookup;
            MethodType type = methodType(returnType, argumentTypes);
            MyLambdaRegistry<T> reg = new MyLambdaRegistry<>(l, starter, allowed, type, functionalInterface);
            try {
                handleProvider.lambdafy(reg, functionalInterface, type);
            } catch (NoSuchMethodException | IllegalAccessException | MismatchedMethodTypeException ex) {
                // default to next finder
            }
            T lambda = reg.getLambda();
            if (lambda == null) {
                MethodHandle handle = reg.getRegisteredHandle();
                if (handle != null) {
                    return Methods.lambdafy(l, handle, functionalInterface, reg.getInitializers());
                }
            }
            return wrapped.lambdafyDirect(lookup, functionalInterface, starter, allowed, returnType, argumentTypes);
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

    private MethodHandle bindTo(Lookup lookup, MethodHandle h, Class<?> boundType, Object probablyExisting) {
        if (probablyExisting != null) {
            return h.bindTo(probablyExisting);
        }
        MethodHandle factory = OneTimeExecution.createFor(findOrFail(lookup, boundType)).getAccessor();
        return foldArguments(h, factory);
    }

    private FactoryFinder staticNondirect(Lookup lookup, MethodHandle handle, boolean optional, RequestedClass requestedClass, Class<?>[] acceptedTypes) {
        if (optional) handle = makeOptional(lookup, handle);
        return requestedClass == null ? new FixedHandleForSpecificTypes(this, handle, acceptedTypes) :
                new GenericHandleFinder(this, handle, acceptedTypes, requestedClass);
    }

    private Class<?> commonSubclassOf(Class<?> returnType, Class<?> parameterizedArgument) {
        if (returnType.isAssignableFrom(parameterizedArgument)) return parameterizedArgument;
        if (parameterizedArgument.isAssignableFrom(returnType)) return returnType;
        throw new AssertionError("Bad factory method: Return type " + returnType.getName() + " and argument type " + parameterizedArgument.getName() + " are not compatible.");
    }

    private FactoryFinder registerAllProvidersFrom(MethodLocator locator, Object providerInstanceOrNull) {
        return locator.methods().filter(m -> m.isAnnotationPresent(Provider.class))
                .reduce(this, (ff, m) -> ff.registerProviderMethod(locator.lookup(), m.getMethod(), providerInstanceOrNull), (ff1, ff2) -> {
            throw new UnsupportedOperationException();
        });
    }

    private FactoryFinder registerProviderMethod(Lookup lookup, Method method, Object providerInstanceOrNull) {
        MethodHandle handle = unreflect(lookup, method);

        RequestedClass requestedClass = findRequestedClass(method);
        Provider annotation = method.getAnnotation(Provider.class);
        Class<?>[] acceptedTypes = annotation == null ? NO_SPECIAL_CLASSES : annotation.value();
        boolean optional = method.isAnnotationPresent(Nullable.class) || annotation != null && annotation.optional();

        if (Modifier.isStatic(method.getModifiers())) {
            return staticNondirect(lookup, handle, optional, requestedClass, acceptedTypes);
        } else {
            if (optional || requestedClass != null && requestedClass.parameterIndex > 1) {
                // Cannot be a direct lambda method
                return staticNondirect(lookup, bindTo(lookup, handle, method.getDeclaringClass(), providerInstanceOrNull), optional, requestedClass, acceptedTypes);
            } else {
                // Might be direct
                if (providerInstanceOrNull == null) {
                    MethodHandle factory = OneTimeExecution.createFor(findOrFail(lookup, method.getDeclaringClass())).getAccessor();
                    return new FixedHandleWithFactory(this, handle, acceptedTypes, requestedClass, factory);
                } else {
                    return new WithHandleAndInitializers(this, handle, acceptedTypes, requestedClass, providerInstanceOrNull);
                }
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
    private MethodHandle makeOptional(Lookup lookup, MethodHandle provider) {
        MethodType type = provider.type();
        Class<?> returnType = type.returnType();
        if (returnType.isPrimitive()) {
            // Cannot be nullable
            return provider;
        }
        MethodHandle existing = findOrFail(lookup, returnType, type.parameterArray());
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
                return new RequestedClass(i, commonSubclassOf(upperBound, m.getReturnType()));
            }
            i++;
        }
        return null;
    }

    private Optional<MethodHandle> runPostProcessor(@Nullable Lookup lookup, Class<?> type) {
        Lookup l = lookup == null ? lookupForEvaluation() : lookup;
        return streamPostProcessors(l, type)
                .map(h -> h.type().returnType() == void.class ? Methods.returnArgument(h, 0) : h)
                .map(h -> h.asType(methodType(type, type)))
                .reduce(MethodHandles::filterReturnValue);
    }

    private <T, R> Function<T, R> runPostProcessor(@Nullable Lookup lookup, Class<?> type, Function<T, R> factory) {
        Lookup l = lookup == null ? lookupForEvaluation() : lookup;
        if (MethodHandleProxies.isWrapperInstance(factory)) {
            // Bug in MethodHandleProxies does not allow default methods on wrapper instance
            MethodHandle result = foldFactoryWithPostProcessors(l, type, MethodHandleProxies.wrapperInstanceTarget(factory));
            @SuppressWarnings("unchecked")
            Function<T, R> func = (Function<T, R>) MethodHandleProxies.asInterfaceInstance(Function.class, result);
            return func;
        }
        return streamPostProcessors(l, type)
                .reduce(factory,
                        (f, pp) -> f.andThen(createFunction(l, pp)),
                        (f1, f2) -> {
                            throw new UnsupportedOperationException();
                        });
    }

    private <R> Supplier<R> runPostProcessor(@Nullable Lookup lookup, Class<?> type, Supplier<R> factory) {
        Lookup l = lookup == null ? lookupForEvaluation() : lookup;
        if (MethodHandleProxies.isWrapperInstance(factory)) {
            // Bug in MethodHandleProxies does not allow default methods on wrapper instance
            MethodHandle result = foldFactoryWithPostProcessors(l, type, MethodHandleProxies.wrapperInstanceTarget(factory));
            @SuppressWarnings("unchecked")
            Supplier<R> supplier = (Supplier<R>) MethodHandleProxies.asInterfaceInstance(Supplier.class, result);
            return supplier;
        }
        return streamPostProcessors(l, type)
                .reduce(factory, (f, h) -> enrich(l, f, h), (f1, f2) -> {
                    throw new UnsupportedOperationException();
                });
    }

    private MethodHandle foldFactoryWithPostProcessors(Lookup l, Class<?> type, MethodHandle factoryHandle) {
        return streamPostProcessors(l, type)
                .reduce(factoryHandle,
                        (f, pp) -> {
                            MethodHandle filter = pp.asType(pp.type().changeParameterType(0, f.type().returnType()));
                            if (pp.type().returnType() == void.class) {
                                return MethodHandles.filterReturnValue(f, Methods.returnArgument(filter, 0));
                            } else {
                                return MethodHandles.filterReturnValue(f, filter);
                            }
                        },
                        (f1, f2) -> {
                            throw new UnsupportedOperationException();
                        });
    }

    private static final MethodType interfaceMethodType = methodType(void.class);

    private Stream<MethodHandle> streamPostProcessors(Lookup lookup, Class<?> type) {
        return MethodLocator.forLocal(lookup, type).methods()
                .filter(m -> m.isAnnotationPresent(PostCreate.class) ||
                        PostProcessor.class.isAssignableFrom(type) && m.getName().equals("postConstruct") && m.getType().equals(interfaceMethodType))
                .map(m -> {
                    MethodHandle postProcessor = m.getHandle();
                    MethodType t = postProcessor.type();
                    if (t.parameterCount() != 1) {
                        throw new AssertionError(m + " is annotated with @" + PostCreate.class.getSimpleName() + " but should have exactly one parameter");
                    }
                    Class<?> returnType = t.returnType();
                    if (returnType != void.class && !type.isAssignableFrom(returnType)) {
                        if (m.getMethod().getDeclaringClass().isAssignableFrom(returnType)) {
                            // Then this is a @PostConstruct method from a superclass with a type not matching any more - ignore it
                            return null;
                        } else {
                            throw new AssertionError(m + " is annotated with @PostCreate but returns a type that does not match with " + type.getName());
                        }
                    }
                    return postProcessor;
                })
                .filter(Objects::nonNull);
    }

    private static <T> UnaryOperator<T> createFunction(Lookup lookup, MethodHandle handle) {
        if (handle.type().returnType() == void.class) {
            @SuppressWarnings("unchecked")
            Consumer<T> consumer = (Consumer<T>) Methods.lambdafy(lookup, handle, Consumer.class);
            return t -> {
                consumer.accept(t);
                return t;
            };
        }

        @SuppressWarnings("unchecked")
        UnaryOperator<T> f = (UnaryOperator<T>) Methods.lambdafy(lookup, handle, UnaryOperator.class);
        return f;
    }

    private static <R> Supplier<R> enrich(Lookup lookup, Supplier<R> supplier, MethodHandle handle) {
        if (handle.type().returnType() == void.class) {
            @SuppressWarnings("unchecked")
            Consumer<R> consumer = (Consumer<R>) Methods.lambdafy(lookup, handle, Consumer.class);
            return () -> {
                R result = supplier.get();
                consumer.accept(result);
                return result;
            };
        }

        @SuppressWarnings("unchecked")
        Function<R, R> f = (Function<R, R>) Methods.lambdafy(lookup, handle, Function.class);
        return () -> f.apply(supplier.get());
    }

    NoMatchingFactoryException exceptionFor(Class<?> returnType, Class<?>... argumentTypes) {
        return new NoMatchingFactoryException(returnType, argumentTypes,
                classArrayToNames("No handle for (", ")" + returnType.getName(), argumentTypes));
    }

    private String classArrayToNames(String prefix, String suffix, Class<?>... argumentTypes) {
        return Arrays.stream(argumentTypes).map(Class::getName).reduce(
                new StringJoiner(", ", prefix, suffix), StringJoiner::add, StringJoiner::merge)
                .toString();
    }

    private boolean typesAreInHierarchy(Class<?> t1, Class<?> t2) {
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

    private static class FormatStyleConverter {
        private static final Map<FormatStyle, Integer> styleMapping = new EnumMap<>(FormatStyle.class);

        static {
            Lookup l = publicLookup().in(DateFormat.class);
            try {
                for (FormatStyle fs : FormatStyle.values()) {
                    VarHandle constant = l.findStaticVarHandle(DateFormat.class, fs.name(), int.class);
                    int value = (int) constant.get();
                    styleMapping.put(fs, value);
                }
            } catch (NoSuchFieldException | IllegalAccessException ex) {
                throw new AssertionError(ex);
            }
        }

        static int oldDateFormatStyleFor(FormatStyle fs) {
            return styleMapping.get(fs);
        }
    }

    static class Defaults {
        static final FactoryFinder MINIMAL, FULL;

        static {
            MethodHandle getTime, numberToString, dateToInstant, instantToDate, contains;
            try {
                getTime = publicLookup().findVirtual(Date.class, "getTime", methodType(long.class));
                numberToString = publicLookup().findVirtual(Number.class, "toString", methodType(String.class));
                dateToInstant = publicLookup().findVirtual(Date.class, "toInstant", methodType(Instant.class));
                instantToDate = publicLookup().findStatic(Date.class, "from", methodType(Date.class, Instant.class));
                contains = publicLookup().findVirtual(CharSet.class, "contains", methodType(boolean.class, char.class));
            } catch (NoSuchMethodException | IllegalAccessException ex) {
                throw new AssertionError("getTime", ex);
            }

            MINIMAL = FactoryFinder.instantiator().withMethodHandleProvider((r, t) -> r.findStatic(t.returnType(), "valueOf"))
                    .withMethodHandleProvider((r, t) -> {
                        if (t.parameterCount() == 1) {
                            Class<?> returnType = t.returnType();
                            if (returnType.isPrimitive()) {
                                Class<?> argumentType = t.parameterType(0);
                                String name = returnType.getName();
                                if (String.class.equals(argumentType)) {
                                    // Finds something like Integer::parseInt
                                    r.findStatic(Types.toWrapper(returnType), "parse" + Character.toUpperCase(name.charAt(0)) + name.substring(1));
                                } else {
                                    // Finds something like Integer::longValue
                                    r.findVirtual(name + "Value");
                                }
                            }
                        }
                    }).withMethodHandleProvider((r, t) -> {
                        if (String.class.equals(t.returnType()) && t.parameterCount() == 1) {
                            Class<?> argumentType = t.parameterType(0);
                            if (argumentType.isEnum()) {
                                r.findVirtual("name");
                            }
                        }
                    }).withMethodHandle(numberToString);

            CharSet trueValueChars = CharSet.of("tTyYwW1");
            FULL = MINIMAL.withMethodHandle(getTime)
                    .withMethodHandle(dateToInstant)
                    .withMethodHandle(instantToDate)
                    .withMethodHandle(contains, trueValueChars)
                    .withProvidersFrom(MethodHandles.lookup(), new Object() {
                        @Provider @SuppressWarnings("unused")
                        char charFromString(String value) {
                            if (value.isEmpty()) return (char) 0;
                            return value.charAt(0);
                        }

                        @Provider
                        @SuppressWarnings("unused")
                        char charFromBool(boolean value) {
                            return value ? 't' : 'f';
                        }

                        @Provider @SuppressWarnings("unused")
                        boolean boolFromString(String value) {
                            if (value == null || value.isEmpty()) return false;
                            if (value.length() == 1) return trueValueChars.contains(value.charAt(0));
                            return Boolean.parseBoolean(value);
                        }

                        @Provider @SuppressWarnings("unused")
                        java.sql.Date sqlDateFromDate(Date origin) {
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
                        Date dateFromAnySQLTType(Date origin) {
                            return new Date(origin.getTime());
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
    }
}
