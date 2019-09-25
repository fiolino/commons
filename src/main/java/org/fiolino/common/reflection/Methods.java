package org.fiolino.common.reflection;

import org.fiolino.common.util.Types;

import javax.annotation.Nullable;
import java.lang.invoke.*;
import java.lang.reflect.*;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.function.*;
import java.util.logging.Logger;

import static java.lang.invoke.MethodHandles.*;
import static java.lang.invoke.MethodType.methodType;

/**
 * Box of several arbitary utility methods around MethodHandles.
 * <p>
 * Created by Michael Kuhlmann on 14.12.2015.
 */
public final class Methods {

    private static final String IGNORE_MYSELF = Methods.class.getName();

    private static BiConsumer<String, String> logger = (c, l) -> Logger.getLogger(c).warning(c + ": " + l);

    private Methods() {
        throw new AssertionError();
    }

    public static void setLogger(BiConsumer<String, String> logger) {
        Methods.logger = logger;
    }

    static void warn(String message) {
        String callerName = StackWalker.getInstance().walk(s ->
                        s.map(StackWalker.StackFrame::getClassName).filter(c -> !IGNORE_MYSELF.equals(c)).findFirst()
                                .orElseThrow(() -> new AssertionError("Small stack trace")));
        logger.accept(callerName, message);
    }

    private static final class NullChecks {
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
        return NullChecks.nullCheck.asType(methodType(boolean.class, toCheck));
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
        return NullChecks.notNullCheck.asType(methodType(boolean.class, toCheck));
    }

    /**
     * Creates a handle that compares two values for equality if it's an Object, or by identity if it's a primitive or enum.
     *
     * @param type The type of the objects to compare
     * @return (&lt;type&gt;,&lt;type&gt;)boolean
     */
    public static MethodHandle equalsComparator(Class<?> type) {
        if (type.isPrimitive()) {
            if (type == void.class) {
                throw new IllegalArgumentException("void");
            }
            return IdentityComparators.getIdentityComparator(type);
        }
        final MethodHandle handle;
        if (type.isEnum()) {
            handle = IdentityComparators.getIdentityComparator(Enum.class);
        } else {
            try {
                handle = publicLookup().findStatic(Objects.class, "equals", methodType(boolean.class, Object.class, Object.class));
            } catch (NoSuchMethodException | IllegalAccessException ex) {
                throw new AssertionError("Objects.equals(Object,Object)");
            }
        }
        return handle.asType(methodType(boolean.class, type, type));
    }

    private static final class IdentityComparators {
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
        private static boolean isIdentical(boolean a, boolean b) {
            return a == b;
        }

        @SuppressWarnings("unused")
        private static boolean isIdentical(Enum<?> a, Enum<?> b) {
            return a == b;
        }
    }

    /**
     * Creates a MethodHandle which accepts a String as the only parameter and returns an instance of the given type.
     * It already contains a null check: If the input string is null, then the returned value will be null as well.
     *
     * @param type The enum type
     * @param specialHandler Can return a special value for some field and its value.
     *                       If it returns null, name() is used.
     * @param <E> The enum
     * @return (String)&lt;type&gt; -- throws IllegalArgumentException If the input string is invalid, i.e. none of the accepted enumeration values
     */
    public static <E> MethodHandle convertStringToEnum(Class<E> type, BiFunction<? super Field, ? super E, String> specialHandler) {
        Map<String, Object> map = new HashMap<>();
        Field[] fields = AccessController.doPrivileged((PrivilegedAction<Field[]>) type::getFields);
        boolean useMap = !type.isEnum();

        for (java.lang.reflect.Field f : fields) {
            int m = f.getModifiers();
            if (!Modifier.isStatic(m) || !Modifier.isPublic(m)) {
                continue;
            }
            if (!type.isAssignableFrom(f.getType())) {
                continue;
            }
            AccessController.doPrivileged((PrivilegedAction<Field>) () -> {
                f.setAccessible(true);
                return null;
            });
            E v;
            try {
                v = type.cast(f.get(null));
            } catch (IllegalAccessException ex) {
                throw new AssertionError("Cannot access " + f, ex);
            }
            String value = specialHandler.apply(f, v);
            if (value == null) {
                // The default handling.
                put(map, f.getName(), v);
                continue;
            }
            useMap = true;
            put(map, value, v);
        }
        if (!useMap) {
            // No special handling
            return convertStringToNormalEnum(type);
        }
        // There are annotations, or it wasn't an enum; bind to Map.get() method
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
            warn("Key " + key + " was already defined for " + map.get(key));
            return;
        }
        map.put(key, value);
    }

    /**
     * Creates a handle that converts some enum or some other class with constants to a String.
     * Instead of just using the field's, it is checked whether some fields should be treated specifically; in that case,
     * those values will be used.
     *
     * An example would be to check whether some enum field is annotated somehow.
     *
     * @param type The type
     * @param specialHandler Can return a special value for some field and its value.
     *                       If it returns null, the field's name is used. If it returns an empty String, the field will be skipped.
     * @param <E> The enum
     * @return (E)String -- Returns null if the parameter is null
     */
    public static <E> MethodHandle convertEnumToString(Class<E> type, BiFunction<? super Field, ? super E, String> specialHandler) {
        @SuppressWarnings("unchecked")
        Map<E, String> map = type.isEnum() ? new EnumMap(type.asSubclass(Enum.class)) : new HashMap<>();
        boolean useMap = !type.isEnum();
        Field[] fields = AccessController.doPrivileged((PrivilegedAction<Field[]>) type::getFields);
        for (java.lang.reflect.Field f : fields) {
            int m = f.getModifiers();
            if (!Modifier.isStatic(m) || !Modifier.isPublic(m)) {
                continue;
            }
            if (!type.isAssignableFrom(f.getType())) {
                continue;
            }
            AccessController.doPrivileged((PrivilegedAction<Field>) () -> {
                f.setAccessible(true);
                return null;
            });
            E v;
            try {
                v = type.cast(f.get(null));
            } catch (IllegalAccessException ex) {
                throw new AssertionError("Cannot access " + f, ex);
            }
            if (v == null) continue;
            String value = specialHandler.apply(f, v);
            if (value == null) {
                // The default handling.
                map.put(v, f.getName());
                continue;
            }
            useMap = true;
            if (!value.isEmpty()) {
                map.put(v, value);
            }
        }
        if (useMap) {
            MethodHandle getFromMap;
            try {
                getFromMap = publicLookup().bind(map, "get", methodType(Object.class, Object.class));
            } catch (NoSuchMethodException | IllegalAccessException ex) {
                throw new InternalError("Map::get", ex);
            }
            return getFromMap.asType(methodType(String.class, type));
        } else {
            // No special handling
            // type will be an enum
            try {
                return secureNull(publicLookup().findVirtual(type, "name", methodType(String.class)));
            } catch (NoSuchMethodException | IllegalAccessException ex) {
                throw new InternalError(type.getName() + "::name", ex);
            }
        }
    }

    @SuppressWarnings("unchecked")
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
     * failed call, then you can refer to these values in the exception handler via parameters [0]..[k] for the injection values,
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
     * failed call, then you can refer to these values in the exception handler via parameters [0]..[k] for the injection values,
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
        handlerHandle = insertArgumentsOrSuppliers(handlerHandle, injections);
        handlerHandle = changeNullSafeReturnType(handlerHandle, targetType.returnType());
        handlerHandle = handlerHandle.asType(targetType.insertParameterTypes(0, catchedExceptionType));

        return catchException(target, catchedExceptionType, handlerHandle);
    }

    private static MethodHandle insertArgumentsOrSuppliers(MethodHandle handle, Object[] injections) {
        for (Object i : injections) {
            if (i instanceof Supplier) {
                return insertSuppliers(handle, injections);
            }
        }
        return insertArguments(handle, 1, injections);
    }

    private static MethodHandle insertSuppliers(MethodHandle handle, Object[] injections) {
        MethodHandle h = handle;
        for (Object i : injections) {
            if (i instanceof Supplier) {
                MethodHandle get = MethodLocator.findUsing(Supplier.class, Supplier::get).bindTo(i);
                h = collectArguments(h, 1, get);
            } else {
                h = insertArguments(h, 1, i);
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
            return NullChecks.notNullCheck;
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
        return iterate(null, target, parameterIndex, leadingArguments);
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
     * @param lookup The local lookup; necessary when the target is a direct, private member. May be null
     * @param target This will be called
     * @param parameterIndex The index of the target's iterated element parameter, starting from 0, including the
     *                       leading arguments
     * @param leadingArguments These will be passed to every call of the target as the first arguments
     * @return A handle that iterates over all elements of the given iterable
     */
    public static MethodHandle iterate(@Nullable Lookup lookup, MethodHandle target, int parameterIndex, Object... leadingArguments) {
        return iterate(lookup, target, Iterable.class, parameterIndex, leadingArguments);
    }

    /**
     * Creates a handle that iterates over some inputType instance instead of the original parameter at the given index.
     * Only stateless iterations are supported, and no value is returned.
     *
     * The given target handle will be called for each element in the given Iterable instance. If the target returns
     * some values, it will be ignored.
     *
     * Let (l1, ... lN, aN+1, ... aP, aP+1, aP+2, ... aM)R be the target type, where N is the number of leading arguments,
     * P is the parameter index of the iterated value, M is the number of all target's arguments,
     * and R is the return type.
     *
     * Then the resulting handle will be of type (a1, ... aP-N, inputType, aP-N+2, ... aM)void.
     *
     * The given parameter index may be smaller than the number of leading arguments. In this case, the index still refers
     * to the target's argument index, and the remaining leading arguments are filled thereafter. The input type will be
     * at the first position of the resulting handle then.
     *
     * The result will be of variable arity if the original target was already of variable arity, and the iterated
     * parameter is not the last one.
     *
     * Implementation note:
     * The resulting handle will prefer calling forEach() when the target handle can be directly converted to a lambda.
     * This is the case when
     * <ol>
     *     <li>the inputType implements the forEach() method,</li>
     *     <li>the target is a direct method handle,</li>
     *     <li>it is visible from the given Lookup instance,</li>
     *     <li>it originally returns void,</li>
     *     <li>the iterated value is the target's last parameter,</li>
     *     <li>and it is non-primitive.</li>
     * </ol>
     * In any other case, the default iterator pattern is used.
     *
     * @param lookup The local lookup; necessary when the target is a direct, private member. May be null
     * @param target This will be called
     * @param inputType The new argument type at the specified position. Must at least implement iterator(), and
     *                  optionally forEach(). Any class implementing the {@link Iterator} interface is a good choice
     * @param parameterIndex The index of the target's iterated element parameter, starting from 0, including the
     *                       leading arguments
     * @param leadingArguments These will be passed to every call of the target as the first arguments
     * @return A handle that iterates over all elements of the given iterable
     */
    public static MethodHandle iterate(@Nullable Lookup lookup, MethodHandle target,
                                       Class<?> inputType, int parameterIndex, Object... leadingArguments) {
        MethodType targetType = target.type();
        int numberOfLeadingArguments = leadingArguments.length;
        checkArgumentCounts(target, numberOfLeadingArguments, parameterIndex);
        Class<?> iteratedType = targetType.parameterType(parameterIndex);
        if (targetType.returnType() == void.class && targetType.parameterCount() == parameterIndex + 1
                && !iteratedType.isPrimitive()) {
            // Try forEach() with direct lambda
            MethodHandle h = iterateDirectHandle(lookup, target, inputType, parameterIndex, leadingArguments);
            if (h != null) return h;
        }

        // Not possible to create Consumer interface, use standard iterator pattern
        MethodHandle h = target.asType(targetType.changeReturnType(void.class));
        h = insertLeadingArguments(h, parameterIndex, leadingArguments);
        parameterIndex -= numberOfLeadingArguments;
        if (parameterIndex <= 0) {
            parameterIndex = 0;
        }

        h = iterate(l(lookup, inputType), h, inputType, parameterIndex);
        return h.withVarargs(target.isVarargsCollector() && parameterIndex < h.type().parameterCount() - 1);
    }

    private static MethodHandle iterate(Lookup lookup, MethodHandle target, Class<?> iterableType, int parameterIndex) {
        target = shiftArgument(target, parameterIndex, 0);
        target = dropArguments(target, 1, iterableType);
        MethodHandle iterable;
        try {
            iterable = lookup.findVirtual(iterableType, "iterator", methodType(Iterator.class));
        } catch (NoSuchMethodException | IllegalAccessException ex) {
            throw new IllegalArgumentException(iterableType.getName() + " does not implement iterator() method", ex);
        }
        target = iteratedLoop(iterable, null, target);
        return shiftArgument(target, 0, parameterIndex);
    }

    private static Lookup l(Lookup l, Class<?> t) {
        return l == null ? publicLookup().in(t) : l;
    }

    private static MethodHandle iterateDirectHandle(@Nullable Lookup lookup, MethodHandle target, Class<?> inputType, int parameterIndex, Object[] leadingArguments) {
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
        MethodHandle forEach;
        try {
            forEach = l(lookup, inputType).findVirtual(inputType, "forEach", methodType(void.class, Consumer.class));
        } catch (NoSuchMethodException | IllegalAccessException ex) {
            return null; // No forEach method, use iterator
        }
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
            MethodHandle result = collectArguments(forEach, 1, lambdaFactory);
            result = shiftArgument(result, 0, parameterIndex);
            return insertArguments(result, 0, leadingArguments);
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
        return collectArrayIntoArray(target.asType(target.type().changeReturnType(void.class)), parameterIndex);
    }

    /**
     * Creates a handle that iterates over some {@link Iterable} or {@link Collection} instance instead of the original
     * parameter at the given index.
     * The return values of each iteration step is collected into a container of type 'outputType', which will be the
     * return type of the constructed handle.
     *
     * The outputType can be some collection-like concrete class with a public constructor and an add() method,
     * or it can be an array of any matching type.
     *
     * On every iteration, a new instance of the outputType is created.
     * If the outputType is an array, this array will be filled and will have the same length as the number of
     * iteration steps.
     *
     * If the input type has a public size() method, this method will try to instantiate via a public constructor
     * which accepts an int as the only parameter, filling it with the size() return value.
     * Otherwise, the empty constructor is used.
     *
     * The outputType must not necessarily be some {@link Collection}. It must have an add() method, accepting a
     * single value of the target's return type or some of its superclasses and return either boolean or void
     * -- or it must be an array.
     *
     * The given target handle will be called for each element in the input element. It must return some value which
     * can be added to the outputType.
     *
     * The result will be of variable arity if the original target was already of variable arity.
     *
     * @param target This will be called
     * @param inputType The resulting handle will accept this type...
     * @param inputPosition ... at this position. The input element of each iteration step gets filled into this position
     *                      of the original target handle
     * @param outputType The result's return type. Must have an empty constructor or of type (int) if the input type has
     *                   a size method, and must have an add() method
     * @return A handle that iterates over all elements of the given container and returns the results
     */
    public static MethodHandle collectInto(@Nullable MethodHandle target, Class<?> inputType, int inputPosition, @Nullable Class<?> outputType) {
        inputType = validateCollectParameters(target, inputType, inputPosition, outputType);
        if (outputType == null) {
            if (target == null) {
                throw new IllegalArgumentException("Only one of target and outputType may be null");
            }
            return collectCollectionIntoArray(target, inputType, inputPosition, toArrayType(target.type().returnType()));
        }
        if (outputType.isArray()) {
            return collectCollectionIntoArray(target, inputType, inputPosition, outputType);
        } else {
            return collectCollectionIntoCollection(target, inputType, inputPosition, outputType, "add");
        }
    }

    private static Class<?> validateCollectParameters(@Nullable MethodHandle target, Class<?> inputType, int inputPosition, Class<?> outputType) {
        if (target == null) {
            if (inputPosition != 0) throw new IllegalArgumentException("inputPosition " + inputPosition + " must be 0 if target is null");
            if (inputType == null || outputType == null) throw new IllegalArgumentException("types are mandatory of target ist null");
        } else {
            MethodType targetType = target.type();
            checkArgumentLength(targetType, inputPosition);
            ensureReturnsValue(targetType);

            if (inputType == null) inputType = toArrayType(targetType.parameterType(inputPosition));
        }
        return inputType;
    }

    private static MethodHandle collectCollectionIntoCollection(@Nullable MethodHandle target, Class<?> inputType, int inputPosition, Class<?> outputType, String addMethodName) {
        MethodType targetType = target == null ? null : target.type();
        MethodHandle constructor = findConstructorForOutput(outputType, targetType, inputType, inputPosition);
        MethodHandle add = findAddMethod(outputType, targetType == null ? Object.class : targetType.returnType(), addMethodName);
        MethodHandle result = collectInto(target, add, inputType, inputPosition, constructor);
        return target == null ? result : result.withVarargs(target.isVarargsCollector());
    }

    /**
     * Creates a handle that iterates over some {@link Iterable} or {@link Collection} instance instead of the original
     * parameter at the given index.
     * The return values of each iteration step is collected into a container of type 'outputType', which will be the
     * return type of the constructed handle.
     *
     * The outputType must be some collection-like concrete class with a public constructor and an adder method,
     * which is named via the addMethodName parameter.
     *
     * On every iteration, a new instance of the outputType is created.
     *
     * If the input type has a public size() method, this method will try to instantiate via a public constructor
     * which accepts an int as the only parameter, filling it with the size() return value.
     * Otherwise, the empty constructor is used.
     *
     * The given target handle will be called for each element in the input element. It must return some value which
     * can be added to the outputType.
     *
     * The result will be of variable arity if the original target was already of variable arity.
     *
     * @param target This will be called
     * @param inputType The resulting handle will accept this type...
     * @param inputPosition ... at this position. The input element of each iteration step gets filled into this position
     *                      of the original target handle
     * @param outputType The result's return type. Must have an empty constructor or of type (int) if the input type has
     *                   a size method
     * @param addMethodName The name of the 'add' method. The outputType must implement this as an instance method. Its
     *                      second argument type must be the target's return type or some super class of it. Its return
     *                      type can be either void, boolean, or the outputType itself
     * @return A handle that iterates over all elements of the given container and returns the results
     */
    public static MethodHandle collectInto(@Nullable MethodHandle target, @Nullable Class<?> inputType, int inputPosition, Class<?> outputType, String addMethodName) {
        inputType = validateCollectParameters(target, inputType, inputPosition, outputType);
        if (outputType.isArray()) {
            throw new IllegalArgumentException("When outputType " + outputType.getName() + " is an array, no addMethodName (" + addMethodName + ") must be given.");
        }
        return collectCollectionIntoCollection(target, inputType, inputPosition, outputType, addMethodName);
    }

    private static void ensureReturnsValue(MethodType targetType) {
        if (targetType.returnType() == void.class) {
            throw new IllegalArgumentException(targetType + " must not return void");
        }
    }

    private static MethodHandle findAddMethod(Class<?> outputType, Class<?> elementType, String methodName) {
        Lookup l = publicLookup().in(outputType);
        MethodHandle add = null;
        Class<?> p = elementType;
        Class<?>[] possibleReturnTypes = {void.class, boolean.class, outputType};
        loop:
        do {
            for (Class<?> r : possibleReturnTypes) {
                try {
                    add = l.findVirtual(outputType, methodName, methodType(r, p));
                    break loop;
                } catch (NoSuchMethodException | IllegalAccessException ex) {
                    // continue
                }
            }
            p = p.isInterface() ? Object.class : (p.isPrimitive() ? Types.toWrapper(p) : p.getSuperclass());
        } while (p != null);

        if (add == null) {
            throw new IllegalArgumentException("No add method in " + outputType.getName());
        }
        return add.asType(methodType(void.class, outputType, elementType));
    }

    private static MethodHandle findConstructorForOutput(Class<?> outputType, @Nullable MethodType targetType, Class<?> inputType, int inputPosition) {
        try {
            MethodHandle size = publicLookup().in(inputType).findVirtual(inputType, "size", methodType(int.class));
            try {
                MethodHandle constructor = publicLookup().in(outputType).findConstructor(outputType, methodType(void.class, int.class));
                constructor = filterArguments(constructor, 0, size);
                if (targetType != null) constructor = moveSingleArgumentTo(constructor, targetType, inputPosition);
                return constructor;
            } catch (NoSuchMethodException | IllegalAccessException ex) {
                // No constructor with size initialization
                // Empty constructor
            }
        } catch (NoSuchMethodException | IllegalAccessException ex) {
            // No size method
            // Empty constructor
        }
        return findEmptyConstructor(outputType);
    }

    /**
     * Creates a handle that iterates over some {@link Iterable} or {@link Collection} instance instead of the original
     * parameter at the given index.
     * The return values of each iteration step is collected into an array whose component type is the return type of
     * the target handle.
     *
     * On every iteration, a new instance of the array is created. It will have the same length as the number of
     * iteration steps.
     *
     * If the input type has a public size() method, this method will instantiate the array with the size value.
     * Otherwise the array will grow step by step.
     *
     * The given target handle will be called for each element in the input element.
     *
     * The result will be of variable arity if the original target was already of variable arity.
     *
     * @param target This will be called
     * @param inputType The resulting handle will accept this type...
     * @param inputPosition ... at this position. The input element of each iteration step gets filled into this position
     *                      of the original target handle
     * @return A handle that iterates over all elements of the given container and returns the results as an array
     */
    public static MethodHandle collectIntoArray(MethodHandle target, Class<?> inputType, int inputPosition) {
        Class<?> returnType = target.type().returnType();
        if (returnType == void.class) {
            MethodHandle handle = iterate(target, inputPosition);
            return handle.asType(handle.type().changeParameterType(inputPosition, inputType));
        }
        return collectInto(target, inputType, inputPosition, toArrayType(returnType));
    }

    private static MethodHandle collectCollectionIntoArray(@Nullable MethodHandle target, Class<?> inputType, int inputPosition, Class<?> arrayType) {
        MethodHandle constructor, arrayGetter, indexGetter;
        Lookup l = lookup().in(ArrayFiller.class);
        try {
            constructor = l.findConstructor(ArrayFiller.class, methodType(void.class, Object.class));
            arrayGetter = l.findVirtual(ArrayFiller.class, "array", methodType(Object.class));
            indexGetter = l.findVirtual(ArrayFiller.class, "next", methodType(int.class));
        } catch (NoSuchMethodException | IllegalAccessException ex) {
            throw new InternalError(ex);
        }

        MethodHandle arrayFactory = arrayConstructor(arrayType).asType(methodType(Object.class, int.class));
        try {
            MethodHandle size = publicLookup().in(inputType).findVirtual(inputType, "size", methodType(int.class));
            MethodHandle minimum1 = insertArguments(publicLookup().findStatic(Math.class, "max", methodType(int.class, int.class, int.class)), 0, 1);
            size = filterReturnValue(size,  minimum1);
            arrayFactory = filterArguments(arrayFactory, 0, size);
            constructor = filterArguments(constructor, 0, arrayFactory);
            if (target != null) constructor = moveSingleArgumentTo(constructor, target.type(), inputPosition);
        } catch (NoSuchMethodException | IllegalAccessException ex) {
            // No size method
            constructor = foldArguments(constructor, arrayFactory.bindTo(ArrayFiller.DEFAULT_INITIAL_LENGTH));
        }

        Class<?> returnType = target == null ? Object.class : target.type().returnType();
        MethodHandle arraySetter = arrayElementSetter(arrayType);
        arraySetter = arraySetter.asType(methodType(void.class, Object.class, int.class, returnType));
        arraySetter = filterArguments(arraySetter, 1, indexGetter);
        arraySetter = foldArguments(arraySetter, arrayGetter);

        MethodHandle executeAndAdd = collectInto(target, arraySetter, inputType, inputPosition, constructor);

        MethodHandle getArray;
        try {
            getArray = l.findVirtual(ArrayFiller.class, "values", methodType(Object.class));
        } catch (NoSuchMethodException | IllegalAccessException ex) {
            throw new InternalError(ex);
        }
        getArray = getArray.asType(methodType(arrayType, ArrayFiller.class));
        MethodHandle result = MethodHandles.filterReturnValue(executeAndAdd, getArray);
        return target == null ? result : result.withVarargs(target.isVarargsCollector());
    }

    private static MethodHandle collectInto(@Nullable MethodHandle target, MethodHandle adder, Class<?> inputType, int inputPosition, MethodHandle constructor) {
        MethodHandle executeAndAdd;
        if (target == null) {
            executeAndAdd = iterate(publicLookup(), adder, inputType, 1);
        } else {
            executeAndAdd = collectArguments(adder, 1, target);
            executeAndAdd = iterate(publicLookup(), executeAndAdd, inputType, inputPosition + 1);
        }
        executeAndAdd = returnArgument(executeAndAdd, 0);
        return foldArguments(executeAndAdd, constructor);
    }

    private static MethodHandle findEmptyConstructor(Class<?> outputType) {
        try {
            return publicLookup().in(outputType).findConstructor(outputType, methodType(void.class));
        } catch (NoSuchMethodException | IllegalAccessException ex) {
            throw new IllegalArgumentException("No suitable constructor in " + outputType.getName(), ex);
        }
    }

    /**
     * Creates a handle that iterates over some array instance instead of the original parameter at the given index.
     * The return values of each iteration step is collected into an array of the exact same length as the input array.
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
    public static MethodHandle collectArrayIntoArray(MethodHandle target, int parameterIndex) {
        MethodType targetType = target.type();
        checkArgumentLength(targetType, parameterIndex);

        Class<?> returnType = targetType.returnType();
        Class<?> arrayType = toArrayType(targetType.parameterType(parameterIndex));

        MethodHandle getValue = arrayElementGetter(arrayType);
        MethodHandle arrayLength = arrayLength(arrayType);
        arrayLength = moveSingleArgumentTo(arrayLength, targetType, parameterIndex);
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
        return loop.withVarargs(parameterIndex == targetType.parameterCount() - 1 || target.isVarargsCollector());
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
     * array, and the second one specifies the argument which receives the result value of previous loop iteration steps.
     *
     * As a consequence, the original target will be called with identical parameter values except the one at the
     * array index, which is filled with the array values, and the one at the invariant index, which will get the
     * parameter of the loop call in the first iteration step, and the result value of the previous step on every
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
     * array, and the second one specifies the argument which receives the result value of previous loop iteration steps.
     *
     * As a consequence, the original target will be called with identical parameter values except the one at the
     * array index, which is filled with the array values, and the one at the invariant index, which will get the
     * initial value in the first iteration step, and the result value of the previous step on every
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
        return loop.withVarargs(arrayIndex == targetType.parameterCount() - 1 || target.isVarargsCollector());
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

    static Method[] getDeclaredMethodsFrom(Class<?> c) {
        return AccessController.doPrivileged((PrivilegedAction<Method[]>) c::getDeclaredMethods);
    }

    /**
     * Internal interface to check lambda access.
     */
    public interface LambdaMarker { }

    /**
     * Creates a lambda factory for the given type which will then call the given target method.
     *
     * @param lookup       Must be the caller's lookup according to LambdaMetaFactory's documentation.
     *                     Can be null if the called handle is public
     * @param targetMethod This will be called in the created lambda - it must be a direct handle, otherwise this
     *                     method will return null
     * @param lambdaType   The interface that specifies the lambda. The lambda method's argument size n must not exceed
     *                     the targetMethod's argument size t, and their types must be convertible to the last n arguments of the
     *                     target.
     * @param markerInterfaces Some interfaces without methods that will be implemented by the created lambda instance
     * @return A MethodHandle that accepts the first (t-n) arguments of the targetMethod and returns an instance of lambdaType.
     */
    @Nullable
    public static MethodHandle createLambdaFactory(@Nullable Lookup lookup, MethodHandle targetMethod, Class<?> lambdaType, Class<?>... markerInterfaces) {
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
            if (!marker.isInterface() || getDeclaredMethodsFrom(marker).length > 0) {
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
        Lookup l = lookup == null ? lookup() : lookup;
        try {
            MethodHandleInfo info = l.revealDirect(targetMethod);
            Member originalMember = info.reflectAs(Member.class, l);
            if (Modifier.isPrivate(originalMember.getModifiers()) && !originalMember.getDeclaringClass().equals(l.lookupClass())) {
                // This is a weird situation where only the call to the lambda method itself will throw an IllegalAccessError
                return null;
            }
        } catch (IllegalArgumentException | ClassCastException ex) {
            // Then it's probably not a direct member? continue trying...
        }
        CallSite callSite;
        try {
            callSite = LambdaMetafactory.altMetafactory(l, name, factoryType, metaArguments);
        } catch (LambdaConversionException ex) {
            if (ex.getMessage().contains("Unsupported MethodHandle kind") || ex.getMessage().startsWith("Type mismatch") || ex.getMessage().startsWith("Invalid caller")) {
                // Ugly check, but how to do better?
                return null;
            }
            throw new IllegalArgumentException("Cannot call " + targetMethod + " on " + m, ex);
        } catch (IllegalArgumentException ex) {
            if (ex.getMessage().contains("not a direct method handle") || ex.getMessage().contains(" is private:") || ex.getMessage().contains("member is protected")) {
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
        return lambdafy(null, targetMethod, lambdaType, initializers);
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
    public static <T> T lambdafy(@Nullable Lookup lookup, MethodHandle targetMethod, Class<T> lambdaType, Object... initializers) {
        MethodHandle lambdaFactory = createLambdaFactory(lookup, targetMethod, lambdaType);
        if (lambdaFactory == null) {
            warn(targetMethod + " is not direct, creating a Proxy for " + lambdaType.getName());
            // Not a direct handle - use normal interface creation
            MethodHandle handle = insertArguments(targetMethod, 0, initializers);
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

class ArrayFiller<A> {
    static final int DEFAULT_INITIAL_LENGTH = 16;

    private A values;
    private int i;
    private final ArrayCopier<A> copier;
    private final MethodHandle lengthGetter;

    ArrayFiller(A array) {
        values = array;
        @SuppressWarnings("unchecked")
        Class<A> arrayType = (Class<A>) array.getClass();
        copier = ArrayCopier.createFor(arrayType);
        lengthGetter = MethodHandles.arrayLength(arrayType).asType(methodType(int.class, Object.class));
    }

    private int l() throws Throwable {
        return (int) lengthGetter.invokeExact(values);
    }

    A array() throws Throwable {
        int l = l();
        if (i >= l) {
            values = copy(i * 2);
        }
        return values;
    }

    int next() {
        return i++;
    }

    private A copy(int newLength) {
        return copier.copyOf(values, newLength);
    }

    Object values() throws Throwable {
        if (i == l()) {
            return values;
        }
        return copy(i);
    }
}
