package org.fiolino.common.reflection;

import org.fiolino.common.analyzing.AmbiguousTypesException;
import org.fiolino.common.ioc.Instantiator;
import org.fiolino.common.util.Types;

import javax.annotation.Nullable;
import java.lang.invoke.*;
import java.lang.reflect.*;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.function.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.lang.invoke.MethodHandles.*;
import static java.lang.invoke.MethodType.methodType;

/**
 * Box of several arbitary utility methods around MethodHandles.
 * <p>
 * Created by Michael Kuhlmann on 14.12.2015.
 */
public class Methods {

    private static final Logger logger = Logger.getLogger(Methods.class.getName());

    private static final MethodHandle nullCheck;
    private static final MethodHandle notNullCheck;

    static {
        try {
            nullCheck = publicLookup().findStatic(Objects.class, "isNull", methodType(boolean.class, Object.class));
            notNullCheck = publicLookup().findStatic(Objects.class, "nonNull", methodType(boolean.class, Object.class));
        } catch (NoSuchMethodException | IllegalAccessException ex) {
            throw new InternalError("Objects.isNull()", ex);
        }
    }

    /**
     * Returns a method handle that accepts the given type and returns true if this is null.
     *
     * @return (&lt;toCheck&gt;)boolean
     */
    public static MethodHandle nullCheck(Class<?> toCheck) {
        if (toCheck.isPrimitive()) {
            return acceptThese(FALSE, toCheck);
        }
        return nullCheck.asType(methodType(boolean.class, toCheck));
    }

    /**
     * Returns a method handle that accepts the given type and returns true if this is not null.
     *
     * @return (&lt;toCheck&gt;)boolean
     */
    public static MethodHandle notNullCheck(Class<?> toCheck) {
        if (toCheck.isPrimitive()) {
            return acceptThese(TRUE, toCheck);
        }
        return notNullCheck.asType(methodType(boolean.class, toCheck));
    }

    /**
     * Creates a handle that compares two values for equality if it's an Object, or by identity if it's a primitive or enum.
     *
     * @param type The type of the objects to compare
     * @return (&lt;type&gt;,&lt;type&gt;)boolean
     */
    public static MethodHandle equalsComparator(Class<?> type) {
        if (type.isPrimitive()) {
            return getIdentityComparator(type);
        }
        if (type.isEnum()) {
            return getIdentityComparator(Enum.class).asType(methodType(boolean.class, type, type));
        }
        MethodHandle handle;
        try {
            handle = publicLookup().findStatic(Objects.class, "equals", methodType(boolean.class, Object.class, Object.class));
        } catch (NoSuchMethodException | IllegalAccessException ex) {
            throw new AssertionError("Objects.equals(Object,Object)");
        }
        return handle.asType(methodType(boolean.class, type, type));
    }

    private static MethodHandle getIdentityComparator(Class<?> primitiveOrEnum) {
        Lookup lookup = lookup();
        try {
            return lookup.findStatic(lookup.lookupClass(), "isIdentical", methodType(boolean.class, primitiveOrEnum, primitiveOrEnum));
        } catch (NoSuchMethodException | IllegalAccessException ex) {
            throw new AssertionError("boolean isIdentical(" + primitiveOrEnum.getName() + "," + primitiveOrEnum.getName() + ")");
        }
    }

    @SuppressWarnings("unused")
    private static boolean isIdentical(int a, int b) {
        return a == b;
    }

    @SuppressWarnings("unused")
    private static boolean isIdentical(byte a, byte b) {
        return a == b;
    }

    @SuppressWarnings("unused")
    private static boolean isIdentical(short a, short b) {
        return a == b;
    }

    @SuppressWarnings("unused")
    private static boolean isIdentical(long a, long b) {
        return a == b;
    }

    @SuppressWarnings("unused")
    private static boolean isIdentical(double a, double b) {
        return a == b;
    }

    @SuppressWarnings("unused")
    private static boolean isIdentical(float a, float b) {
        return a == b;
    }

    @SuppressWarnings("unused")
    private static boolean isIdentical(char a, char b) {
        return a == b;
    }

    @SuppressWarnings("unused")
    private static boolean isIdentical(Enum<?> a, Enum<?> b) {
        return a == b;
    }

    /**
     * Creates a MethodHandle which accepts a String as the only parameter and returns an enum of type enumType.
     * It already contains a null check: If the input string is null, then the return value will be null as well.
     *
     * @param type The enum type
     * @param specialHandler Can return a special value for some field and its value.
     *                       If it returns null, name() is used.
     * @param <E> The enum
     * @throws IllegalArgumentException If the input string is invalid, i.e. none of the accepted enumeration values
     */
    public static <E extends Enum<E>> MethodHandle convertStringToEnum(Class<E> type, BiFunction<? super Field, ? super E, String> specialHandler) {
        assert type.isEnum();
        Map<String, Object> map = new HashMap<>();
        if (!type.isEnum()) {
            throw new IllegalArgumentException(type.getName() + " should be an enum.");
        }
        Field[] fields = AccessController.doPrivileged((PrivilegedAction<Field[]>) type::getFields);
        boolean isSpecial = false;

        for (java.lang.reflect.Field f : fields) {
            int m = f.getModifiers();
            if (!Modifier.isStatic(m) || !Modifier.isPublic(m)) {
                continue;
            }
            if (!type.isAssignableFrom(f.getType())) {
                continue;
            }
            E v;
            try {
                v = type.cast(f.get(null));
            } catch (IllegalAccessException ex) {
                throw new AssertionError("Cannot access " + f, ex);
            }
            String value = specialHandler.apply(f, v);
            if (value == null) {
                // The default handling.
                put(map, v.name(), v);
                continue;
            }
            isSpecial = true;
            put(map, value, v);
        }
        if (!isSpecial) {
            // No special handling
            return convertStringToNormalEnum(type);
        }
        // There are annotations, bind to Map.get() method
        MethodHandle getFromMap;
        Lookup lookup = lookup();
        try {
            getFromMap = lookup.findStatic(lookup.lookupClass(), "getIfNotNull", methodType(Object.class, Map.class, String.class));
        } catch (NoSuchMethodException | IllegalAccessException ex) {
            throw new InternalError("getIfNotNull", ex);
        }
        getFromMap = getFromMap.bindTo(map);
        return getFromMap.asType(methodType(type, String.class));
    }

    @SuppressWarnings("unused")
    private static Object getIfNotNull(Map<String, Object> map, String key) {
        if (key == null) return null;
        Object result = map.get(key);
        if (result == null) throw new IllegalArgumentException(key);
        return result;
    }

    private static MethodHandle convertStringToNormalEnum(Class<?> enumType) {
        Lookup lookup = publicLookup().in(enumType);
        MethodHandle valueOf;
        try {
            valueOf = lookup.findStatic(enumType, "valueOf", methodType(enumType, String.class));
        } catch (NoSuchMethodException | IllegalAccessException ex) {
            throw new InternalError("No valueOf in " + enumType.getName() + "?", ex);
        }

        return secureNull(valueOf);
    }

    private static void put(Map<String, Object> map, String key, Object value) {
        if (map.containsKey(key)) {
            logger.log(Level.WARNING, () -> "Key " + key + " was already defined for " + map.get(key));
            return;
        }
        map.put(key, value);
    }

    /**
     * Creates a handle that converts some enum to a String.
     * Instead of just calling name(), it is checked whether some fields should be treated specifically; in that case,
     * those values will be used.
     *
     * An example would be to check whether some enum field is annotated somehow.
     *
     * @param type The enum type
     * @param specialHandler Can return a special value for some field and its value.
     *                       If it returns null, name() is used. If it returns an empty String, null will be returned.
     * @param <E> The enum
     * @return (E)String
     */
    public static <E extends Enum<E>> MethodHandle convertEnumToString(Class<E> type, BiFunction<? super Field, ? super E, String> specialHandler) {
        Map<E, String> map = new EnumMap<>(type);
        if (!type.isEnum()) {
            throw new IllegalArgumentException(type.getName() + " should be an enum.");
        }
        boolean isSpecial = false;
        Field[] fields = AccessController.doPrivileged((PrivilegedAction<Field[]>) type::getFields);
        for (java.lang.reflect.Field f : fields) {
            int m = f.getModifiers();
            if (!Modifier.isStatic(m) || !Modifier.isPublic(m)) {
                continue;
            }
            if (!type.isAssignableFrom(f.getType())) {
                continue;
            }
            E v;
            try {
                v = type.cast(f.get(null));
            } catch (IllegalAccessException ex) {
                throw new AssertionError("Cannot access " + f, ex);
            }
            String value = specialHandler.apply(f, v);
            if (value == null) {
                // The default handling.
                map.put(v, v.name());
                continue;
            }
            isSpecial = true;
            map.put(v, value.isEmpty() ? null : value);
        }
        if (isSpecial) {
            MethodHandle getFromMap;
            try {
                getFromMap = publicLookup().bind(map, "get", methodType(Object.class, Object.class));
            } catch (NoSuchMethodException | IllegalAccessException ex) {
                throw new InternalError("Map::get", ex);
            }
            return getFromMap.asType(methodType(String.class, type));
        } else {
            // No special handling
            try {
                return publicLookup().findVirtual(type, "name", methodType(String.class));
            } catch (NoSuchMethodException | IllegalAccessException ex) {
                throw new InternalError(type.getName() + "::name", ex);
            }
        }
    }

    private static MethodHandle exceptionHandlerCaller(ExceptionHandler handler) {
        return MethodLocator.findUsing(ExceptionHandler.class, h -> h.handle(null, null)).bindTo(handler);
    }

    /**
     * Wraps the given method handle by an exception handler that is called in case.
     *
     * The given exception handler gets an array of objects, including all static injection parameters plus all parameters
     * of the failed target handle call.
     *
     * So if 0..k are the indexes of the injection parameters, and 0..n are the indexes of the target's parameters of the
     * failed call, then you can refer to these values in the exception handler via parameters [0]..[k[ for the injection values,
     * and [k+1]..[k+n+1] for the target call's parameters.
     *
     * @param target           The method handle to wrap.
     * @param catchedExceptionType Which type shall be catched.
     * @param exceptionHandler The handler.
     * @param injections       These will be the first static values of the given parameter array in the exception handler.
     *                         Each one can will be called lazily
     * @param <E>              The exception type
     * @return A method handle of the same type as the target, which won't throw E but call the handler instead.
     */
    public static <E extends Throwable> MethodHandle wrapWithExceptionHandler(MethodHandle target, Class<E> catchedExceptionType,
                                                                              ExceptionHandler<? super E> exceptionHandler,
                                                                              Supplier<?>... injections) {

        return wrapWithExceptionHandler(target, catchedExceptionType, exceptionHandlerCaller(exceptionHandler),
                (Object[]) injections);
    }

    /**
     * Wraps the given method handle by an exception handler that is called in case.
     *
     * The given exception handler gets an array of objects, including all static injection parameters plus all parameters
     * of the failed target handle call.
     *
     * So if 0..k are the indexes of the injection parameters, and 0..n are the indexes of the target's parameters of the
     * failed call, then you can refer to these values in the exception handler via parameters [0]..[k[ for the injection values,
     * and [k+1]..[k+n+1] for the target call's parameters.
     *
     * @param target           The method handle to wrap.
     * @param catchedExceptionType Which type shall be catched.
     * @param exceptionHandler The handler.
     * @param injections       These will be the first static values of the given parameter array in the exception handler.
     *                         Each one can be any object, which is inserted directly, or a {@link Supplier}, which will be called lazily
     * @param <E>              The exception type
     * @return A method handle of the same type as the target, which won't throw E but call the handler instead.
     */
    public static <E extends Throwable> MethodHandle wrapWithExceptionHandler(MethodHandle target, Class<E> catchedExceptionType,
                                                                              ExceptionHandler<? super E> exceptionHandler,
                                                                              Object... injections) {

        return wrapWithExceptionHandler(target, catchedExceptionType, exceptionHandlerCaller(exceptionHandler), injections);
    }

    /**
     * Wraps the given method handle by an exception handler that is called in case. The exception handle gets the catched
     * exception and an Object array of the injections and all called parameter values.
     *
     * @param target           The method handle to wrap.
     * @param catchedExceptionType Which type shall be catched.
     * @param exceptionHandler The handler: (&lt;? super catchedExceptionType&gt;,Object[])&lt;? super target return type&gt;
     * @param injections       These will be the first static values of the given parameter array in the exception handler.
     *                         Each one can be any object, which is inserted directly, or a {@link Supplier}, which will be called lazily
     * @return A method handle of the same type as the target, which won't throw the exception but call the handler instead.
     */
    public static MethodHandle wrapWithExceptionHandler(MethodHandle target, Class<? extends Throwable> catchedExceptionType,
                                                        MethodHandle exceptionHandler, Object... injections) {
        MethodType targetType = target.type();
        MethodHandle handlerHandle = exceptionHandler.asCollector(Object[].class, injections.length + targetType.parameterCount());
        handlerHandle = insertArgumentsOrSuppliers(handlerHandle, 1, injections);
        handlerHandle = changeNullSafeReturnType(handlerHandle, targetType.returnType());
        handlerHandle = handlerHandle.asType(targetType.insertParameterTypes(0, catchedExceptionType));

        return catchException(target, catchedExceptionType, handlerHandle);
    }

    private static MethodHandle insertArgumentsOrSuppliers(MethodHandle handle, int pos, Object[] injections) {
        for (Object i : injections) {
            if (i instanceof Supplier) {
                return insertSuppliers(handle, pos, injections);
            }
        }
        return insertArguments(handle, pos, injections);
    }

    private static MethodHandle insertSuppliers(MethodHandle handle, int pos, Object[] injections) {
        MethodHandle h = handle;
        for (Object i : injections) {
            if (i instanceof Supplier) {
                MethodHandle get = MethodLocator.findUsing(Supplier.class, Supplier::get).bindTo(i);
                h = collectArguments(h, pos, get);
            } else {
                h = insertArguments(h, pos, i);
            }
        }

        return h;
    }

    static void checkArgumentLength(MethodType targetType, int actual) {
        checkArgumentLength(targetType, 0, actual);
    }

    private static void checkArgumentLength(MethodType targetType, int min, int actual) {
        checkArgumentLength(min, targetType.parameterCount(), actual);
    }

    private static void checkArgumentLength(int min, int max, int actual) {
        if (actual < min) {
            throw new IllegalArgumentException(actual + " is less than " + min);
        }
        if (actual >= max) {
            throw new IllegalArgumentException("Index " + actual + " is out of range: Target has only "
                    + max + " arguments.");
        }
    }

    /**
     * Returns the given target with a different return type.
     * Returning null in the target handle will be safe even if the new return type is a primitive, which would fail
     * in case of a normal asType() conversion. So it is safe to return null from target in all cases.
     *
     * The returned value will be zero-like if null was originally returned.
     *
     * @param target The handle to call
     * @param returnType The new return type
     * @return A handle of the same type as the target type, modified to the new return type
     */
    public static MethodHandle changeNullSafeReturnType(MethodHandle target, Class<?> returnType) {
        MethodType targetType = target.type();
        Class<?> existingType = targetType.returnType();
        if (existingType.equals(returnType)) return target;
        if (existingType == void.class) {
            return returnEmptyValue(target, returnType);
        }
        if (!returnType.isPrimitive() || returnType == void.class || existingType.isPrimitive()) {
            return target.asType(targetType.changeReturnType(returnType));
        }

        MethodHandle caseNull = empty(methodType(returnType, existingType));
        MethodHandle caseNotNull = identity(existingType).asType(methodType(returnType, existingType));
        MethodHandle checkReturnValueForNull = guardWithTest(nullCheck(existingType), caseNull, caseNotNull);

        return filterReturnValue(target, checkReturnValueForNull);
    }

    /**
     * Converts the type of the target handle to accept all given parameters.
     *
     * Let's be n the target type's parameter count, and k the length of the expected parameter types,
     * then n&lt;=k, and the resulting handle will accept all k given parameters, where all parameters at index
     * i &gt; n are being skipped.
     *
     * @param target This will be executed
     * @param expectedParameterTypes Describes the returning handle's parameter types; the return type stays untouched
     * @return A handle that calls target but drops all trailing arguments if expectedParameterTypes are more than in target
     */
    public static MethodHandle acceptThese(MethodHandle target, Class<?>... expectedParameterTypes) {
        int n = target.type().parameterCount();
        MethodType resultType = methodType(target.type().returnType(), expectedParameterTypes);
        int k = expectedParameterTypes.length;
        if (n == k) {
            return explicitCastArguments(target, resultType);
        }
        if (n > k) {
            throw new IllegalArgumentException(target + " has less parameters than " + Arrays.toString(expectedParameterTypes));
        }
        Class<?>[] parameterTypes;
        if (n == 0) {
            parameterTypes = expectedParameterTypes;
        } else {
            parameterTypes = new Class<?>[k - n];
            System.arraycopy(expectedParameterTypes, n, parameterTypes, 0, parameterTypes.length);
        }
        return explicitCastArguments(dropArguments(target, n, parameterTypes), resultType);
    }

    /**
     * Modifies the given target handle so that it will return a value of the given type which will always be null or zero.
     *
     * @param target Some target to execute
     * @param returnType The type to return
     * @return A handle with the same arguments as the target, but which returns a null value, or a zero-like value if returnType is a primitive
     */
    public static MethodHandle returnEmptyValue(MethodHandle target, Class<?> returnType) {
        if (returnType == void.class) {
            return target.asType(target.type().changeReturnType(void.class));
        }
        Class<?> r = target.type().returnType();
        MethodHandle returnHandle = r == void.class ? zero(returnType) : empty(methodType(returnType, r));
        return filterReturnValue(target, returnHandle);
    }

    /**
     * A handle that checks the given argument (which is of type Object) for being an instance of the given type.
     *
     * @param check The type to check
     * @return A handle of type (Object)boolean
     */
    public static MethodHandle instanceCheck(Class<?> check) {
        if (check.isPrimitive()) {
            return dropArguments(FALSE, 0, Object.class);
        }
        if (Object.class.equals(check)) {
            return notNullCheck;
        }
        try {
            return publicLookup().in(check).bind(check, "isInstance", methodType(boolean.class, Object.class));
        } catch (NoSuchMethodException | IllegalAccessException ex) {
            throw new InternalError("No isInstance for " + check.getName() + "?");
        }

    }

    public static final MethodHandle FALSE = constant(boolean.class, false);
    public static final MethodHandle TRUE = constant(boolean.class, true);

    /**
     * Combines a list of conditions by and.
     * Given is an array of MethodHandle instances that return boolean, and whose parameter types are identical
     * except that leading handles may have fewer types than following handles.
     * <p>
     * The returned handle will accept the maximum number of input parameters, which is the parameters of the last
     * given handle. If no handle was given at all, then a MethodHandle returning a constant TRUE value without any
     * incoming parameters, is returned.
     * <p>
     * Each handle starting from the second one is invoked only if all previous handles returned true.
     */
    public static MethodHandle and(MethodHandle... handles) {
        if (handles.length == 0) {
            return TRUE;
        }
        return and(handles, 0);
    }

    private static MethodHandle and(MethodHandle[] handles, int start) {
        if (start == handles.length - 1) {
            return handles[start];
        }
        MethodHandle remaining = and(handles, start + 1);
        return guardWithTest(handles[start], remaining, acceptThese(FALSE, remaining.type().parameterArray()));
    }

    /**
     * Combines a list of conditions by or.
     * Given is an array of MethodHandle instances that return boolean, and whose parameter types are identical
     * except that leading handles may have fewer types than following handles.
     * <p>
     * The returned handle will accept the maximum number of input parameters, which is the parameters of the last
     * given handle. If no handle was given at all, then a MethodHandle returning a constant FALSE value without any
     * incoming parameters, is returned.
     * <p>
     * Each handle starting from the second one is invoked only if all previous handles returned false.
     */
    public static MethodHandle or(MethodHandle... handles) {
        if (handles.length == 0) {
            return FALSE;
        }
        return or(handles, 0);
    }

    private static MethodHandle or(MethodHandle[] handles, int start) {
        if (start == handles.length - 1) {
            return handles[start];
        }
        MethodHandle remaining = or(handles, start + 1);
        return guardWithTest(handles[start], acceptThese(TRUE, remaining.type().parameterArray()), remaining);
    }

    /**
     * Secures a given MethodHandle so that it will only invoked if none of its parameters is null.
     * The returned MethodHandle is of the same type as the given target. If the return type is an Object,
     * then the return value is null if any input value was null. If the return type is a primitive, then
     * the return value will be 0 or false in that case. If the target handle is of type void, the it will
     * simply not being called at all if there is a null value.
     */
    public static MethodHandle secureNull(MethodHandle target) {
        return secureNull(target, i -> true);
    }

    /**
     * Secures a given MethodHandle so that it will only invoked if none of its specified parameters is null.
     * The returned MethodHandle is of the same type as the given target. If the return type is an Object,
     * then the return value is null if any input value was null. If the return type is a primitive, then
     * the return value will be 0 or false in that case. If the target handle is of type void, the it will
     * simply not being called at all if there is a null value.
     */
    public static MethodHandle secureNull(MethodHandle target, int... argumentIndexes) {
        return secureNull(target, argumentTester(target.type(), argumentIndexes));
    }

    private static IntPredicate argumentTester(MethodType type, int... argumentIndexes) {
        switch (argumentIndexes.length) {
            case 0:
                return i -> true;
            case 1:
                int index = argumentIndexes[0];
                checkArgumentLength(type, index);
                return i -> i == index;
            default:
                BitSet toCheck = new BitSet(type.parameterCount());
                for (int i : argumentIndexes) {
                    checkArgumentLength(type, i);
                    toCheck.set(i);
                }
                return toCheck::get;
        }
    }

    private static MethodHandle secureNull(MethodHandle target, IntPredicate toCheck) {
        return checkForNullValues(target, toCheck, Methods::rejectIf);
    }

    /**
     * Creates a handle that executes the given target, but validates the given arguments before by checking for null.
     * The handle will throw the given exception if some of these values is null.
     *
     * @param target The target to execute; all specified arguments are guaranteed to be non-null
     * @param exceptionTypeToThrow This exception will be thrown. Its public constructor must accept the message as the single argument
     * @param message This is the message in the new exception
     * @param argumentIndexes The arguments to check. If none is given, then all arguments are being checked
     * @return The null-safe handle
     */
    public static MethodHandle assertNotNull(MethodHandle target, Class<? extends Throwable> exceptionTypeToThrow,
                                             String message, int... argumentIndexes) {
        MethodType type = target.type();
        Lookup l = publicLookup().in(exceptionTypeToThrow);
        MethodHandle exceptionConstructor;
        try {
            exceptionConstructor = l.findConstructor(exceptionTypeToThrow, methodType(void.class, String.class));
        } catch (NoSuchMethodException | IllegalAccessException ex) {
            throw new IllegalArgumentException(exceptionTypeToThrow.getName() + " should have a public constructor accepting a String", ex);
        }
        exceptionConstructor = exceptionConstructor.bindTo(message);
        MethodHandle throwException = throwException(type.returnType(), exceptionTypeToThrow);
        throwException = collectArguments(throwException, 0, exceptionConstructor);
        MethodHandle nullCase  = dropArguments(throwException, 0, type.parameterArray());
        return checkForNullValues(target, argumentTester(type, argumentIndexes), (t, g) -> guardWithTest(g, nullCase, t));
    }

    /**
     * Creates a handle that executes the given target, but validates the given argument before by checking for null.
     * The handle will throw a null pointer exception if the parameter is null.
     *
     * @param target The target to execute; all specified arguments are guaranteed to be non-null
     * @param argument The argument to check
     * @param argumentNames This is used as a message in the exception. Use null values for parameters that shall not be checked.
     * @return The null-safe handle
     */
    public static MethodHandle assertNotNull(MethodHandle target, int argument, String... argumentNames) {
        if (argumentNames.length == 0) {
            throw new IllegalArgumentException("No names given");
        }
        MethodHandle h = target;
        int i = argument;
        for (String n : argumentNames) {
            if (n == null) {
                i++;
                continue;
            }
            h = assertNotNull(h, NullPointerException.class, n + " must not be null", i++);
        }
        return h;
    }

    private static MethodHandle checkForNullValues(MethodHandle target, IntPredicate toCheck, BinaryOperator<MethodHandle> resultBuilder) {
        MethodType targetType = target.type();
        Class<?>[] parameterArray = targetType.parameterArray();
        MethodHandle[] checks = new MethodHandle[parameterArray.length];
        int checkCount = 0;
        MethodType nullCheckType = targetType.changeReturnType(boolean.class);
        int i = 0;
        for (Class<?> p : parameterArray) {
            if (!toCheck.test(i++) || p.isPrimitive()) {
                continue;
            }
            MethodHandle nullCheck = nullCheck(p);
            MethodHandle checkArgI = permuteArguments(nullCheck, nullCheckType, i - 1);
            checks[checkCount++] = checkArgI;
        }
        if (checkCount == 0) {
            // All parameters were primitives
            return target;
        }
        if (checkCount < i) {
            // Not all parameters need to get checked
            checks = Arrays.copyOf(checks, checkCount);
        }
        MethodHandle checkNull = or(checks);
        return resultBuilder.apply(target, checkNull);
    }

    /**
     * A handle that accepts nothing, returns nothing, and does nothing.
     */
    public static final MethodHandle DO_NOTHING = constant(Void.class, null).asType(methodType(void.class));

    /**
     * Creates a method handle that invokes its target only if the guard returns false.
     * Guard and target must have the same argument types. Guard must return a boolean,
     * while the target may return any type.
     * <p>
     * The resulting handle has exactly the same type, and if it returns a value, it will return null
     * or a zero-like value if the guard returned false.
     */
    public static MethodHandle rejectIf(MethodHandle target, MethodHandle guard) {
        return guardWithTest(guard, empty(target.type()), target);
    }

    /**
     * Creates a method handle that invokes its target only if the guard returns true.
     * Guard and target must have the same argument types. Guard must return a boolean,
     * while the target may return any type.
     * <p>
     * The resulting handle has exactly the same type, and if it returns a value, it will return null
     * or a zero-like value if the guard returned false.
     */
    public static MethodHandle invokeOnlyIf(MethodHandle target, MethodHandle guard) {
        return guardWithTest(guard, target, empty(target.type()));
    }

    /**
     * Creates a method handle that doesn't execute its target if at least one of the guards returns true.
     * <p>
     * The guard must accept exactly one argument with the type of the given argument of the target, and return a
     * boolean value.
     * <p>
     * The resulting handle has the same type as the target, and returns a null value or zero if the guard doesn't
     * allow to execute.
     *
     * @param target The executed handle
     * @param argumentNumber The starting argument number to check
     * @param guards The guards; may contain null values for arguments not being checked
     * @return A handle of the same type as target
     */
    public static MethodHandle rejectIfArgument(MethodHandle target, int argumentNumber,
                                                MethodHandle... guards) {

        return doOnArguments(target, argumentNumber, Methods::rejectIf, guards);
    }

    /**
     * Creates a method handle that executes its target only if all the guards return true.
     * <p>
     * This methods is just the exact opposite to rejectIfArgument().
     * <p>
     * The guard must accept exactly one argument with the type of the given argument of the target, and return a
     * boolean value.
     * <p>
     * The resulting handle has the same type as the target, and returns a null value or zero if the guard doesn't
     * allow to execute.
     *
     * @param target The executed handle
     * @param argumentNumber The starting argument number to check
     * @param guards The guards; may contain null values for arguments not being checked
     * @return A handle of the same type as target
     */
    public static MethodHandle invokeOnlyIfArgument(MethodHandle target, int argumentNumber,
                                                    MethodHandle... guards) {

        return doOnArguments(target, argumentNumber, Methods::invokeOnlyIf, guards);
    }

    private static MethodHandle doOnArguments(MethodHandle target, int argumentNumber, BinaryOperator<MethodHandle> action,
                                              MethodHandle... guards) {

        int n = guards.length;
        if (n == 0) {
            return target;
        }
        MethodType targetType = target.type();
        checkArgumentLength(0, targetType.parameterCount() - n + 1, argumentNumber);

        MethodHandle handle = target;
        int a = argumentNumber;
        for (MethodHandle g : guards) {
            if (g == null) {
                a++;
                continue;
            }
            MethodHandle argumentGuard = argumentGuard(g, targetType, a++);

            handle = action.apply(handle, argumentGuard);
        }
        return handle;
    }

    private static MethodHandle argumentGuard(MethodHandle guard, MethodType targetType, int argumentNumber) {
        MethodHandle argumentGuard = guard.asType(guard.type().changeParameterType(0, targetType.parameterType(argumentNumber)));
        return moveSingleArgumentTo(argumentGuard, targetType, argumentNumber);
    }

    /**
     * Creates a method handle that executes its target, and then returns the argument at the given index.
     * <p>
     * The resulting handle will have the same type as the target except that the return type is the same as the
     * one of the given argument.
     * <p>
     * If the target returns a value, then this simply is discarded.
     */
    public static MethodHandle returnArgument(MethodHandle target, int argumentNumber) {

        MethodType targetType = target.type();
        checkArgumentLength(targetType, argumentNumber);
        MethodHandle dontReturnAnything = target.asType(targetType.changeReturnType(void.class));
        Class<?> parameterType = targetType.parameterType(argumentNumber);
        MethodHandle identityOfPos = identity(parameterType);
        if (targetType.parameterCount() > 1) {
            identityOfPos = permuteArguments(identityOfPos, targetType.changeReturnType(parameterType), argumentNumber);
        }
        return foldArguments(identityOfPos, dontReturnAnything);
    }

    public static MethodHandle permute(MethodHandle target, MethodType type) {
        MethodType targetType = target.type();
        int parameterCount = targetType.parameterCount();
        int[] indexes = new int[parameterCount];
        Class<?>[] newParameterTypes = new Class<?>[parameterCount];
        for (int i = 0; i < parameterCount; i++) {
            Class<?> p = targetType.parameterType(i);
            int pos = findBestMatchingParameter(type, p, i);
            indexes[i] = pos;
            Class<?> original = type.parameterType(pos);
            newParameterTypes[i] = original;
        }

        return permuteArguments(target.asType(methodType(type.returnType(), newParameterTypes)), type, indexes);
    }

    private static int findBestMatchingParameter(MethodType type, Class<?> lookFor, int pos) {
        int match = -1;
        int perfectMatch = -1;
        for (int i = 0; i < type.parameterCount(); i++) {
            Class<?> p = type.parameterType(i);
            if (p.equals(lookFor)) {
                if (i == pos) {
                    return i;
                }
                if (perfectMatch >= 0) {
                    throw new AmbiguousTypesException("Type " + lookFor.getName() + " is appearing twice in " + type);
                }
                perfectMatch = i;
                match = i;
            } else if (Types.isAssignableFrom(lookFor, p)) {
                if (i != pos && match >= 0) {
                    throw new AmbiguousTypesException("Type " + lookFor.getName() + " is matching twice in " + type);
                }
                match = i;
            }
        }

        if (match < 0) {
            throw new IllegalArgumentException(type + " does not contain parameter of type " + lookFor.getName());
        }
        return match;
    }

    /**
     * Compares an expected method type (the reference) with some other method type to check.
     *
     * @param reference The expected type
     * @param toCheck   This is checked
     * @return The comparison (@see {@link Comparison})
     */
    public static Comparison compare(MethodType reference, MethodType toCheck) {
        return compare(reference.returnType(), toCheck.returnType(),
                reference.parameterArray(), toCheck.parameterArray());
    }

    /**
     * Compares an expected method type (the reference) with some other method to check.
     *
     * @param reference The expected type
     * @param toCheck   This is checked
     * @return The comparison (@see {@link Comparison})
     */
    public static Comparison compare(MethodType reference, Method toCheck) {
        return compare(reference.returnType(), toCheck.getReturnType(),
                reference.parameterArray(), toCheck.getParameterTypes());
    }

    private static Comparison compare(Class<?> expectedReturnType, Class<?> checkedReturnType,
                                      Class<?>[] expectedArguments, Class<?>[] checkedArguments) {
        int expectedArgumentCount = expectedArguments.length;
        int checkedArgumentCount = checkedArguments.length;
        if (expectedArgumentCount > checkedArgumentCount) {
            return Comparison.LESS_ARGUMENTS;
        }
        if (expectedArgumentCount < checkedArgumentCount) {
            return Comparison.MORE_ARGUMENTS;
        }

        Comparison comparison;
        if (checkedReturnType.equals(expectedReturnType)) {
            comparison = Comparison.EQUAL;
        } else if (expectedReturnType == void.class || checkedReturnType == void.class) {
            return Comparison.INCOMPATIBLE;
        } else if (Types.isAssignableFrom(expectedReturnType, checkedReturnType) && !expectedReturnType.isPrimitive()) {
            comparison = Comparison.MORE_GENERIC;
        } else if (Types.isAssignableFrom(checkedReturnType, expectedReturnType)) {
            comparison = Comparison.MORE_SPECIFIC;
        } else {
            return Comparison.INCOMPATIBLE;
        }

        for (int i = 0; i < expectedArgumentCount; i++) {
            Class<?> expectedArgument = expectedArguments[i];
            Class<?> checkedArgument = checkedArguments[i];
            if (expectedArgument.equals(checkedArgument)) {
                continue;
            }
            if (Types.isAssignableFrom(expectedArgument, checkedArgument) && !expectedArgument.isPrimitive()) {
                switch (comparison) {
                    case MORE_GENERIC:
                        comparison = Comparison.CONVERTABLE;
                        break;
                    case EQUAL:
                        comparison = Comparison.MORE_SPECIFIC;
                }
            } else if (Types.isAssignableFrom(checkedArgument, expectedArgument)) {
                switch (comparison) {
                    case MORE_SPECIFIC:
                        comparison = Comparison.CONVERTABLE;
                        break;
                    case EQUAL:
                        comparison = Comparison.MORE_GENERIC;
                }
            } else {
                return Comparison.INCOMPATIBLE;
            }
        }

        return comparison;
    }

    /**
     * Finds the method handle to the only unique method that fits to the given method type.
     * <p>
     * A method is a unique method if it is the only one with the given type within one class. If there are more
     * in its super classes, then it doesn't matter.
     * <p>
     * A method matches if their arguments are either equal to the given method type, or more generic, or more specific,
     * in that order. A method is unique if it is unique within the best matching comparison. For instance,
     * if there is one method that is more generic, and another one is more specific, then uniqueness still
     * is given and the more generic one is chosen.
     * <p>
     * The object to look up may either be an instance: Then its class type will be searched. If the found method then
     * is static, it will simply be used, otherwise the given instance is bound to it.
     * <p>
     * If the object is a Class, and the found method is an instance method, then an empty constructor is expected
     * and a new instance is created now.
     * <p>
     * As a result, the returned handle will have exactly the given reference method type, without any additional
     * object instance.
     *
     * @param object    The object (Class or some instance) to investigate
     * @param reference The method type to look for
     * @return The found method handle
     * @throws AmbiguousMethodException If there are multiple methods matching the searched type
     * @throws NoSuchMethodError        If no method was found
     */
    public static MethodHandle findMethodHandleOfType(Object object, MethodType reference) {
        return findMethodHandleOfType(null, object, reference);
    }

    /**
     * Finds the method handle to the only unique method that fits to the given method type.
     * <p>
     * A method is a unique method if it is the only one with the given type within one class. If there are more
     * in its super classes, then it doesn't matter.
     * <p>
     * A method matches if their arguments are either equal to the given method type, or more generic, or more specific,
     * in that order. A method is unique if it is unique within the best matching comparison. For instance,
     * if there is one method that is more generic, and another one is more specific, then uniqueness still
     * is given and the more generic one is chosen.
     * <p>
     * The object to look up may either be an instance: Then its class type will be searched. If the found method then
     * is static, it will simply be used, otherwise the given instance is bound to it.
     * <p>
     * If the object is a Class, and the found method is an instance method, then an empty constructor is expected
     * and a new instance is created now.
     * <p>
     * As a result, the returned handle will have exactly the given reference method type, without any additional
     * object instance.
     *
     * @param lookup    The lookup
     * @param object    The object (Class or some instance) to investigate
     * @param reference The method type to look for
     * @return The found method handle
     * @throws AmbiguousMethodException If there are multiple methods matching the searched type
     * @throws NoSuchMethodError        If no method was found
     */
    public static MethodHandle findMethodHandleOfType(@Nullable Lookup lookup, Object object, MethodType reference) {
        MethodHandle handle = findSingleMethodHandle(lookup, object, reference);
        return handle.asType(reference);
    }

    /**
     * Finds the method to the only unique method that fits to the given method type.
     * <p>
     * A method is a unique method if it is the only one with the given type within one class. If there are more
     * in its super classes, then it doesn't matter.
     * <p>
     * A method matches if their arguments are either equal to the given method type, or more generic, or more specific,
     * in that order. A method is unique if it is unique within the best matching comparison. For instance,
     * if there is one method that is more generic, and another one is more specific, then uniqueness still
     * is given and the more generic one is chosen.
     *
     * @param type      The reference type to investigate
     * @param reference The method type to look for
     * @return The found method handle
     * @throws AmbiguousMethodException If there are multiple methods matching the searched type
     * @throws NoSuchMethodError        If no method was found
     */
    public static Method findMethodOfType(Class<?> type, MethodType reference) {
        return findMethodOfType(null, type, reference);
    }

    /**
     * Finds the method to the only unique method that fits to the given method type.
     * <p>
     * A method is a unique method if it is the only one with the given type within one class. If there are more
     * in its super classes, then it doesn't matter.
     * <p>
     * A method matches if their arguments are either equal to the given method type, or more generic, or more specific,
     * in that order. A method is unique if it is unique within the best matching comparison. For instance,
     * if there is one method that is more generic, and another one is more specific, then uniqueness still
     * is given and the more generic one is chosen.
     *
     * @param lookup    The lookup
     * @param type      The reference type to investigate
     * @param reference The method type to look for
     * @return The found method handle
     * @throws AmbiguousMethodException If there are multiple methods matching the searched type
     * @throws NoSuchMethodError        If no method was found
     */
    public static Method findMethodOfType(@Nullable Lookup lookup, Class<?> type, MethodType reference) {
        Method m = findSingleMethodIn(lookup, type, reference);
        if (m == null) {
            throw new NoSuchMethodError("No method " + reference + " in " + type.getName());
        }
        return m;
    }

    private static MethodHandle findSingleMethodHandle(Lookup lookup, Object object, MethodType reference) {
        Class<?> type;
        if (object instanceof Class) {
            type = (Class<?>) object;
        } else {
            if (MethodHandleProxies.isWrapperInstance(object)) {
                return MethodHandleProxies.wrapperInstanceTarget(object);
            }
            type = object.getClass();
        }
        Lookup l = lookup == null ? publicLookup().in(type) : lookup;
        Method m = findMethodOfType(l, type, reference);
        MethodHandle handle;
        try {
            handle = l.unreflect(m);
        } catch (IllegalAccessException ex) {
            throw new AssertionError(m + " should be visible.", ex);
        }
        if (Modifier.isStatic(m.getModifiers())) {
            return handle;
        }
        if (object instanceof Class) {
            // Then inject a new instance now
            object = Instantiator.getDefault().instantiate((Class<?>) object);
        }
        return handle.bindTo(object);
    }

    /**
     * Guards a method handle with a semaphore to assure bounded access to one single thread at a time.
     *
     * On each call to the target handle, a single permit is acquired, and it's released afterwards.
     *
     * The permit is acquired uninterruptibly, meaning that it will wait even if Thread.interrupt() is called.
     * Therefore the caller doesn't need to check for InterruptedExceptions.
     *
     * @param target The target handle to call
     * @return A handle with the same type as the target handle
     */
    public static MethodHandle synchronize(MethodHandle target) {
        return synchronize(target, 1);
    }

    /**
     * Guards a method handle with a semaphore to assure bounded access.
     *
     * On each call to the target handle, a single permit is acquired, and it's released afterwards.
     *
     * The permit is acquired uninterruptibly, meaning that it will wait even if Thread.interrupt() is called.
     * Therefore the caller doesn't need to check for InterruptedExceptions.
     *
     * @param target The target handle to call
     * @param concurrentAccesses How many threads may access this operation concurrently
     * @return A handle with the same type as the target handle
     */
    public static MethodHandle synchronize(MethodHandle target, int concurrentAccesses) {
        return synchronizeWith(target, new Semaphore(concurrentAccesses));
    }

    /**
     * Guards a method handle with a semaphore to assure bounded access.
     *
     * On each call to the target handle, a single permit is acquired, and it's released afterwards.
     *
     * The permit is acquired uninterruptibly, meaning that it will wait even if Thread.interrupt() is called.
     * Therefore the caller doesn't need to check for InterruptedExceptions.
     *
     * @param target The target handle to call
     * @param semaphore This is used to block gthe operation
     * @return A handle with the same type as the target handle
     */
    public static MethodHandle synchronizeWith(MethodHandle target, Semaphore semaphore) {
        MethodType type = target.type();
        MethodHandle acquire, release;
        try {
            acquire = publicLookup().bind(semaphore, "acquireUninterruptibly", methodType(void.class));
            release = publicLookup().bind(semaphore, "release", methodType(void.class));
        } catch (NoSuchMethodException | IllegalAccessException ex) {
            throw new InternalError(ex);
        }
        MethodHandle executeAndRelease = doFinally(target, release);
        MethodHandle synced = foldArguments(executeAndRelease, acquire);

        if (target.isVarargsCollector()) {
            synced = synced.asVarargsCollector(type.parameterType(type.parameterCount() - 1));
        }
        return synced;
    }

    /**
     * Creates a handle that
     * <ol>
     *    <li>executes the target</li>
     *    <li>executes the finallyBlock even if target threw some exception</li>
     *    <li>and then return target's value or finally throws target's exception</li>
     * </ol>
     *
     * @param target The main block which is guarded by a catch clause
     * @param finallyBlock The block to execute after target; will not return a value, and may accept the same or less parameters than the target
     * @return A handle of the exact same type as target
     */
    public static MethodHandle doFinally(MethodHandle target, MethodHandle finallyBlock) {
        MethodHandle cleanup = acceptThese(finallyBlock, target.type().parameterArray());
        Class<?> returnType = target.type().returnType();
        if (returnType == void.class) {
            cleanup = dropArguments(cleanup, 0, Throwable.class);
            cleanup = cleanup.asType(cleanup.type().changeReturnType(void.class));
        } else {
            cleanup = dropArguments(cleanup, 0, Throwable.class, returnType);
            cleanup = returnArgument(cleanup, 1);
        }

        return tryFinally(target, cleanup);
    }

    /**
     * Creates a handle that iterates over some {@link Iterable} instance instead of the original parameter at the given index.
     * Only stateless iterations are supported, and no value is returned.
     *
     * The given target handle will be called for each element in the given Iterable instance. If the target returns
     * some values, it will be ignored.
     *
     * Let (l1, ... lN, aN+1, ... aP, aP+1, aP+2, ... aM)R be the target type, where N is the number of leading arguments,
     * P is the parameter index of the iterated value, M is the number of all target's arguments,
     * and R is the return type.
     *
     * Then the resulting handle will be of type (a1, ... aP-N, Iterable, aP-N+2, ... aM)void.
     *
     * The given parameter index may be smaller than the number of leading arguments. In this case, the index still refers
     * to the target's argument index, and the remaining leading arguments are filled thereafter. The iterable will be
     * at the first position of the resulting handle then.
     *
     * The result will be of variable arity if the original target was already of variable arity, and the iterated
     * parameter is not the last one.
     *
     * Implementation note:
     * The resulting handle will prefer calling forEach() when the target handle can be directly converted to a lambda.
     * This is the case when
     * <ol>
     *     <li>the target is a direct method handle,</li>
     *     <li>it has a public visibility,</li>
     *     <li>it originally returns void,</li>
     *     <li>the iterated value is the target's last parameter,</li>
     *     <li>and it is non-primitive.</li>
     * </ol>
     * In any other case, the default iterator pattern is used.
     *
     * @param target This will be called
     * @param parameterIndex The index of the target's iterated element parameter, starting from 0, not counting the
     *                       leading arguments
     * @param leadingArguments These will be passed to every call of the target as the first arguments
     * @return A handle that iterates over all elements of the given iterable
     */
    public static MethodHandle iterate(MethodHandle target,  int parameterIndex, Object... leadingArguments) {
        return iterate(publicLookup(), target, parameterIndex, leadingArguments);
    }

    /**
     * Creates a handle that iterates over some {@link Iterable} instance instead of the original parameter at the given index.
     * Only stateless iterations are supported, and no value is returned.
     *
     * The given target handle will be called for each element in the given Iterable instance. If the target returns
     * some values, it will be ignored.
     *
     * Let (l1, ... lN, aN+1, ... aP, aP+1, aP+2, ... aM)R be the target type, where N is the number of leading arguments,
     * P is the parameter index of the iterated value, M is the number of all target's arguments,
     * and R is the return type.
     *
     * Then the resulting handle will be of type (a1, ... aP-N, Iterable, aP-N+2, ... aM)void.
     *
     * The given parameter index may be smaller than the number of leading arguments. In this case, the index still refers
     * to the target's argument index, and the remaining leading arguments are filled thereafter. The iterable will be
     * at the first position of the resulting handle then.
     *
     * The result will be of variable arity if the original target was already of variable arity, and the iterated
     * parameter is not the last one.
     *
     * Implementation note:
     * The resulting handle will prefer calling forEach() when the target handle can be directly converted to a lambda.
     * This is the case when
     * <ol>
     *     <li>the target is a direct method handle,</li>
     *     <li>it is visible from the given Lookup instance,</li>
     *     <li>it originally returns void,</li>
     *     <li>the iterated value is the target's last parameter,</li>
     *     <li>and it is non-primitive.</li>
     * </ol>
     * In any other case, the default iterator pattern is used.
     *
     * @param lookup The local lookup; necessary when the target is a direct, private member
     * @param target This will be called
     * @param parameterIndex The index of the target's iterated element parameter, starting from 0, including the
     *                       leading arguments
     * @param leadingArguments These will be passed to every call of the target as the first arguments
     * @return A handle that iterates over all elements of the given iterable
     */
    public static MethodHandle iterate(Lookup lookup, MethodHandle target,  int parameterIndex, Object... leadingArguments) {
        MethodType targetType = target.type();
        int numberOfLeadingArguments = leadingArguments.length;
        checkArgumentCounts(target, numberOfLeadingArguments, parameterIndex);
        Class<?> iteratedType = targetType.parameterType(parameterIndex);
        if (targetType.returnType() == void.class && targetType.parameterCount() == parameterIndex + 1
                && !iteratedType.isPrimitive()) {
            // Try forEach() with direct lambda
            MethodHandle h = iterateDirectHandle(lookup, target, parameterIndex, leadingArguments);
            if (h != null) return h;
        }

        // Not possible to create Consumer interface, use standard iterator pattern
        MethodHandle h = target.asType(targetType.changeReturnType(void.class));
        h = insertLeadingArguments(h, parameterIndex, leadingArguments);
        parameterIndex -= numberOfLeadingArguments;
        if (parameterIndex <= 0) {
            parameterIndex = 0;
        } else {
            h = shiftArgument(h, parameterIndex, 0);
        }

        h = dropArguments(h, 1, Iterable.class);
        h = iteratedLoop(null, null, h);
        h = shiftArgument(h, 0, parameterIndex);
        targetType = h.type();
        return target.isVarargsCollector() && parameterIndex < targetType.parameterCount() - 1 ?
                h.asVarargsCollector(targetType.parameterType(targetType.parameterCount() - 1)) : h;
    }

    private static MethodHandle iterateDirectHandle(Lookup lookup, MethodHandle target, int parameterIndex, Object[] leadingArguments) {
        MethodHandle lambdaFactory;
        try {
            lambdaFactory = createLambdaFactory(lookup, target, Consumer.class);
        } catch (IllegalArgumentException ex) {
            // Lookup not available or iterated type is primitive
            lambdaFactory = null;
        }
        if (lambdaFactory == null) {
            return null;
        }
        int numberOfLeadingArguments = leadingArguments.length;
        @SuppressWarnings({"unchecked", "rawTypes"})
        MethodHandle forEach = MethodLocator.findUsing(Iterable.class, i -> i.forEach(null));
        if (parameterIndex == numberOfLeadingArguments) {
            // Can directly insert action, resulting handle will only accept the iterable
            Object action;
            try {
                action = lambdaFactory.invokeWithArguments(leadingArguments);
            } catch (RuntimeException | Error e) {
                throw e;
            } catch (Throwable t) {
                throw new UndeclaredThrowableException(t);
            }
            return insertArguments(forEach, 1, action);
        } else {
            // result has leading arguments, needs to create lambda on the fly
            MethodType targetType = target.type();
            Class<?>[] leadingTypes = Arrays.copyOf(targetType.parameterArray(), targetType.parameterCount() - 1);
            MethodHandle factory = exactInvoker(methodType(Consumer.class, leadingTypes)).bindTo(lambdaFactory);
            factory = insertArguments(factory, 0, leadingArguments);
            MethodHandle result = collectArguments(forEach, 1, factory);
            return shiftArgument(result, 0, parameterIndex - numberOfLeadingArguments);
        }
    }

    private static void checkArgumentCounts(MethodHandle target, int numberOfLeadingArguments, int parameterIndex) {
        checkArgumentLength(target.type(), parameterIndex);
        if (numberOfLeadingArguments >= target.type().parameterCount()) {
            throw new IllegalArgumentException(target + " cannot accept " + numberOfLeadingArguments + " leading arguments");
        }
    }

    private static MethodHandle insertLeadingArguments(MethodHandle target, int parameterIndex, Object... leadingArguments) {
        int remainingCount = leadingArguments.length - parameterIndex;
        if (remainingCount > 0) {
            // Index is between leading attributes
            target = insertArguments(target, 0, Arrays.copyOf(leadingArguments, parameterIndex));
            Object[] remainingLeadingArguments = new Object[remainingCount];
            System.arraycopy(leadingArguments, parameterIndex, remainingLeadingArguments, 0, remainingCount);
            target = insertArguments(target, 1, remainingLeadingArguments);
        } else {
            // Leading attributes can be added directly
            target = insertArguments(target, 0, leadingArguments);
        }
        return target;
    }

    /**
     * Creates a handle that iterates over some array instance instead of the original parameter at the given index.
     * Only stateless iterations are supported, and no value is returned.
     *
     * The given target handle will be called for each element in the given array. The array's component type will be
     * the same as the original argument type. If the target returns some values, it will be ignored.
     *
     * Let (a0, ... aP, aP+1, ... aM-1)R be the target type, where P is the parameter index of the iterated value,
     * M is the number of the target's arguments, and R is the return type.
     *
     * Then the resulting handle will be of type (a0, ..., aP[], aP+1, ... aM-1)void.
     *
     * The result will be of variable arity if the arrayed parameter is the last one, or if the original target
     * was already of variable arity.
     *
     * @param target This will be called
     * @param parameterIndex The index of the target's iterated element parameter, starting from 0
     * @return A handle that iterates over all elements of the given array
     */
    public static MethodHandle iterateArray(MethodHandle target,  int parameterIndex) {
        return collectArray(target.asType(target.type().changeReturnType(void.class)), parameterIndex);
    }

    /**
     * Creates a handle that iterates over some array instance instead of the original parameter at the given index.
     * The return values of each iteration is collected into an array of the exact same length as the input array.
     *
     * The given target handle will be called for each element in the given array. The array's component type will be
     * the same as the original argument type. The return type is the same array type.
     *
     * Let (a0, ... aP, aP+1, ... aM-1)R be the target type, where P is the parameter index of the iterated value,
     * M is the number of the target's arguments, and R is the return type.
     *
     * Then the resulting handle will be of type (a0, ..., aP[], aP+1, ... aM-1)R[].
     *
     * If the target's return type is void, then the resulting handle's return type is also void.
     *
     * The result will be of variable arity if the arrayed parameter is the last one, or if the original target
     * was already of variable arity.
     *
     * @param target This will be called
     * @param parameterIndex The index of the target's iterated element parameter
     * @return A handle that iterates over all elements of the given array and returns the results
     */
    public static MethodHandle collectArray(MethodHandle target,  int parameterIndex) {
        MethodType targetType = target.type();
        checkArgumentLength(targetType, parameterIndex);

        Class<?> returnType = targetType.returnType();
        Class<?> arrayType = toArrayType(targetType.parameterType(parameterIndex));

        MethodHandle getValue = arrayElementGetter(arrayType);
        MethodHandle arrayLength = createArrayLengthGetter(arrayType, targetType, parameterIndex);
        MethodHandle body = collectArguments(target, parameterIndex, getValue);

        MethodType bodyType;
        MethodHandle init;
        int startIndex, indexPosition;
        if (returnType == void.class) {
            init = null;
            // body = (0..n-1,array,index,n+1..m)void
            // Expected: (index,0..n,array,n+2..m)void
            startIndex = 1;
            indexPosition = 0;
            bodyType = body.type().insertParameterTypes(0, int.class);
        } else {
            Class<?> returnedArrayType = toArrayType(returnType);
            init = filterReturnValue(arrayLength, arrayConstructor(returnedArrayType));
            MethodHandle setter = returnArgument(arrayElementSetter(returnedArrayType), 0);
            body = collectArguments(setter, 2, body); // (V,index,0..n-1,array,index,n+1..m)V
            // Expected: (V,index,0..n-1,array,n+1..m)V
            startIndex = 0;
            indexPosition = 1;
            bodyType = body.type();
        }

        int additionalIndexPosition = parameterIndex + 2 + indexPosition;
        int pCount = bodyType.parameterCount();
        bodyType = bodyType.dropParameterTypes(additionalIndexPosition, additionalIndexPosition+1);
        int[] indexes = new int[pCount-startIndex];
        for (int i=startIndex; i < pCount; i++) {
            indexes[i-startIndex] = i < additionalIndexPosition ? i : (i == additionalIndexPosition ? indexPosition : i-1);
        }
        body = permuteArguments(body, bodyType, indexes);

        MethodHandle loop = countedLoop(start(), arrayLength, init, body);
        if (parameterIndex == targetType.parameterCount() - 1 || target.isVarargsCollector()) {
            return loop.asVarargsCollector(loop.type().parameterType(loop.type().parameterCount() - 1));
        }

        return loop;
    }

    private static MethodHandle createArrayLengthGetter(Class<?> arrayType, MethodType type, int parameterIndex) {
        MethodHandle getter = arrayLength(arrayType);
        return moveSingleArgumentTo(getter, type, parameterIndex);
    }

    private static MethodHandle moveSingleArgumentTo(MethodHandle target, MethodType type, int index) {
        return dropArguments(target, 0, Arrays.copyOf(type.parameterArray(), index));
    }

    private static Class<?> toArrayType(Class<?> componentType) {
        return Array.newInstance(componentType, 0).getClass();
    }

    /**
     * Creates a handle that iterates over some array instance instead of the original parameter at the given index.
     * The original target's result value (which must not be void) will be injected as a parameter value
     * in every successive call after the first one.
     *
     * The given target handle will be called for each element in the given array. The array's component type will be
     * the same as the original argument type.
     *
     * You have to specify two parameter indexes: the first one specifies the argument which shall be converted to an
     * array, and the second one specifies the argument which receives the result value of previous loop iterations.
     *
     * As a consequence, the original target will be called with identical parameter values except the one at the
     * array index, which is filled with the array values, and the one at the invariant index, which will get the
     * parameter of the loop call in the first iteration, and the result value of the previous iteration on every
     * successive execution.
     *
     * Let (a0, ... aP, aP+1, ... aM-1)R be the target type, where P is the array index of the iterated value,
     * M is the number of the target's arguments, and R is the return type.
     *
     * Then the resulting handle will be of type (a0, ..., aP[], aP+1, ... aM-1)R. The parameter type of the invariant
     * index remains unchanged.
     *
     * @param target This will be called
     * @param arrayIndex The index of the target's iterated element parameter, starting from 0
     * @param invariantIndex The index of the invariant value
     * @return A handle that iterates over all elements of the given array
     */
    public static MethodHandle reduceArray(MethodHandle target, int arrayIndex, int invariantIndex) {
        return reduceArray0(target, arrayIndex, invariantIndex,
                (t, i) -> moveSingleArgumentTo(identity(i), t, invariantIndex), true);
    }


    /**
     * Creates a handle that iterates over some array instance instead of the original parameter at the given index.
     * In the first iteration, the target will be called with the initial value in place of the invariant argument,
     * and in every successive iteration, the previous result value (which must not be void) will be used.
     *
     * The given target handle will be called for each element in the given array. The array's component type will be
     * the same as the original argument type. The initial value will usually be the neutral element of the called operation.
     *
     * You have to specify two parameter indexes: the first one specifies the argument which shall be converted to an
     * array, and the second one specifies the argument which receives the result value of previous loop iterations.
     *
     * As a consequence, the original target will be called with identical parameter values except the one at the
     * array index, which is filled with the array values, and the one at the invariant index, which will get the
     * initial value in the first iteration, and the result value of the previous iteration on every
     * successive execution.
     *
     * Let (a0, ... aP, aP+1, ... aI, aI+1, ... aM-1)R be the target type, where P is the array index of the iterated
     * value, I is the invariant index (which may also be lower than P),  M is the number of the target's arguments,
     * and R is the return type.
     *
     * Then the resulting handle will be of type (a0, ..., aP[], aP+1, ... aI-1, aI+1, ... aM-1)R.
     * The parameter of the invariant index is removed.
     *
     * @param target This will be called
     * @param arrayIndex The index of the target's iterated element parameter, starting from 0
     * @param invariantIndex The index of the invariant value
     * @param initialValue This is injected as the starting value for the first iteration. This may be null, even for
     *                     primitive invariant types; in this case, a zero-like value is used
     * @return A handle that iterates over all elements of the given array
     */
    public static MethodHandle reduceArray(MethodHandle target, int arrayIndex, int invariantIndex, @Nullable Object initialValue) {
        return reduceArray0(target, arrayIndex, invariantIndex,
                (t, i) -> initialValue == null ? null : constant(i, initialValue), false);
    }

    private static MethodHandle reduceArray0(MethodHandle target, int arrayIndex, int invariantIndex,
                                             BiFunction<MethodType, Class<?>, MethodHandle> initFactory, boolean keepInvariantParameter) {
        MethodType targetType = target.type();
        checkArgumentLength(targetType, arrayIndex);
        checkArgumentLength(targetType, invariantIndex);
        if (arrayIndex == invariantIndex) {
            throw new IllegalArgumentException("Same index " + arrayIndex + " for array and invariant type");
        }

        Class<?> arrayType = toArrayType(targetType.parameterType(arrayIndex));
        Class<?> returnType = targetType.returnType();
        if (returnType == void.class) {
            throw new IllegalArgumentException(target + " must return some value");
        }
        Class<?> invariantType = targetType.parameterType(invariantIndex);
        MethodHandle h = target.asType(targetType.changeReturnType(invariantType));

        MethodHandle getValue = arrayElementGetter(arrayType);
        MethodHandle body = collectArguments(h, arrayIndex, getValue);
        // Actual: (0..n-1,array,index,n+1..m-1,V,m+1...o)V
        // Expected: (V,index,0..n-1,array,n+1..m)V

        MethodType outerType = body.type();
        int pCount = outerType.parameterCount();
        outerType =  outerType.dropParameterTypes(arrayIndex + 1, arrayIndex + 2);

        int outerArrayPos;
        if (keepInvariantParameter) {
            outerArrayPos = arrayIndex;
        } else {
            outerType = outerType.dropParameterTypes(invariantIndex, invariantIndex + 1);
            outerArrayPos = invariantIndex < arrayIndex ? arrayIndex - 1 : arrayIndex;
        }
        MethodType bodyType = outerType.insertParameterTypes(0, invariantType, int.class);
        int[] indexes = new int[pCount];
        int addend = 2; // For invariant value and index
        int newInvariantIndex = invariantIndex;
        for (int i=0; i < pCount; i++) {
            if (i == newInvariantIndex) {
                indexes[i] = 0;
                if (!keepInvariantParameter) {
                    addend--;
                }
                continue;
            }
            indexes[i] = i + addend;
            if (i == arrayIndex) {
                indexes[++i] = 1;
                addend--;
                newInvariantIndex++;
            }
        }
        body = permuteArguments(body, bodyType, indexes);

        MethodHandle end = moveSingleArgumentTo(arrayLength(arrayType), outerType, outerArrayPos);
        MethodHandle init = initFactory.apply(outerType, invariantType);
        MethodHandle loop = countedLoop(start(), end, init, body);
        outerType = outerType.changeReturnType(returnType);
        loop = loop.asType(outerType);
        if (arrayIndex == targetType.parameterCount() - 1 || target.isVarargsCollector()) {
            loop = loop.asVarargsCollector(outerType.parameterType(outerType.parameterCount() - 1));
        }

        return loop;
    }

    private static MethodHandle start() {
        return constant(int.class, 0);
    }

    /**
     * Shifts an argument of the given target to a new position and returns the resulting handle.
     *
     * One individual argument can be shifted to any new position. The argument between the old and the new position
     * will shift by one, while the argument before and after remain unchanged.
     *
     * @param target The handle to call
     * @param fromIndex Move this argument...
     * @param toIndex ... to that position
     * @return A handle with the exact same type but with a shifted argument list
     */
    public static MethodHandle shiftArgument(MethodHandle target, int fromIndex, int toIndex) {
        MethodType type = target.type();
        checkArgumentLength(type, fromIndex);
        checkArgumentLength(type, toIndex);
        if (fromIndex == toIndex) return target;

        int parameterCount = type.parameterCount();
        Class<?>[] parameterArray = type.parameterArray();
        Class<?>[] newArguments = new Class<?>[parameterCount];
        int[] permutations = new int[parameterCount];
        System.arraycopy(parameterArray, 0, newArguments, 0, Math.min(fromIndex, toIndex));
        newArguments[toIndex] = parameterArray[fromIndex];

        if (fromIndex < toIndex) {
            System.arraycopy(parameterArray, fromIndex + 1, newArguments, fromIndex, toIndex - fromIndex);
            System.arraycopy(parameterArray, toIndex + 1, newArguments, toIndex + 1, parameterCount - toIndex - 1);
            for (int i = 0; i < parameterCount; i++) {
                permutations[i] = i > fromIndex && i <= toIndex ? i - 1 : i;
            }
        } else {
            System.arraycopy(parameterArray, toIndex, newArguments, toIndex + 1, fromIndex - toIndex);
            System.arraycopy(parameterArray, fromIndex + 1, newArguments, fromIndex + 1, parameterCount - fromIndex - 1);
            for (int i = 0; i < parameterCount; i++) {
                permutations[i] = i >= toIndex && i < fromIndex ? i + 1 : i;
            }
        }

        permutations[fromIndex] = toIndex;
        return permuteArguments(target, methodType(type.returnType(), newArguments), permutations);
    }

    private static Method findSingleMethodIn(Lookup lookup, Class<?> type, MethodType reference) {
        MethodLocator locator = MethodLocator.forLocal(lookup, type);

        return locator.doInClassHierarchy(c -> {
            Method bestMatch = null;
            Comparison matchingRank = null;
            boolean ambiguous = false;
            Method[] methods = MethodLocator.getDeclaredMethodsFrom(c);
            loop:
            for (Method m : methods) {
                if (!locator.wouldBeVisible(m)) {
                    continue;
                }
                Comparison compare = compare(reference, m);
                switch (compare) {
                    case EQUAL:
                        if (matchingRank == Comparison.EQUAL) {
                            ambiguous = true;
                            break loop;
                        }
                        matchingRank = Comparison.EQUAL;
                        bestMatch = m;
                        ambiguous = false;
                        break;
                    case MORE_SPECIFIC:
                        if (matchingRank != null && matchingRank != Comparison.MORE_SPECIFIC) {
                            break;
                        }
                    case MORE_GENERIC: // or MORE_SPECIFIC
                        if (matchingRank != compare) {
                            matchingRank = compare;
                            ambiguous = false;
                        } else {
                            ambiguous = bestMatch != null;
                        }
                        bestMatch = m;
                        break;
                    default:
                        // Do nothing then
                }
            }

            if (ambiguous) {
                throw new AmbiguousMethodException("Multiple methods found with type " + reference + " in " + type.getName());
            }
            return bestMatch;
        });
    }

    /**
     * Finds the only method in the given interface that doesn't have a default implementation, i.e. it's the functional interface's only method.
     * Fail if the given type is not a functional interface.
     *
     * @param lambdaType The interface type
     * @return The found method
     */
    public static Method findLambdaMethodOrFail(Class<?> lambdaType) {
        return findLambdaMethod(lambdaType).orElseThrow(() -> new IllegalArgumentException(lambdaType.getName() + " is not a functional interface."));
    }

    /**
     * Finds the only method in the given interface that doesn't have a default implementation, i.e. it's the functional interface's only method.
     * Will return only one or no methods at all.
     *
     * @param lambdaType The interface type
     * @return The found method
     */
    public static Optional<Method> findLambdaMethod(Class<?> lambdaType) {
        if (!lambdaType.isInterface()) {
            throw new IllegalArgumentException(lambdaType.getName() + " should be an interface!");
        }
        Method found = null;
        Method[] methods = AccessController.doPrivileged((PrivilegedAction<Method[]>) lambdaType::getMethods);
        for (Method m : methods) {
            int modifiers = m.getModifiers();
            if (Modifier.isStatic(modifiers) || !Modifier.isAbstract(modifiers)) {
                continue;
            }
            if (found == null) {
                found = m;
            } else {
                return Optional.empty();
            }
        }
        return Optional.ofNullable(found);
    }

    /**
     * Internal interface to check lambda access.
     */
    public interface LambdaMarker { }

    /**
     * Creates a lambda factory for the given type which will then call the given target method.
     *
     * @param lookup       Must be the caller's lookup according to LambdaMetaFactory's documentation
     * @param targetMethod This will be called in the created lambda - it must be a direct handle, otherwise this
     *                     method will return null
     * @param lambdaType   The interface that specifies the lambda. The lambda method's argument size n must not exceed
     *                     the target's argument size t, and their types must be convertible to the last n arguments of the
     *                     target.
     * @param markerInterfaces Some interfaces without methods that will be implemented by the created lambda instance
     * @return A MethodHandle that accepts the first (t-n) arguments of the target and returns an instance of lambdaType.
     */
    @Nullable
    public static MethodHandle createLambdaFactory(Lookup lookup, MethodHandle targetMethod, Class<?> lambdaType, Class<?>... markerInterfaces) {

        Method m = findLambdaMethodOrFail(lambdaType);
        String name = m.getName();
        Class<?>[] calledParameters = m.getParameterTypes();
        MethodType calledType = methodType(m.getReturnType(), calledParameters);
        MethodType targetType = targetMethod.type();
        Class<?>[] targetParameters = targetType.parameterArray();
        int methodParamSize = calledParameters.length;
        if (targetParameters.length < methodParamSize) {
            throw new IllegalArgumentException("targetMethod has too few parameters: Expected at least " + methodParamSize + ", actual: " + targetParameters.length);
        }
        int additional = targetParameters.length - methodParamSize;
        Class<?>[] additionalParameters = Arrays.copyOf(targetParameters, additional);
        MethodType factoryType = methodType(lambdaType, additionalParameters);
        MethodType instantiatedType;
        if (additional == 0) {
            instantiatedType = targetType;
        } else {
            Class<?>[] params = new Class<?>[methodParamSize];
            System.arraycopy(targetParameters, additional, params, 0, methodParamSize);
            instantiatedType = methodType(targetType.returnType(), params);
        }

        for (Class<?> marker : markerInterfaces) {
            if (!marker.isInterface() || MethodLocator.getDeclaredMethodsFrom(marker).length > 0) {
                throw new IllegalArgumentException(marker.getName() + " must be an empty interface!");
            }
        }

        Object[] metaArguments = new Object[6 + markerInterfaces.length];
        metaArguments[0] = calledType;
        metaArguments[1] = targetMethod;
        metaArguments[2] = instantiatedType;
        metaArguments[3] = LambdaMetafactory.FLAG_MARKERS;
        metaArguments[4] = markerInterfaces.length + 1;
        metaArguments[5] = LambdaMarker.class;
        System.arraycopy(markerInterfaces, 0, metaArguments, 6, markerInterfaces.length);
        CallSite callSite;
        try {
            callSite = LambdaMetafactory.altMetafactory(lookup, name, factoryType, metaArguments);
        } catch (LambdaConversionException ex) {
            if (ex.getMessage().contains("Unsupported MethodHandle kind")) {
                // Ugly check, but how to do better?
                return null;
            }
            throw new IllegalArgumentException("Cannot call " + targetMethod + " on " + m, ex);
        } catch (IllegalArgumentException ex) {
            if (ex.getMessage().contains("not a direct method handle") || ex.getMessage().contains(" is private:")) {
                // Ugly check, but how to do better?
                return null;
            }
            throw ex;
        }

        return callSite.getTarget();
    }

    /**
     * Creates an instance of the given lambda interface that will call the given target method.
     * <p>
     * You can see this as an abbreviation to first creating a factory handle via createLambdaFactory() and then
     * creating that instance by inserting the initializer values. But this will also work when the target handle is
     * not a direct one by falling back to a proxy implementation.
     *
     * @param targetMethod This method will be called. It must accept at least that many arguments as the lambda method.
     * @param lambdaType   The instantiated type. Must be a functional interface.
     * @param initializers These can be used to fill up the first target handle's arguments
     * @param <T>          The type
     * @return An instance of the lambda type
     */
    public static <T> T lambdafy(MethodHandle targetMethod, Class<T> lambdaType, Object... initializers) {
        return lambdafy(lookup(), targetMethod, lambdaType, initializers);
    }

    /**
     * Creates an instance of the given lambda interface that will call the given target method.
     * <p>
     * You can see this as an abbreviation to first creating a factory handle via createLambdaFactory() and then
     * creating that instance by inserting the initializer values. But this will also work when the target handle is
     * not a direct one by falling back to a proxy implementation.
     *
     * @param lookup The caller's lookup
     * @param targetMethod This method will be called. It must accept at least that many arguments as the lambda method.
     * @param lambdaType   The instantiated type. Must be a functional interface.
     * @param initializers These can be used to fill up the first target handle's arguments
     * @param <T>          The type
     * @return An instance of the lambda type
     */
    public static <T> T lambdafy(Lookup lookup, MethodHandle targetMethod, Class<T> lambdaType, Object... initializers) {
        MethodHandle lambdaFactory = createLambdaFactory(lookup, targetMethod, lambdaType);
        MethodHandle handle;
        if (lambdaFactory == null) {
            // Not a direct handle - use normal interface creation
            if (initializers.length == 0) {
                handle = targetMethod;
            } else {
                handle = insertArguments(targetMethod, 0, initializers);
            }
            return MethodHandleProxies.asInterfaceInstance(lambdaType, handle);
        }
        try {
            return lambdaType.cast(lambdaFactory.invokeWithArguments(initializers));
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable t) {
            throw new InternalError("Creating lambda " + lambdaType.getName() + " failed.", t);
        }
    }

    /**
     * Checks whether the given lambda instance was created from a factory returned from createLambdaFactory().
     *
     * @param instance The lambda to check
     * @return true is it was successfully created from a direct handle
     */
    public static boolean wasLambdafiedDirect(Object instance) {
        return instance instanceof LambdaMarker;
    }
}