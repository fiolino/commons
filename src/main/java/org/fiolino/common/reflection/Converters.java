package org.fiolino.common.reflection;

import org.fiolino.common.util.CharSet;
import org.fiolino.common.util.Strings;
import org.fiolino.common.util.Types;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Date;

import static org.fiolino.common.reflection.Methods.instanceCheck;
import static org.fiolino.common.reflection.Methods.rejectIf;
import static java.lang.invoke.MethodHandles.publicLookup;
import static java.lang.invoke.MethodType.methodType;

/**
 * Static getters for default converters.
 * <p>
 * Created by Kuli on 6/17/2016.
 */
public final class Converters {

    private Converters() {
        throw new AssertionError();
    }

    public static ConversionRank compare(Class<?> generic, Class<?> specific) {
        if (generic == void.class || specific == void.class) {
            return ConversionRank.IMPOSSIBLE;
        }
        if (generic.equals(specific)) {
            return ConversionRank.IDENTICAL;
        }
        if (generic.isPrimitive()) {
            if (specific.isPrimitive()) {
                return ConversionRank.EXPLICITLY_CASTABLE;
            }
            if (Types.asPrimitive(specific) == generic) {
                return ConversionRank.WRAPPABLE;
            }
        } else if (specific.isPrimitive() && Types.asPrimitive(generic) == specific) {
            return ConversionRank.WRAPPABLE;
        }

        if (Types.isAssignableFrom(generic, specific)) {
            return ConversionRank.IN_HIERARCHY;
        }
        return ConversionRank.IMPOSSIBLE;
    }

    /**
     * Creates a MethodHandle that accepts some value as the only argument and returns the specified type.
     * <p/>
     * A static valueOf() method is called on the specified type, which works for all Number types and all Enums
     * plus a few more.
     * <p/>
     * For String/Number pairs, toString() resp. the constructor is used as well.
     * <p/>
     * Or, if the given type is a primitive, parseXXX() is called on the wrapper type, which works for all
     * primitive types except char and void.
     */
    public static MethodHandle createSimpleConverter(MethodHandles.Lookup lookup,
                                                     Class<?> inputType, Class<?> returnType) {
        if (inputType.equals(returnType)) {
            return null;
        }
        if (returnType.isPrimitive()) {
            if (String.class.equals(inputType)) {
                return createStringToPrimitiveConverter(returnType);
            }
            try {
                // Finds something like Integer.longValue()
                return lookup.in(inputType).findVirtual(inputType, returnType.getName() + "Value",
                        methodType(returnType));
            } catch (NoSuchMethodException | IllegalAccessException ex) {
                // No such method
                return null;
            }
        }
        MethodHandle pure = null;
        MethodHandles.Lookup l = lookup.in(returnType);
        try {
            // Try to find a static valueOf() method like in the Number or Enum types
            pure = l.findStatic(returnType, "valueOf", methodType(returnType, inputType));
        } catch (NoSuchMethodException | IllegalAccessException ex) {
            // Or try to find a constructor accepting the input
            if (Number.class.isAssignableFrom(returnType)) { // Date is handled explicitely
                try {
                    pure = l.findConstructor(returnType, methodType(void.class, inputType));
                } catch (NoSuchMethodException | IllegalAccessException next) {
                    // Then I just don't know
                    // Do nothing
                }
            }
        }
        if (pure != null) {
            if (String.class.equals(inputType)) {
                return MethodHandles.filterArguments(pure, 0, trim);
            }
            return pure;
        }
        if (returnType == String.class && Number.class.isAssignableFrom(inputType)) {
            try {
                return lookup.findVirtual(inputType, "toString", methodType(String.class));
            } catch (NoSuchMethodException | IllegalAccessException ex) {
                throw new AssertionError(inputType.getName() + ".toString() not available");
            }
        }

        return null;
    }

    /**
     * Converts from String to primitive numbers by calling parseXXX().
     *
     * @param primitiveType One of int, long, float, double, byte, short
     * @return (String)&lt;primitiveType&gt;
     */
    public static MethodHandle createStringToPrimitiveConverter(Class<?> primitiveType) {
        if (!primitiveType.isPrimitive()) {
            throw new IllegalArgumentException(primitiveType.getName());
        }
        if (primitiveType == char.class) {
            return getFirstChar;
        }
        if (primitiveType == boolean.class) {
            return stringToBool;
        }
        MethodHandle pure;
        Class<?> wrapperType = Types.toWrapper(primitiveType);
        String typeName = primitiveType.getName();
        try {
            pure = publicLookup().findStatic(wrapperType, Strings.addLeading(typeName, "parse"),
                    methodType(primitiveType, String.class));
        } catch (NoSuchMethodException | IllegalAccessException ex) {
            // Could only be the case for void
            throw new AssertionError("Cannot convert from String to " + primitiveType.getName(), ex);
        }
        return MethodHandles.filterArguments(pure, 0, trim);
    }

    /**
     * Creates a MethodHandle like the given target, except that it accepts Object in the given argument position.
     * That value can then either be of the original type, or it can be a String, as long as the type can be
     * converted from a String via createSimpleFromStringConverter().
     *
     * @param target         The handle to execute within
     * @param argumentNumber The argument to convert
     * @return A MethodHandle with the same type as target except the argumentNumber's position, which will be Object.
     */
    public static MethodHandle acceptString(MethodHandle target, int argumentNumber) {
        MethodType type = target.type();
        Methods.checkArgumentLength(type, argumentNumber);
        Class<?> argType = type.parameterType(argumentNumber);
        MethodHandle objectAccepting = target.asType(type.changeParameterType(argumentNumber, Object.class));
        MethodHandle converter = defaultConverters.find(String.class, argType);
        if (converter == null) {
            return objectAccepting;
        }
        converter = converter.asType(converter.type().changeParameterType(0, Object.class));
        MethodHandle stringAccepting = MethodHandles.filterArguments(target, argumentNumber, converter);

        MethodHandle check = instanceCheck(String.class);
        if (argumentNumber > 0) {
            Class<?>[] leadingArguments = Arrays.copyOf(type.parameterArray(), argumentNumber);
            check = MethodHandles.dropArguments(check, 0, leadingArguments);
        }
        return MethodHandles.guardWithTest(check, stringAccepting, objectAccepting);
    }

    public static final ExtendableConverterLocator defaultConverters;
    private static final MethodHandle trim, getFirstChar, charToBool, stringToBool;

    static {
        final MethodHandles.Lookup lookup = MethodHandles.lookup();
        try {
            trim = lookup.findVirtual(String.class, "trim", methodType(String.class));
        } catch (NoSuchMethodException | IllegalAccessException ex) {
            throw new AssertionError("String.trim()", ex);
        }
        MethodHandle stringEmptyCheck, charAt;
        try {
            stringEmptyCheck = lookup.findVirtual(String.class, "isEmpty", methodType(boolean.class));
        } catch (NoSuchMethodException | IllegalAccessException ex) {
            throw new AssertionError("String.isEmpty()", ex);
        }
        try {
            charAt = lookup.findVirtual(String.class, "charAt", methodType(char.class, int.class));
        } catch (NoSuchMethodException | IllegalAccessException ex) {
            throw new AssertionError("String.charAt(int)", ex);
        }
        getFirstChar = rejectIf(MethodHandles.insertArguments(charAt, 1, 0), stringEmptyCheck);
        CharSet trueChars = CharSet.of("tTyYwWX1");
        try {
            charToBool = lookup.findVirtual(CharSet.class, "contains", methodType(boolean.class, char.class))
                    .bindTo(trueChars);
        } catch (NoSuchMethodException | IllegalAccessException ex) {
            throw new AssertionError(ex);
        }
        stringToBool = MethodHandles.filterArguments(charToBool, 0, getFirstChar);

        ExtendableConverterLocator loc = ExtendableConverterLocator.EMPTY.register(lookup, new Object() {
            @Converter
            @SuppressWarnings("unused")
            private MethodHandle convertBasicTypes(Class<?> source, Class<?> target) {
                if (target == Object.class) {
                    return null;
                }
                return createSimpleConverter(lookup, source, target);
            }
        });
        try {
            // Convert enums to String
            MethodHandle enumName = lookup.findVirtual(Enum.class, "name", methodType(String.class));
            loc = loc.register(enumName);

            // Convert Date/long
            MethodHandle dateConstructor = lookup.findConstructor(Date.class, methodType(void.class, long.class));
            loc = loc.register(dateConstructor);
            MethodHandle getTime = lookup.findVirtual(Date.class, "getTime", methodType(long.class));
            loc = loc.register(getTime);

            // Convert java.sql tytpes to Date
            loc = loc.register(MethodHandles.filterArguments(dateConstructor, 0, getTime.asType(
                    methodType(long.class, Timestamp.class))));
            loc = loc.register(MethodHandles.filterArguments(dateConstructor, 0, getTime.asType(
                    methodType(long.class, Time.class))));
            loc = loc.register(MethodHandles.filterArguments(dateConstructor, 0, getTime.asType(
                    methodType(long.class, java.sql.Date.class))));
        } catch (NoSuchMethodException | IllegalAccessException ex) {
            throw new AssertionError("Date", ex);
        }

        // Convert from boolean
        loc = loc.register(lookup, new Object() {
            @Converter
            @SuppressWarnings("unused")
            private char booleanToChar(boolean value) {
                return value ? 't' : 'f';
            }

            @Converter
            @SuppressWarnings("unused")
            private String booleanToString(boolean value) {
                return value ? "t" : "f";
            }
        });
        loc = loc.register(getFirstChar); // String to char
        loc = loc.register(charToBool); // char to boolean
        loc = loc.register(stringToBool);
        defaultConverters = loc;
    }

    /**
     * Finds a converter from the source to the target.
     * If this is not convertable, an exception will be thrown.
     *
     * @param loc    The locator
     * @param source from here..
     * @param target ... to here
     * @return The converting handle, or null of the types match anyway
     */
    public static MethodHandle findStrict(ConverterLocator loc, Class<?> source, Class<?> target) {
        MethodHandle converter = loc.find(source, target);
        if (converter != null) {
            return converter;
        }
        ConversionRank rank = compare(target, source);
        if (rank == ConversionRank.IMPOSSIBLE) {
            throw new NoMatchingConverterException("No converter from " + source.getName() + " to " + target.getName());
        }

        return null;
    }

    /**
     * Converts a single value to a target type by finding a converter from the value's class to the destination type
     * and calling it.
     *
     * @param loc   The locator to use
     * @param value The value to convert
     * @param type  The target type
     * @return The converted value
     */
    public static <T> T convertValue(ConverterLocator loc, Object value, Class<T> type) {
        if (value == null) {
            return null;
        }
        Class<T> wrapped = Types.toWrapper(type);
        if (wrapped.equals(value.getClass())) {
            return wrapped.cast(value);
        }
        MethodHandle converter = findStrict(loc, value.getClass(), type);
        if (converter == null) {
            return wrapped.cast(value);
        }
        try {
            return wrapped.cast(converter.invoke(value));
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable t) {
            throw new RuntimeException("Cannot convert " + value + " to " + type.getName(), t);
        }
    }

    /**
     * Finds a converting handle that accepts the input type and returns the target.
     * <p/>
     * In contrast to ConverterLocator.find(), this never returns null, and the result is already
     * casted to the exact types.
     * <p/>
     *
     * @param loc    The locator
     * @param source From here...
     * @param target ... to here
     * @return (&lt;source&gt;)&lt;target&gt;
     */
    public static MethodHandle findConverter(ConverterLocator loc, Class<?> source, Class<?> target) {
        MethodHandle c = findStrict(loc, source, target);
        if (c == null) {
            c = MethodHandles.identity(target);
        }
        return MethodHandles.explicitCastArguments(c, methodType(target, source));
    }

    /**
     * Creates a handle with a converter that converts the return type to the new one.
     *
     * @param target     This will be called
     * @param loc        The locator to use
     * @param returnType The new return type
     * @return A {@link MethodHandle} of the same type as target except that the return type
     * is of the respected parameter
     */
    public static MethodHandle convertReturnTypeTo(MethodHandle target, ConverterLocator loc,
                                                   Class<?> returnType) {
        MethodType type = target.type();
        Class<?> r = type.returnType();
        if (r.equals(returnType)) {
            return target;
        }
        if (returnType == void.class) {
            return target.asType(type.changeReturnType(void.class));
        }
        MethodHandle converter = findStrict(loc, r, returnType);
        if (converter == null) {
            return MethodHandles.explicitCastArguments(target, type.changeReturnType(returnType));
        }
        converter = MethodHandles.explicitCastArguments(converter, methodType(returnType, r));
        return MethodHandles.filterReturnValue(target, converter);
    }

    /**
     * Returns a {@link MethodHandle} that executes the given target, but first converts all parameters
     * starting from argumentNumber with the ones given in the inputTypes.
     * <p>
     * The returned MethodHandle will have the same type as target except that it accepts the given
     * inputTypes as parameters starting from argumentNumber as a replacement for the original ones.
     *
     * @param target         The target handle
     * @param loc            Use this for conversion
     * @param argumentNumber Start converting from here
     * @param inputTypes     Accept these types (must be convertable to the original types)
     * @return The handle which accepts different parameters
     */
    public static MethodHandle convertArgumentTypesTo(MethodHandle target, ConverterLocator loc,
                                                      int argumentNumber, Class<?>... inputTypes) {
        int n = inputTypes.length;
        MethodType type = target.type();
        if (type.parameterCount() < argumentNumber + n) {
            throw new IllegalArgumentException(target + " does not accept " + (argumentNumber + n) + " parameters.");
        }
        if (n == 0) {
            return target;
        }
        MethodHandle[] filters = new MethodHandle[n];
        MethodType casted = null;
        for (int i = 0; i < n; i++) {
            Class<?> arg = type.parameterType(argumentNumber + i);
            Class<?> newType = inputTypes[i];
            MethodHandle converter = findStrict(loc, newType, arg);
            if (converter == null) {
                if (casted == null) {
                    casted = type;
                }
                casted = casted.changeParameterType(argumentNumber + i, newType);
            }

            if (converter != null) {
                converter = MethodHandles.explicitCastArguments(converter, methodType(arg, newType));
            }
            filters[i] = converter;
        }
        MethodHandle castedTarget = casted == null ? target : MethodHandles.explicitCastArguments(target, casted);
        return MethodHandles.filterArguments(castedTarget, argumentNumber, filters);
    }

    /**
     * Converts the arguments and return type of the given target handle so that it will match the given type.
     * <p/>
     * The resulting handle will be of the given type. All arguments and the return value are being converted
     * via the given {@link ConverterLocator}.
     * <p/>
     * The number of arguments of the given handle and the expected type don't need to match.
     * If the expected type has more arguments than the given target handle, the redundant parameters will just
     * be dropped.
     * If the expected type has less arguments, then the missing parameters will be filled with constant
     * values specified in the additionalValues parameter.
     *
     * @param target           This will be executed
     * @param loc              The converter locator
     * @param type             This is the new type of the returned handle
     * @param additionalValues These are only used if the new type expects less arguments than the target handle.
     *                         For every missing parameter, one value of these is converted, if necessary,
     *                         and then added in the constant pool of the resulting handle.
     * @return A handle of the same type asa specified
     * @throws NoMatchingConverterException      If one of the arguments or return type can't be converted
     * @throws TooManyArgumentsExpectedException If the target handle expects more arguments than the new type,
     *                                           but there are not enough additional values given as constant replacements
     */
    public static MethodHandle convertTo(MethodHandle target, ConverterLocator loc,
                                         MethodType type, Object... additionalValues) {

        MethodHandle casted = convertReturnTypeTo(target, loc, type.returnType());
        MethodType t = target.type();
        int expectedParameterSize = type.parameterCount();
        int actualParameterSize = t.parameterCount();
        int argSize = Math.min(actualParameterSize, expectedParameterSize);
        MethodHandle result;
        if (argSize == 0) {
            result = casted;
        } else {
            MethodHandle[] converters = new MethodHandle[argSize];
            for (int i = 0; i < argSize; i++) {
                Class<?> sourceType = type.parameterType(i);
                Class<?> targetType = t.parameterType(i);
                MethodHandle c = findStrict(loc, sourceType, targetType);
                if (c != null) {
                    c = MethodHandles.explicitCastArguments(c, methodType(targetType, sourceType));
                }
                converters[i] = c;
            }
            result = MethodHandles.filterArguments(casted, 0, converters);
        }
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
                    a = convertValue(loc, a, expectedType);
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
}
