package org.fiolino.common.reflection;

import org.junit.jupiter.api.Test;

import javax.annotation.concurrent.ThreadSafe;
import java.awt.event.ActionListener;
import java.io.FileNotFoundException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandleProxies;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.IntBinaryOperator;
import java.util.function.IntUnaryOperator;
import java.util.function.UnaryOperator;

import static java.lang.invoke.MethodHandles.lookup;
import static java.lang.invoke.MethodHandles.publicLookup;
import static java.lang.invoke.MethodType.methodType;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Created by Michael Kuhlmann on 15.12.2015.
 */
class MethodsTest {

    private static final MethodHandles.Lookup LOOKUP = lookup();

    @Test
    void testEnumConversion() throws Throwable {
        MethodHandle handle = Methods.convertStringToEnum(TimeUnit.class, (f, v) -> null);
        assertNotNull(handle);
        TimeUnit unit = (TimeUnit) handle.invokeExact("DAYS");
        assertEquals(TimeUnit.DAYS, unit);
    }

    @Test
    void testEnumConversionWithSpecial() throws Throwable {
        MethodHandle handle = Methods.convertStringToEnum(TimeUnit.class, (f, v) -> v == TimeUnit.DAYS ? "hola!" : null);
        assertNotNull(handle);
        TimeUnit unit = (TimeUnit) handle.invokeExact("MINUTES");
        assertEquals(TimeUnit.MINUTES, unit);
        unit = (TimeUnit) handle.invokeExact("hola!");
        assertEquals(TimeUnit.DAYS, unit);
    }

    @Test
    void testEnumConversionWithNullValue() throws Throwable {
        MethodHandle handle = Methods.convertStringToEnum(TimeUnit.class, (f, v) -> null);
        assertNotNull(handle);
        TimeUnit unit = (TimeUnit) handle.invokeExact((String) null);
        assertNull(unit);
    }

    @Test
    void testEnumConversionWithException() {
        MethodHandle handle = Methods.convertStringToEnum(TimeUnit.class, (f, v) -> null);
        assertNotNull(handle);
        assertThrows(IllegalArgumentException.class,
                () -> {
                    @SuppressWarnings("unused")
                    TimeUnit unit = (TimeUnit) handle.invokeExact("Boom!");
                });
    }

    @Test
    void testEnumToStringNoSpecial() throws Throwable {
        MethodHandle handle = Methods.convertEnumToString(TimeUnit.class, (f, u) -> null); // Normally you would just use findVirtual("name")
        for (TimeUnit u : TimeUnit.values()) {
            String name = (String) handle.invokeExact(u);
            assertEquals(u.name(), name);
        }
    }

    @Test
    void testEnumToStringWithSpecial() throws Throwable {
        MethodHandle handle = Methods.convertEnumToString(TimeUnit.class, (f, u) -> u == TimeUnit.DAYS || u == TimeUnit.HOURS ? "Boing!" : null);
        for (TimeUnit u : TimeUnit.values()) {
            String name = (String) handle.invokeExact(u);
            if (u == TimeUnit.DAYS || u == TimeUnit.HOURS) {
                assertEquals("Boing!", name);
            } else {
                assertEquals(u.name(), name);
            }
        }
    }

    @SuppressWarnings("unused")
    private void booom() throws FileNotFoundException {
        throw new FileNotFoundException("Booom!");
    }

    @Test
    void testRethrowExceptionNoArguments() throws Throwable {
        MethodHandle booom = LOOKUP.bind(this, "booom", methodType(void.class));
        booom = Methods.wrapWithExceptionHandler(booom, FileNotFoundException.class,
                ExceptionHandler.rethrowException(UnsupportedOperationException::new, "I have no {0} parameters"));
        try {
            booom.invokeExact();
        } catch (UnsupportedOperationException ex) {
            assertEquals("I have no {0} parameters", ex.getMessage());
            assertTrue(ex.getCause() instanceof FileNotFoundException);
            assertEquals("Booom!", ex.getCause().getMessage());
            return;
        }
        fail("Should not be here");
    }

    @Test
    void testRethrowExceptionWithInjections() throws Throwable {
        MethodHandle booom = LOOKUP.bind(this, "booom", methodType(void.class));
        booom = Methods.wrapWithExceptionHandler(booom, FileNotFoundException.class,
                ExceptionHandler.rethrowException(UnsupportedOperationException::new, "No. 1: {0}, no. 2: {1}"), "One", 2);
        try {
            booom.invokeExact();
        } catch (UnsupportedOperationException ex) {
            assertEquals("No. 1: One, no. 2: 2", ex.getMessage());
            assertTrue(ex.getCause() instanceof FileNotFoundException);
            assertEquals("Booom!", ex.getCause().getMessage());
            return;
        }
        fail("Should not be here");
    }

    @SuppressWarnings("unused")
    private void booom(int a, String b) throws FileNotFoundException {
        throw new FileNotFoundException(b);
    }

    @Test
    void testRethrowExceptionWithParameters() throws Throwable {
        MethodHandle booom = LOOKUP.bind(this, "booom", methodType(void.class, int.class, String.class));
        booom = Methods.wrapWithExceptionHandler(booom, FileNotFoundException.class,
                ExceptionHandler.rethrowException(UnsupportedOperationException::new, "Injection 1: {0}, injection 2: {1}, param 1: {2}, param 2: {3}"),
                "One", 2);
        try {
            booom.invokeExact(5, "Hello");
        } catch (UnsupportedOperationException ex) {
            assertEquals("Injection 1: One, injection 2: 2, param 1: 5, param 2: Hello", ex.getMessage());
            assertTrue(ex.getCause() instanceof FileNotFoundException);
            assertEquals("Hello", ex.getCause().getMessage());
            return;
        }
        fail("Should not be here");
    }

    @Test
    void testRethrowExceptionWithSuppliers() throws Throwable {
        MethodHandle booom = LOOKUP.bind(this, "booom", methodType(void.class, int.class, String.class));
        AtomicInteger readCount = new AtomicInteger();
        booom = Methods.wrapWithExceptionHandler(booom, FileNotFoundException.class,
                ExceptionHandler.rethrowException(UnsupportedOperationException::new, "Injection 1: {0}, injection 2: {1}, param 1: {2}, param 2: {3}"),
                () -> "One", readCount::incrementAndGet);

        assertEquals(0, readCount.get());
        try {
            booom.invokeExact(5, "Hello");
        } catch (UnsupportedOperationException ex) {
            assertEquals("Injection 1: One, injection 2: 1, param 1: 5, param 2: Hello", ex.getMessage());
            assertEquals(1, readCount.get());
            assertTrue(ex.getCause() instanceof FileNotFoundException);
            assertEquals("Hello", ex.getCause().getMessage());
        }
        try {
            booom.invokeExact(10, "Hello two");
        } catch (UnsupportedOperationException ex) {
            assertEquals("Injection 1: One, injection 2: 2, param 1: 10, param 2: Hello two", ex.getMessage());
            assertEquals(2, readCount.get());
            return;
        }
        fail("Should not be here");
    }

    @Test
    void testWrapExceptionNoArguments() throws Throwable {
        MethodHandle booom = LOOKUP.bind(this, "booom", methodType(void.class));
        AtomicInteger ref = new AtomicInteger();
        booom = Methods.wrapWithExceptionHandler(booom, FileNotFoundException.class, (ex, v) -> {
            assertEquals(0, v.length);
            ref.set(100);
            return null;
        });
        booom.invokeExact();
        assertEquals(100, ref.get());
    }

    @Test
    void testWrapExceptionTwoArguments() throws Throwable {
        MethodHandle booom = LOOKUP.bind(this, "booom", methodType(void.class, int.class, String.class));
        AtomicInteger refInt = new AtomicInteger();
        AtomicReference<String> refString = new AtomicReference<>();
        booom = Methods.wrapWithExceptionHandler(booom, FileNotFoundException.class, (ex, v) -> {
            assertEquals(2, v.length);
            refInt.set((int) v[0]);
            refString.set((String) v[1]);
            assertEquals(v[1], ex.getMessage());
            return null;
        });
        booom.invokeExact(36, "Fnord");
        assertEquals(36, refInt.get());
        assertEquals("Fnord", refString.get());
    }

    @Test
    void testWrapExceptionAndThrow() throws Throwable {
        MethodHandle booom = LOOKUP.bind(this, "booom", methodType(void.class));
        booom = Methods.wrapWithExceptionHandler(booom, FileNotFoundException.class, (ex, v) -> {
            throw new UnsupportedOperationException(ex);
        });
        assertThrows(UnsupportedOperationException.class, booom::invokeExact);
    }

    @Test
    void testWrapExceptionReturnSomethingElse() throws Throwable {
        MethodHandle parseInt = publicLookup().findStatic(Integer.class, "parseInt", methodType(int.class, String.class));
        AtomicReference<String> ref = new AtomicReference<>();
        parseInt = Methods.wrapWithExceptionHandler(parseInt, NumberFormatException.class, (ex, v) -> {
            assertEquals(1, v.length);
            assertEquals("Not a number", v[0]);
            ref.set((String) v[0]);
            return -5;
        });
        int value = (int) parseInt.invokeExact("10");
        assertEquals(10, value);
        assertNull(ref.get());

        value = (int) parseInt.invokeExact("Not a number");
        assertEquals(-5, value);
        assertEquals("Not a number", ref.get());
    }

    @Test
    void testWrapExceptionReturnNull() throws Throwable {
        MethodHandle parseInt = publicLookup().findStatic(Integer.class, "parseInt", methodType(int.class, String.class));
        AtomicReference<String> ref = new AtomicReference<>();
        parseInt = Methods.wrapWithExceptionHandler(parseInt, NumberFormatException.class, (ex, v) -> {
            assertEquals(1, v.length);
            assertEquals("Not a number", v[0]);
            ref.set((String) v[0]);
            return null; // Should be converted to zero-like value
        });
        int value = (int) parseInt.invokeExact("10");
        assertEquals(10, value);
        assertNull(ref.get());

        value = (int) parseInt.invokeExact("Not a number");
        assertEquals(0, value);
        assertEquals("Not a number", ref.get());
    }

    @Test
    void testWrapExceptionHandleAlternative() throws Throwable {
        MethodHandle charAt = publicLookup().findVirtual(String.class, "charAt", methodType(char.class, int.class));
        ExceptionHandler<RuntimeException> exceptionHandler = (ex, v) -> '0';
        charAt = Methods.wrapWithExceptionHandler(charAt, RuntimeException.class, exceptionHandler.orIf(IndexOutOfBoundsException.class, (ex, v) -> '?'));
        char value = (char) charAt.invokeExact("abc", 1);
        assertEquals('b', value);

        value = (char) charAt.invokeExact((String) null, 5);
        assertEquals('0', value);
        value = (char) charAt.invokeExact("abc", 5);
        assertEquals('?', value);
    }

    @Test
    void testNullSafeObjectToPrimitive() throws Throwable {
        MethodHandle identity = MethodHandles.identity(Object.class);
        MethodHandle handle = Methods.changeNullSafeReturnType(identity, int.class);

        int intValue = (int) handle.invokeExact((Object) null);
        assertEquals(0, intValue);

        MethodHandle handle2 = Methods.changeNullSafeReturnType(identity, boolean.class);

        boolean booleanValue = (boolean) handle2.invokeExact((Object) null);
        assertFalse(booleanValue);
    }

    @Test
    void testNullSafeObjectToVoid() throws Throwable {
        MethodHandle identity = MethodHandles.identity(Object.class);
        MethodHandle handle = Methods.changeNullSafeReturnType(identity, void.class);

        handle.invokeExact((Object) null);
    }

    @Test
    void testNullSafePrimitiveToObject() throws Throwable {
        MethodHandle identity = MethodHandles.identity(int.class);
        MethodHandle handle = Methods.changeNullSafeReturnType(identity, Object.class);

        Object value = handle.invokeExact(10);
        assertEquals(10, value);
    }

    @Test
    void testNullSafePrimitiveToPrimitive() throws Throwable {
        MethodHandle identity = MethodHandles.identity(int.class);
        MethodHandle handle = Methods.changeNullSafeReturnType(identity, long.class);

        long value = (long) handle.invokeExact(10);
        assertEquals(10L, value);
    }

    @Test
    void testNullSafePrimitiveToVoid() throws Throwable {
        MethodHandle identity = MethodHandles.identity(int.class);
        MethodHandle handle = Methods.changeNullSafeReturnType(identity, void.class);

        handle.invokeExact(10);
    }

    @Test
    void testNullSafeVoidToObject() throws Throwable {
        Date date = new Date();
        MethodHandle setTime = publicLookup().bind(date, "setTime", methodType(void.class, long.class));
        MethodHandle handle = Methods.changeNullSafeReturnType(setTime, Object.class);

        Object value = handle.invokeExact(123456789L);
        assertNull(value);
        assertEquals(123456789L, date.getTime());
    }

    @Test
    void testNullSafeVoidToPrimitive() throws Throwable {
        Date date = new Date();
        MethodHandle setTime = publicLookup().bind(date, "setTime", methodType(void.class, long.class));
        MethodHandle handle = Methods.changeNullSafeReturnType(setTime, double.class);

        double value = (double) handle.invokeExact(123456789L);
        assertEquals(0, value, 0.01);
        assertEquals(123456789L, date.getTime());
    }

    @SuppressWarnings("unused")
    private boolean checkEquals(String first, String second, AtomicInteger counter) {
        counter.incrementAndGet();
        return first.equals(second);
    }

    @SuppressWarnings("unused")
    private boolean checkEqualsConcatenation(String first, String second, String secondTail, AtomicInteger counter) {
        counter.incrementAndGet();
        return first.equals(second + " " + secondTail);
    }

    @Test
    void testAnd() throws Throwable {
        AtomicInteger counter = new AtomicInteger();
        MethodHandle check = LOOKUP.bind(this, "checkEquals", methodType(boolean.class, String.class, String.class, AtomicInteger.class));
        check = MethodHandles.insertArguments(check, 2, counter);
        MethodHandle check2 = LOOKUP.bind(this, "checkEqualsConcatenation", methodType(boolean.class, String.class, String.class, String.class, AtomicInteger.class));
        check2 = MethodHandles.insertArguments(check2, 3, counter);

        MethodHandle result = Methods.and();
        boolean val = (boolean) result.invokeExact();
        assertTrue(val);

        result = Methods.and(MethodHandles.insertArguments(check, 0, "Hello"), MethodHandles.insertArguments(check2, 0, "Hello World!"));
        val = (boolean) result.invokeExact("Hi", "cruel World!");
        assertFalse(val);
        assertEquals(1, counter.get());

        counter.set(0);
        val = (boolean) result.invokeExact("Hello", "Goodbye");
        assertFalse(val);
        assertEquals(2, counter.get());

        counter.set(0);
        val = (boolean) result.invokeExact("Hello", "World!");
        assertTrue(val);
        assertEquals(2, counter.get());
    }

    @Test
    void testOr() throws Throwable {
        AtomicInteger counter = new AtomicInteger();
        MethodHandle check = LOOKUP.bind(this, "checkEquals", methodType(boolean.class, String.class, String.class, AtomicInteger.class));
        check = MethodHandles.insertArguments(check, 2, counter);
        MethodHandle check2 = LOOKUP.bind(this, "checkEqualsConcatenation", methodType(boolean.class, String.class, String.class, String.class, AtomicInteger.class));
        check2 = MethodHandles.insertArguments(check2, 3, counter);

        MethodHandle result = Methods.or();
        boolean val = (boolean) result.invokeExact();
        assertFalse(val);

        result = Methods.or(MethodHandles.insertArguments(check, 0, "Hi"), MethodHandles.insertArguments(check, 0, "World!"), MethodHandles.insertArguments(check2, 0, "Hello World!"));
        val = (boolean) result.invokeExact("Hi", "cruel World!");
        assertTrue(val);
        assertEquals(1, counter.get());

        counter.set(0);
        val = (boolean) result.invokeExact("World!", "Goodbye");
        assertTrue(val);
        assertEquals(2, counter.get());

        counter.set(0);
        val = (boolean) result.invokeExact("Hello", "World!");
        assertTrue(val);
        assertEquals(3, counter.get());

        counter.set(0);
        val = (boolean) result.invokeExact("Hello", "cruel World!");
        assertFalse(val);
    }

    @Test
    void testDoNothing() throws Throwable {
        MethodHandle doNothing = Methods.DO_NOTHING;
        doNothing.invokeExact();
    }

    @Test
    void testIsNull() throws Throwable {
        MethodHandle isNull = Methods.nullCheck(String.class);
        boolean test = (boolean) isNull.invokeExact("ABC");
        assertFalse(test);

        test = (boolean) isNull.invokeExact((String) null);
        assertTrue(test);

        isNull = Methods.nullCheck(int.class);
        test = (boolean) isNull.invokeExact(123);
        assertFalse(test);
    }

    @Test
    void testNotNull() throws Throwable {
        MethodHandle isNotNull = Methods.notNullCheck(Object.class);
        boolean test = (boolean) isNotNull.invokeExact((Object) new Date());
        assertTrue(test);

        test = (boolean) isNotNull.invokeExact((Object) null);
        assertFalse(test);

        isNotNull = Methods.notNullCheck(char.class);
        test = (boolean) isNotNull.invokeExact('x');
        assertTrue(test);
    }

    @SuppressWarnings("unused")
    private String concatenate(String head, int number, String middle, boolean bool, String tail) {
        return head + number + middle + bool + tail;
    }

    @SuppressWarnings("unused")
    private void increment(AtomicInteger counter, Object test) {
        counter.incrementAndGet();
    }

    @Test
    void testNullCheck() throws Throwable {
        MethodHandle handle = LOOKUP.bind(this, "concatenate", methodType(String.class, String.class, int.class, String.class, boolean.class, String.class));
        handle = Methods.secureNull(handle);
        String notNull = (String) handle.invokeExact("Num ", 12, " is not ", true, ".");
        assertNotNull(notNull);
        assertEquals("Num 12 is not true.", notNull);

        String isNull = (String) handle.invokeExact("Num ", 12, " is not ", true, (String) null);
        assertNull(isNull);

        isNull = (String) handle.invokeExact((String) null, 12, " is not ", true, ".");
        assertNull(isNull);

        isNull = (String) handle.invokeExact((String) null, 12, (String) null, true, (String) null);
        assertNull(isNull);

        handle = LOOKUP.findVirtual(String.class, "length", methodType(int.class));
        handle = Methods.secureNull(handle);
        int l = (int) handle.invokeExact("123");
        assertEquals(3, l);
        l = (int) handle.invokeExact((String) null);
        assertEquals(0, l);

        handle = LOOKUP.findVirtual(List.class, "isEmpty", methodType(boolean.class));
        handle = Methods.secureNull(handle);
        boolean isEmpty = (boolean) handle.invokeExact(Collections.emptyList());
        assertTrue(isEmpty);
        isEmpty = (boolean) handle.invokeExact((List) null);
        assertFalse(isEmpty);

        handle = LOOKUP.bind(this, "increment", methodType(void.class, AtomicInteger.class, Object.class));
        handle = Methods.secureNull(handle);
        AtomicInteger counter = new AtomicInteger();
        handle.invokeExact(counter, new Object());
        assertEquals(1, counter.get());

        counter = new AtomicInteger();
        handle.invokeExact(counter, (Object) null);
        assertEquals(0, counter.get());
    }

    @Test
    void testSecureArgument() throws Throwable {
        @SuppressWarnings({"unchecked", "rawTypes"})
        MethodHandle addToList = MethodLocator.findUsing(List.class, p -> p.add(null));
        MethodHandle check = LOOKUP.findVirtual(String.class, "startsWith", methodType(boolean.class, String.class));
        check = MethodHandles.insertArguments(check, 1, "Foo");
        MethodHandle dontAddFoo = Methods.rejectIfArgument(addToList, 1, check);

        List<String> list = new ArrayList<>();
        boolean added = (boolean) dontAddFoo.invokeExact(list, (Object) "Hello");
        assertTrue(added);
        added = (boolean) dontAddFoo.invokeExact(list, (Object) "Foobar");
        assertFalse(added);
        added = (boolean) dontAddFoo.invokeExact(list, (Object) "World");
        assertTrue(added);

        assertEquals(2, list.size());
        assertEquals("Hello", list.get(0));
        assertEquals("World", list.get(1));
        assertFalse(list.contains("Foobar"));
    }

    @Test
    void testAssertNotNull() throws Throwable {
        @SuppressWarnings({"unchecked", "rawTypes"})
        MethodHandle addToList = MethodLocator.findUsing(List.class, p -> p.add(null));
        addToList = Methods.assertNotNull(addToList, 1, "blubber");
        List<Object> list = new ArrayList<>();

        assertTrue((boolean) addToList.invokeExact(list, (Object) "Not null")); // Should not fail
        try {
            assertTrue((boolean) addToList.invokeExact(list, (Object) null));
        } catch (NullPointerException npe) {
            assertTrue(npe.getMessage().contains("blubber"));
            return;
        }

        fail("Should have thrown NPE");
    }

    @Test
    void testAssertNotNullOwnException() {
        @SuppressWarnings({"unchecked", "rawTypes"})
        MethodHandle addToList = MethodLocator.findUsing(List.class, p -> p.add(null));
        MethodHandle nullChecked = Methods.assertNotNull(addToList, FileNotFoundException.class, "This was a test");
        List<Object> list = new ArrayList<>();

        assertThrows(FileNotFoundException.class,
                () -> assertTrue((boolean) nullChecked.invokeExact(list, (Object) null)));
    }

    @Test
    void testReturnEmpty() throws Throwable {
        Date date = new Date();
        MethodHandle getTime = publicLookup().findVirtual(Date.class, "getTime", methodType(long.class));
        MethodHandle returnEmpty = Methods.returnEmptyValue(getTime, long.class);
        long time = (long) returnEmpty.invokeExact(date);
        assertEquals(0L, time);

        returnEmpty = Methods.returnEmptyValue(getTime, String.class);
        String emptyString = (String) returnEmpty.invokeExact(date);
        assertNull(emptyString);

        returnEmpty = Methods.returnEmptyValue(getTime, boolean.class);
        boolean falseValue = (boolean) returnEmpty.invokeExact(date);
        assertFalse(falseValue);

        returnEmpty = Methods.returnEmptyValue(getTime, void.class);
        returnEmpty.invokeExact(date);
    }

    @Test
    void testReturnArgument() throws Throwable {
        Date date = new Date();
        MethodHandle setTime = publicLookup().findVirtual(Date.class, "setTime", methodType(void.class, long.class));
        MethodHandle returnArg = Methods.returnArgument(setTime, 1);
        long timeSet = (long) returnArg.invokeExact(date, 1000000L);
        assertEquals(1000000L, timeSet);
        assertEquals(1000000L, date.getTime());

        returnArg = Methods.returnArgument(setTime, 0);
        Date itself = (Date) returnArg.invokeExact(date, 999999999L);
        assertEquals(itself, date);
        assertEquals(999999999L, itself.getTime());

        // Silly way: Instead of returning the timestamp, return the instance itself
        MethodHandle getTime = publicLookup().findVirtual(Date.class, "getTime", methodType(long.class));
        returnArg = Methods.returnArgument(getTime, 0);
        itself = (Date) returnArg.invokeExact(date);
        assertEquals(itself, date);
    }

    @Test
    void testInstanceCheck() throws Throwable {
        MethodHandle instanceCheck = Methods.instanceCheck(String.class);
        boolean isInstance = (boolean) instanceCheck.invokeExact((Object) 1848);
        assertFalse(isInstance, "Number is not String");
        isInstance = (boolean) instanceCheck.invokeExact((Object) "Some string");
        assertTrue(isInstance, "String is a String");
        isInstance = (boolean) instanceCheck.invokeExact((Object) null);
        assertFalse(isInstance, "null is not a String");
    }

    @Test
    void testInstanceCheckIsNotNullCheckForObject() throws Throwable {
        MethodHandle instanceCheck = Methods.instanceCheck(Object.class);
        boolean isInstance = (boolean) instanceCheck.invokeExact((Object) 1848);
        assertTrue(isInstance, "Number is not null");
        isInstance = (boolean) instanceCheck.invokeExact((Object) "Some string");
        assertTrue(isInstance, "String is not null");
        isInstance = (boolean) instanceCheck.invokeExact((Object) null);
        assertFalse(isInstance, "null check");
    }

    @SuppressWarnings("unused")
    private MethodHandle countCalls(MethodHandle h, AtomicInteger counter) throws NoSuchMethodException, IllegalAccessException {
        MethodHandle increment = publicLookup().bind(counter, "incrementAndGet", methodType(int.class));
        increment = increment.asType(methodType(void.class));
        return MethodHandles.foldArguments(h, increment);
    }

    @Test
    void testMethodTypesEqual() {
        MethodType prototype = methodType(Date.class, CharSequence.class, int.class);
        Comparison compare = Methods.compare(prototype, methodType(Date.class, CharSequence.class, int.class));
        assertEquals(Comparison.EQUAL, compare);
        compare = Methods.compare(prototype, methodType(Date.class, CharSequence.class, Integer.class));
        assertNotEquals(Comparison.EQUAL, compare);
    }

    @Test
    void testMethodTypesMoreGeneric() {
        MethodType prototype = methodType(Date.class, String.class, int.class);
        Comparison compare = Methods.compare(prototype, methodType(Timestamp.class, CharSequence.class, Number.class));
        assertEquals(Comparison.MORE_GENERIC, compare);
        compare = Methods.compare(prototype, methodType(Date.class, String.class, Integer.class));
        assertEquals(Comparison.MORE_GENERIC, compare);
        prototype = methodType(Number.class);
        compare = Methods.compare(prototype, methodType(Integer.class));
        assertEquals(Comparison.MORE_GENERIC, compare);
        prototype = methodType(Integer.class);
        compare = Methods.compare(prototype, methodType(int.class));
        assertEquals(Comparison.MORE_GENERIC, compare);
    }

    @Test
    void testMethodTypesMoreSpecific() {
        MethodType prototype = methodType(Date.class, CharSequence.class, Integer.class);
        Comparison compare = Methods.compare(prototype, methodType(Date.class, CharSequence.class, int.class));
        assertEquals(Comparison.MORE_SPECIFIC, compare);
        compare = Methods.compare(prototype, methodType(Object.class, String.class, Integer.class));
        assertEquals(Comparison.MORE_SPECIFIC, compare);
        prototype = methodType(Integer.class);
        compare = Methods.compare(prototype, methodType(Number.class));
        assertEquals(Comparison.MORE_SPECIFIC, compare);
        prototype = methodType(int.class);
        compare = Methods.compare(prototype, methodType(Integer.class));
        assertEquals(Comparison.MORE_SPECIFIC, compare);
    }

    @Test
    void testMethodTypesConvertable() {
        MethodType prototype = methodType(Date.class, CharSequence.class, Integer.class);
        Comparison compare = Methods.compare(prototype, methodType(Date.class, Object.class, int.class));
        assertEquals(Comparison.CONVERTABLE, compare);
        compare = Methods.compare(prototype, methodType(Object.class, Object.class, Object.class));
        assertEquals(Comparison.CONVERTABLE, compare);
    }

    @Test
    void testMethodTypesIncomatible() {
        MethodType prototype = methodType(Date.class, CharSequence.class, Integer.class);
        Comparison compare = Methods.compare(prototype, methodType(Date.class, Date.class, int.class));
        assertEquals(Comparison.INCOMPATIBLE, compare);
        compare = Methods.compare(prototype, methodType(TimeUnit.class, CharSequence.class, Integer.class));
        assertEquals(Comparison.INCOMPATIBLE, compare);
    }

    @Test
    void testMethodTypesLessArguments() {
        MethodType prototype = methodType(Date.class, CharSequence.class, Integer.class);
        Comparison compare = Methods.compare(prototype, methodType(Date.class, CharSequence.class));
        assertEquals(Comparison.LESS_ARGUMENTS, compare);
    }

    @Test
    void testMethodTypesMoreArguments() {
        MethodType prototype = methodType(Date.class);
        Comparison compare = Methods.compare(prototype, methodType(Date.class, float.class));
        assertEquals(Comparison.MORE_ARGUMENTS, compare);
    }

    @Test
    void testFindSingleMethod() throws Throwable {
        final AtomicReference<String> ref = new AtomicReference<>();
        MethodHandle handle = Methods.findMethodHandleOfType(LOOKUP, new Object() {
            @SuppressWarnings("unused")
            void set(String value) {
                ref.set(value);
            }
        }, methodType(void.class, String.class));
        assertEquals(methodType(void.class, String.class), handle.type());
        handle.invokeExact("Hello world.");
        assertEquals("Hello world.", ref.get());

        handle = Methods.findMethodHandleOfType(LOOKUP, new Object() {
            @SuppressWarnings("unused")
            void set(String value) {
                ref.set(value);
            }
        }, methodType(void.class, Object.class));
        assertEquals(methodType(void.class, Object.class), handle.type());
        handle.invokeExact((Object) "Hello world.");
        assertEquals("Hello world.", ref.get());
    }

    @Test
    void testFindSingleMethodFailed() {
        assertThrows(NoSuchMethodError.class,
                () -> Methods.findMethodHandleOfType(LOOKUP, ActionListener.class, methodType(String.class)));
    }

    @Test
    void findSingleMethodWithNewInstance() throws Throwable {
        MethodHandle handle = Methods.findMethodHandleOfType(publicLookup(), StringBuilder.class,
                methodType(StringBuilder.class, String.class));
        StringBuilder sb1 = (StringBuilder) handle.invokeExact("First");
        assertEquals("First", sb1.toString());
        StringBuilder sb2 = (StringBuilder) handle.invokeExact(" Second");
        assertEquals("First Second", sb2.toString());
        assertEquals(sb1, sb2);
    }

    @Test
    void findSingleMethodWithSameInstanceOnEveryCall() throws Throwable {
        StringBuilder sb = new StringBuilder("Initial ");
        MethodHandle handle = Methods.findMethodHandleOfType(publicLookup(), sb,
                methodType(StringBuilder.class, String.class));
        StringBuilder sb1 = (StringBuilder) handle.invokeExact("First");
        assertSame(sb1, sb);
        assertEquals("Initial First", sb1.toString());
        StringBuilder sb2 = (StringBuilder) handle.invokeExact(" Second");
        assertEquals("Initial First Second", sb2.toString());
        assertEquals(sb1, sb2);
    }

    @SuppressWarnings("unused")
    static class WithThreeMethods {
        static String returnHello() {
            return "Hello";
        }

        private WithThreeMethods(boolean dontAllowEmptyConstructor) {

        }

        String returnString(String input) {
            return input + " string";
        }

        CharSequence returnCharSequence(CharSequence input) {
            return input;
        }
    }

    @Test
    void findSingleBestMatchingMethod() throws Throwable {
        // Also checks that no new instance ist created because returnHello() is static
        MethodHandle handle = Methods.findMethodHandleOfType(LOOKUP, WithThreeMethods.class, methodType(String.class));
        String s = (String) handle.invokeExact();
        assertEquals("Hello", s);

        // First find the exact match
        handle = Methods.findMethodHandleOfType(LOOKUP, new WithThreeMethods(true),
                methodType(String.class, String.class));
        s = (String) handle.invokeExact("My");
        assertEquals("My string", s);

        // then find the more generic one
        handle = Methods.findMethodHandleOfType(LOOKUP, new WithThreeMethods(true),
                methodType(Object.class, CharSequence.class));
        Object o = handle.invokeExact((CharSequence) "My");
        assertEquals("My", o);
    }

    @Test
    void testFindSingleMethodAmbiguous() {
        // Both possible methods are more generic
        assertThrows(AmbiguousMethodException.class,
                () -> Methods.findMethodHandleOfType(LOOKUP, WithThreeMethods.class, methodType(CharSequence.class, String.class)));
    }

    @Test
    void testSynchronize() throws Throwable {
        Semaphore p = new Semaphore(1);
        AtomicReference<String> ref = new AtomicReference<>("Initial");
        MethodHandle getValue = publicLookup().bind(ref, "get", methodType(Object.class));
        MethodHandle guarded = Methods.synchronizeWith(getValue, p);
        Object val = guarded.invokeExact();
        assertEquals("Initial", val);
        assertEquals(1, p.availablePermits());
    }

    @SuppressWarnings("unused")
    private static void check(AtomicReference<?> ref, Object boom) {
        if (ref.get().equals(boom)) {
            throw new IllegalArgumentException("Boom!");
        }
    }

    @Test
    void testSynchronizeWithException() throws Throwable {
        Semaphore p = new Semaphore(1);
        AtomicReference<String> ref = new AtomicReference<>("Initial");
        MethodHandle checkValue = LOOKUP.findStatic(LOOKUP.lookupClass(), "check", methodType(void.class, AtomicReference.class, Object.class));
        MethodHandle guarded = Methods.synchronizeWith(checkValue, p);
        guarded.invokeExact(ref, (Object) "not now");
        assertEquals(1, p.availablePermits());

        ref.set("Boom");
        try {
            guarded.invokeExact(ref, (Object) "Boom");
        } catch (IllegalArgumentException ex) {
            assertEquals(1, p.availablePermits());
            return;
        }
        fail("Exception expected");
    }

    @SuppressWarnings("unused")
    private static String sleepAndCompare(long sleepInMillis, AtomicReference<String> ref, String newValue) throws InterruptedException {
        TimeUnit.MILLISECONDS.sleep(sleepInMillis);
        return ref.getAndSet(newValue);
    }

    @Test
    void testSynchronizeConcurrent() throws Throwable {
        AtomicReference<String> ref = new AtomicReference<>("Initial");
        MethodHandle sleepAndCompare = LOOKUP.findStatic(LOOKUP.lookupClass(), "sleepAndCompare", methodType(String.class, long.class, AtomicReference.class, String.class));
        MethodHandle guarded = Methods.synchronize(sleepAndCompare);
        Thread thread = new Thread(() -> {
            try {
                String currentValue = (String) guarded.invokeExact(300L, ref, "Set by thread");
                assertEquals("Initial", currentValue);
            } catch (Throwable t) {
                fail(t.getMessage());
            }
        });
        thread.start();

        Thread.yield();
        TimeUnit.MILLISECONDS.sleep(50);
        String currentValue = (String) guarded.invokeExact(100L, ref, "Set by main method");
        thread.join();
        assertEquals("Set by thread", currentValue);
    }

    @Test
    void testFinallyNormalExecution() throws Throwable {
        MethodHandle parseInt = publicLookup().findStatic(Integer.class, "parseInt", methodType(int.class, String.class));
        AtomicReference<String> ref = new AtomicReference<>();
        MethodHandle set = publicLookup().bind(ref, "set", methodType(void.class, Object.class));

        MethodHandle parseAndSet = Methods.doFinally(parseInt, set);
        int result = (int) parseAndSet.invokeExact("123");
        assertEquals(123, result);
        assertEquals("123", ref.get());

        result = (int) parseAndSet.invokeExact("99669933");
        assertEquals(99669933, result);
        assertEquals("99669933", ref.get());
    }

    @Test
    void testFinallyWithException() throws Throwable {
        MethodHandle parseInt = publicLookup().findStatic(Integer.class, "parseInt", methodType(int.class, String.class));
        AtomicReference<String> ref = new AtomicReference<>();
        MethodHandle set = publicLookup().bind(ref, "set", methodType(void.class, Object.class));

        MethodHandle parseAndSet = Methods.doFinally(parseInt, set);
        int result = (int) parseAndSet.invokeExact("123");
        assertEquals(123, result);
        assertEquals("123", ref.get());

        try {
            result = (int) parseAndSet.invokeExact("Not a number");
        } catch (NumberFormatException ex) {
            assertEquals(123, result);
            assertEquals("Not a number", ref.get());
            return;
        }
        fail("Should not be here");
    }

    @SuppressWarnings("unused")
    private String manyArguments(int a1, String a2, Object a3, boolean[] a4, float a5, TimeUnit a6) {
        return a1 + a2 + a3 + a4[0] + a5 + a6;
    }

    @Test
    void testShiftUnchanged() throws Throwable {
        MethodHandle h = LOOKUP.bind(this, "manyArguments", methodType(String.class, int.class, String.class, Object.class, boolean[].class, float.class, TimeUnit.class));
        for (int i=0; i < h.type().parameterCount(); i++) {
            MethodHandle shifted = Methods.shiftArgument(h, i, i);
            assertEquals(h, shifted);
        }
    }

    @Test
    void testShiftLeft() throws Throwable {
        MethodHandle h = LOOKUP.bind(this, "manyArguments", methodType(String.class, int.class, String.class, Object.class, boolean[].class, float.class, TimeUnit.class));

        MethodHandle shifted = Methods.shiftArgument(h, 1, 0);
        assertEquals(methodType(String.class, String.class, int.class, Object.class, boolean[].class, float.class, TimeUnit.class), shifted.type());
        String result = (String) shifted.invokeExact("b", 17, (Object)'!', new boolean[] { true }, 0.8f, TimeUnit.MICROSECONDS);
        assertEquals("17b!true0.8MICROSECONDS", result);

        shifted = Methods.shiftArgument(h, 4, 2);
        assertEquals(methodType(String.class, int.class, String.class, float.class, Object.class, boolean[].class, TimeUnit.class), shifted.type());
        result = (String) shifted.invokeExact(17, "b", 0.8f, (Object)'!', new boolean[] { true }, TimeUnit.MICROSECONDS);
        assertEquals("17b!true0.8MICROSECONDS", result);

        shifted = Methods.shiftArgument(h, 5, 4);
        assertEquals(methodType(String.class, int.class, String.class, Object.class, boolean[].class, TimeUnit.class, float.class), shifted.type());
        result = (String) shifted.invokeExact(17, "b", (Object)'!', new boolean[] { true }, TimeUnit.MICROSECONDS, 0.8f);
        assertEquals("17b!true0.8MICROSECONDS", result);
    }

    @Test
    void testShiftRight() throws Throwable {
        MethodHandle h = LOOKUP.bind(this, "manyArguments", methodType(String.class, int.class, String.class, Object.class, boolean[].class, float.class, TimeUnit.class));

        MethodHandle shifted = Methods.shiftArgument(h, 0, 1);
        assertEquals(methodType(String.class, String.class, int.class, Object.class, boolean[].class, float.class, TimeUnit.class), shifted.type());
        String result = (String) shifted.invokeExact("b", 17, (Object)'!', new boolean[] { true }, 0.8f, TimeUnit.MICROSECONDS);
        assertEquals("17b!true0.8MICROSECONDS", result);

        shifted = Methods.shiftArgument(h, 2, 4);
        assertEquals(methodType(String.class, int.class, String.class, boolean[].class, float.class, Object.class, TimeUnit.class), shifted.type());
        result = (String) shifted.invokeExact(17, "b", new boolean[] { true }, 0.8f, (Object)'!', TimeUnit.MICROSECONDS);
        assertEquals("17b!true0.8MICROSECONDS", result);

        shifted = Methods.shiftArgument(h, 4, 5);
        assertEquals(methodType(String.class, int.class, String.class, Object.class, boolean[].class, TimeUnit.class, float.class), shifted.type());
        result = (String) shifted.invokeExact(17, "b", (Object)'!', new boolean[] { true }, TimeUnit.MICROSECONDS, 0.8f);
        assertEquals("17b!true0.8MICROSECONDS", result);
    }

    @SuppressWarnings("unused")
    private static void add(AtomicInteger counter, Integer summand) {
        counter.addAndGet(summand);
    }

    @SuppressWarnings("unused")
    private static void add(AtomicInteger counter, int summand) {
        counter.addAndGet(summand);
    }

    private static class ContractBreakingIterable<T> implements Iterable<T> {
        private final Iterable<T> iterable;
        private final UnaryOperator<T> operator;

        ContractBreakingIterable(Iterable<T> iterable, UnaryOperator<T> operator) {
            this.iterable = iterable;
            this.operator = operator;
        }

        @Override
        public Iterator<T> iterator() {
            return iterable.iterator();
        }

        @Override
        public void forEach(Consumer<? super T> action) {
            iterable.forEach(x -> action.accept(operator.apply(x)));
        }
    }

    @Test
    void testIteratorWithForEachCall() throws Throwable {
        AtomicInteger counter = new AtomicInteger();
        MethodHandle sum = LOOKUP.findStatic(LOOKUP.lookupClass(), "add", methodType(void.class, AtomicInteger.class, Integer.class));
        List<Integer> numbers = Arrays.asList(33, 27, 0, -10);
        Iterable<Integer> iterable = new ContractBreakingIterable<>(numbers, x -> x + x);
        MethodHandle iterator = Methods.iterate(LOOKUP, sum, 1);
        iterator.invokeExact(counter, iterable);
        assertEquals(100, counter.get());

        counter = new AtomicInteger();
        iterator = Methods.iterate(LOOKUP, sum, 1, counter);
        iterator.invokeExact(iterable);
        assertEquals(100, counter.get());
    }

    @Test
    void testIteratorWithPrimitive() throws Throwable {
        AtomicInteger counter = new AtomicInteger();
        MethodHandle sum = LOOKUP.findStatic(LOOKUP.lookupClass(), "add", methodType(void.class, AtomicInteger.class, int.class));
        List<Integer> numbers = Arrays.asList(33, 27, 0, -10);
        Iterable<Integer> iterable = new ContractBreakingIterable<>(numbers, x -> x + x);
        MethodHandle iterator = Methods.iterate(LOOKUP, sum, 1);
        iterator.invokeExact(counter, iterable);
        assertEquals(50, counter.get());

        counter = new AtomicInteger();
        iterator = Methods.iterate(LOOKUP, sum, 1, counter);
        iterator.invokeExact(iterable);
        assertEquals(50, counter.get());
    }

    @Test
    void testIteratorWithIterator() throws Throwable {
        AtomicInteger counter = new AtomicInteger();
        MethodHandle sum = publicLookup().findVirtual(AtomicInteger.class, "addAndGet", methodType(int.class, int.class));
        List<Integer> numbers = Arrays.asList(33, 27, 0, -10);
        Iterable<Integer> iterable = new ContractBreakingIterable<>(numbers, x -> x + x);
        MethodHandle iterator = Methods.iterate(sum, 1);
        iterator.invokeExact(counter, iterable);
        assertEquals(50, counter.get());

        counter = new AtomicInteger();
        iterator = Methods.iterate(sum, 1, counter);
        iterator.invokeExact(iterable);
        assertEquals(50, counter.get());
    }

    private static class Calculator {
        int result;

        void sumUp(int v1, String v2, Long v3) {
            result += v1;
            result += Integer.parseInt(v2);
            result += v3;
        }
    }

    @Test
    void testIterateMultipleArguments() throws Throwable {
        MethodHandle sum = LOOKUP.findVirtual(Calculator.class, "sumUp", methodType(void.class, int.class, String.class, Long.class));
        List<Long> vals = Arrays.asList(20L, 30L, 40L, 100L, -10L);
        Iterable<Long> it = new ContractBreakingIterable<>(vals, x -> x + x);

        MethodHandle loop = Methods.iterate(LOOKUP, sum, 3);
        Calculator c = new Calculator();
        loop.invokeExact(c, 17, "11", it);
        assertEquals((17+11)*5 + 40 + 60 + 80 + 200 - 20, c.result);
        c = new Calculator();
        c.result = 1000;
        loop.invokeExact(c, 1, "-20", it);
        assertEquals(1000 - 19*5 + 40 + 60 + 80 + 200 - 20, c.result);

        c = new Calculator();
        loop = Methods.iterate(LOOKUP, sum, 3, c);
        loop.invokeExact(5, "9", it);
        assertEquals((5+9)*5 + 40 + 60 + 80 + 200 - 20, c.result);

        c = new Calculator();
        loop = Methods.iterate(LOOKUP, sum, 3, c, 18);
        loop.invokeExact("7", it);
        assertEquals((7+18)*5 + 40 + 60 + 80 + 200 - 20, c.result);

        c = new Calculator();
        loop = Methods.iterate(LOOKUP, sum, 3, c, 12, "99");
        loop.invokeExact(it);
        assertEquals((12+99)*5 + 40 + 60 + 80 + 200 - 20, c.result);

        // Now we switch to iterator style
        List<String> vals2 = Arrays.asList("1", "2", "3");
        Iterable<String> it2 = new ContractBreakingIterable<>(vals2, x -> x + "0");

        c = new Calculator();
        loop = Methods.iterate(LOOKUP, sum, 2);
        loop.invokeExact(c, 11, it2, Long.valueOf(99L));
        assertEquals((11+99)*3 + 1 + 2 + 3, c.result);

        c = new Calculator();
        loop = Methods.iterate(LOOKUP, sum, 2, c);
        loop.invokeExact(11, it2, Long.valueOf(99L));
        assertEquals((11+99)*3 + 1 + 2 + 3, c.result);

        c = new Calculator();
        loop = Methods.iterate(LOOKUP, sum, 2, c, 5);
        loop.invokeExact(it2, Long.valueOf(23L));
        assertEquals((23+5)*3 + 1 + 2 + 3, c.result);

        c = new Calculator();
        loop = Methods.iterate(LOOKUP, sum, 2, c, 237, 46L);
        loop.invokeExact(it2);
        assertEquals((237+46)*3 + 1 + 2 + 3, c.result);

        Iterable<Integer> vals3 = Arrays.asList(11, 22, 33, 44);

        c = new Calculator();
        loop = Methods.iterate(LOOKUP, sum, 1);
        loop.invokeExact(c, vals3, "98", Long.valueOf(703L));
        assertEquals((98+703)*4 + 11 + 22 + 33 + 44, c.result);

        c = new Calculator();
        loop = Methods.iterate(LOOKUP, sum, 1, c);
        loop.invokeExact(vals3, "315", Long.valueOf(65841L));
        assertEquals((315+65841)*4 + 11 + 22 + 33 + 44, c.result);

        c = new Calculator();
        loop = Methods.iterate(LOOKUP, sum, 1, c, "344");
        loop.invokeExact(vals3, Long.valueOf(16L));
        assertEquals((344+16)*4 + 11 + 22 + 33 + 44, c.result);

        c = new Calculator();
        loop = Methods.iterate(LOOKUP, sum, 1, c, "2", 30L);
        loop.invokeExact(vals3);
        assertEquals((30+2)*4 + 11 + 22 + 33 + 44, c.result);

        Calculator c1 = new Calculator();
        Calculator c2 = new Calculator();
        Calculator c3 = new Calculator();
        Calculator c4 = new Calculator();
        c1.result = 10;
        c2.result = 20;
        c3.result = 30;
        c4.result = 40;
        Iterable<Calculator> vals4 = Arrays.asList(c1, c2, c3, c4);

        loop = Methods.iterate(LOOKUP, sum, 0);
        loop.invokeExact(vals4, -18, "44", Long.valueOf(77));
        assertEquals(10 - 18 + 44 + 77, c1.result);
        assertEquals(20 - 18 + 44 + 77, c2.result);
        assertEquals(30 - 18 + 44 + 77, c3.result);
        assertEquals(40 - 18 + 44 + 77, c4.result);

        c1.result = 10;
        c2.result = 20;
        c3.result = 30;
        c4.result = 40;
        loop = Methods.iterate(LOOKUP, sum, 0, -171);
        loop.invokeExact(vals4, "-543", Long.valueOf(2828));
        assertEquals(10 - 171 - 543 + 2828, c1.result);
        assertEquals(20 - 171 - 543 + 2828, c2.result);
        assertEquals(30 - 171 - 543 + 2828, c3.result);
        assertEquals(40 - 171 - 543 + 2828, c4.result);

        c1.result = 10;
        c2.result = 20;
        c3.result = 30;
        c4.result = 40;
        loop = Methods.iterate(LOOKUP, sum, 0, -11, "2046");
        loop.invokeExact(vals4, Long.valueOf(1183));
        assertEquals(10 - 11 + 2046 + 1183, c1.result);
        assertEquals(20 - 11 + 2046 + 1183, c2.result);
        assertEquals(30 - 11 + 2046 + 1183, c3.result);
        assertEquals(40 - 11 + 2046 + 1183, c4.result);

        c1.result = 10;
        c2.result = 20;
        c3.result = 30;
        c4.result = 40;
        loop = Methods.iterate(LOOKUP, sum, 0, 199, "1284", 3333L);
        loop.invokeExact(vals4);
        assertEquals(10 + 199 + 1284 + 3333, c1.result);
        assertEquals(20 + 199 + 1284 + 3333, c2.result);
        assertEquals(30 + 199 + 1284 + 3333, c3.result);
        assertEquals(40 + 199 + 1284 + 3333, c4.result);
    }

    @Test
    void testIterateWithFailedChecks() {
        MethodHandle oneArgument = MethodHandles.identity(Object.class);
        assertThrows(IllegalArgumentException.class,
                () -> Methods.iterate(oneArgument, -1)); // Index too low
        assertThrows(IllegalArgumentException.class,
                () -> Methods.iterate(oneArgument, 1)); // Index too high
        assertThrows(IllegalArgumentException.class,
                () -> Methods.iterate(oneArgument, 0, new Object())); // Too many leading arguments
    }

    @Test
    void testIterateArray() throws Throwable {
        MethodHandle sum = LOOKUP.findVirtual(Calculator.class, "sumUp", methodType(void.class, int.class, String.class, Long.class));

        MethodHandle loop = Methods.iterateArray(sum, 3);
        Calculator c = new Calculator();
        loop.invoke(c, 17, "11", 20L, 30L, 40L, 100L, -10L);
        assertEquals((17+11)*5 + 20 + 30 + 40 + 100 - 10, c.result);
        c = new Calculator();
        c.result = 1000;
        loop.invoke(c, 1, "-20", 20L, 30L, 40L, 100L, -10L);
        assertEquals(1000 - 19*5 + 20 + 30 + 40 + 100 - 10, c.result);

        c = new Calculator();
        loop = Methods.iterateArray(sum, 2);
        loop.invokeExact(c, 11, new String[] {"1", "2", "3"}, Long.valueOf(99L));
        assertEquals((11+99)*3 + 1 + 2 + 3, c.result);

        c = new Calculator();
        loop = Methods.iterateArray(sum, 1);
        loop.invokeExact(c, new int[] {11, 22, 33, 44}, "98", Long.valueOf(703L));
        assertEquals((98+703)*4 + 11 + 22 + 33 + 44, c.result);

        Calculator c1 = new Calculator();
        Calculator c2 = new Calculator();
        Calculator c3 = new Calculator();
        Calculator c4 = new Calculator();
        c1.result = 10;
        c2.result = 20;
        c3.result = 30;
        c4.result = 40;
        Calculator[] vals4 = {c1, c2, c3, c4};

        loop = Methods.iterateArray(sum, 0);
        loop.invokeExact(vals4, -18, "44", Long.valueOf(77));
        assertEquals(10 - 18 + 44 + 77, c1.result);
        assertEquals(20 - 18 + 44 + 77, c2.result);
        assertEquals(30 - 18 + 44 + 77, c3.result);
        assertEquals(40 - 18 + 44 + 77, c4.result);
    }

    @Test
    void testCollectStringArray() throws Throwable {
        MethodHandle concat = publicLookup().findVirtual(String.class, "concat", methodType(String.class, String.class));
        MethodHandle concatAll = Methods.collectArray(concat, 1);
        assertTrue(concatAll.isVarargsCollector());
        String[] result = (String[]) concatAll.invokeExact("Hello ", "World,Fred,Melissa,Luisa".split(","));
        assertArrayEquals(new String[] {"Hello World", "Hello Fred", "Hello Melissa", "Hello Luisa"}, result);

        concatAll = Methods.collectArray(concat, 0);
        assertFalse(concatAll.isVarargsCollector());
        result = (String[]) concatAll.invokeExact("Hello,Hi,Ciao".split(","), " Melissa");
        assertArrayEquals(new String[] {"Hello Melissa", "Hi Melissa", "Ciao Melissa"}, result);
    }

    @Test
    void testCollectLongArray() throws Throwable {
        MethodHandle addExact = publicLookup().findStatic(Math.class, "addExact", methodType(long.class, long.class, long.class));
        MethodHandle addAll = Methods.collectArray(addExact, 1);
        assertTrue(addAll.isVarargsCollector());
        long[] result = (long[]) addAll.invoke(100, 10, 20, 30);
        assertArrayEquals(new long[] {110, 120, 130}, result);

        addAll = Methods.collectArray(addExact, 0);
        assertFalse(addAll.isVarargsCollector());
        result = (long[]) addAll.invokeExact(new long[] {5, 9, -222, 2046}, 1103L);
        assertArrayEquals(new long[] {1108, 1112, 1103-222, 1103+2046}, result);
    }

    @Test
    void testCollectIntArray() throws Throwable {
        MethodHandle length = publicLookup().findVirtual(String.class, "length", methodType(int.class));
        MethodHandle lengthAll = Methods.collectArray(length, 0);
        assertTrue(lengthAll.isVarargsCollector());
        int[] lengths = (int[]) lengthAll.invoke("123", "", "123456789012345");
        assertArrayEquals(new int[] {3, 0, 15}, lengths);

        MethodHandle addExact = publicLookup().findStatic(Math.class, "addExact", methodType(int.class, int.class, int.class));
        addExact = addExact.asSpreader(int[].class, 2);
        addExact = MethodHandles.filterArguments(addExact, 0, lengthAll);
        addExact = addExact.asCollector(String[].class, 2);
        // Note: In real world, it doesn't make sense to use an array transformation here. You'd better just filter both arguments.

        int sumOfLengths = (int) addExact.invokeExact("12345", "1234");
        assertEquals(9, sumOfLengths);
    }

    @Test
    void testIterateEmptyArray() throws Throwable {
        MethodHandle fail = MethodHandles.throwException(boolean.class, AssertionError.class);
        fail = MethodHandles.filterArguments(fail, 0, publicLookup().findConstructor(AssertionError.class, methodType(void.class, Object.class)));
        fail = MethodHandles.dropArguments(fail, 1, int.class);
        MethodHandle failAll = Methods.iterateArray(fail, 1);
        failAll.invokeExact((Object) "Should not get called!", new int[0]);

        failAll = Methods.collectArray(fail, 1);
        boolean[] noresults = (boolean[]) failAll.invokeExact((Object) "Should not get called!", new int[0]);
        assertEquals(0, noresults.length);
    }

    @Test
    void testReduceValue() throws Throwable {
        MethodHandle addExact = publicLookup().findStatic(Math.class, "addExact", methodType(int.class, int.class, int.class));
        MethodHandle addCollect = Methods.reduceArray(addExact, 1, 0);
        assertTrue(addCollect.isVarargsCollector());
        addCollect = makeHandleAcceptCommaSeparatedString(addCollect, 1);
        int multiSum = (int) addCollect.invokeExact(10, "18,99,-17,-1055");
        assertEquals(10+18+99-17-1055, multiSum);

        addCollect = Methods.reduceArray(addExact, 0, 1);
        assertFalse(addCollect.isVarargsCollector());
        addCollect = makeHandleAcceptCommaSeparatedString(addCollect, 0);
        multiSum = (int) addCollect.invokeExact("7,45,1103", 55);
        assertEquals(7+45+1103+55, multiSum);

        MethodHandle concat = publicLookup().findVirtual(String.class, "concat", methodType(String.class, String.class));
        MethodHandle concatAll = Methods.reduceArray(concat, 1, 0);
        String statement = (String) concatAll.invokeExact("", "I ,love ,Java".split(","));
        assertEquals("I love Java", statement);
    }

    @Test
    void testReduceValueWithDifferentTypes() throws Throwable {
        MethodHandle handle = LOOKUP.bind(this, "doSomething", methodType(int.class, Object.class, Integer.class, String.class, int.class));
        MethodHandle multi = Methods.reduceArray(handle, 0, 1);
        assertEquals(methodType(int.class, Object[].class, Integer.class, String.class, int.class), multi.type());
        int result = (int) multi.invokeExact(new Object[] {1, 2, 3}, (Integer) 4, "5", 6);
        int expected = 1+2*4+3*5+4*6;
        expected = 2+2*expected+3*5+4*6;
        expected = 3+2*expected+3*5+4*6;
        assertEquals(expected, result);

        multi = Methods.reduceArray(handle, 0, 3);
        assertEquals(methodType(int.class, Object[].class, Integer.class, String.class, int.class), multi.type());
        result = (int) multi.invokeExact(new Object[] {1, 2, 3}, (Integer) 4, "5", 6);
        expected = 1+2*4+3*5+4*6;
        expected = 2+2*4+3*5+4*expected;
        expected = 3+2*4+3*5+4*expected;
        assertEquals(expected, result);

        multi = Methods.reduceArray(handle, 1, 0);
        assertEquals(methodType(int.class, Object.class, Integer[].class, String.class, int.class), multi.type());
        result = (int) multi.invokeExact((Object) 1, new Integer[] {2, 3, 4}, "5", 6);
        expected = 1+2*2+3*5+4*6;
        expected = expected+2*3+3*5+4*6;
        expected = expected+2*4+3*5+4*6;
        assertEquals(expected, result);

        multi = Methods.reduceArray(handle, 1, 3);
        assertEquals(methodType(int.class, Object.class, Integer[].class, String.class, int.class), multi.type());
        result = (int) multi.invokeExact((Object) 1, new Integer[] {2, 3, 4}, "5", 6);
        expected = 1+2*2+3*5+4*6;
        expected = 1+2*3+3*5+4*expected;
        expected = 1+2*4+3*5+4*expected;
        assertEquals(expected, result);

        multi = Methods.reduceArray(handle, 2, 0);
        assertEquals(methodType(int.class, Object.class, Integer.class, String[].class, int.class), multi.type());
        result = (int) multi.invokeExact((Object) 1, (Integer) 2, "3,4,5".split(","), 6);
        expected = 1+2*2+3*3+4*6;
        expected = expected+2*2+3*4+4*6;
        expected = expected+2*2+3*5+4*6;
        assertEquals(expected, result);

        multi = Methods.reduceArray(handle, 2, 1);
        assertEquals(methodType(int.class, Object.class, Integer.class, String[].class, int.class), multi.type());
        result = (int) multi.invokeExact((Object) 1, (Integer) 2, "3,4,5".split(","), 6);
        expected = 1+2*2+3*3+4*6;
        expected = 1+2*expected+3*4+4*6;
        expected = 1+2*expected+3*5+4*6;
        assertEquals(expected, result);

        multi = Methods.reduceArray(handle, 2, 3);
        assertEquals(methodType(int.class, Object.class, Integer.class, String[].class, int.class), multi.type());
        result = (int) multi.invokeExact((Object) 1, (Integer) 2, "3,4,5".split(","), 6);
        expected = 1+2*2+3*3+4*6;
        expected = 1+2*2+3*4+4*expected;
        expected = 1+2*2+3*5+4*expected;
        assertEquals(expected, result);

        multi = Methods.reduceArray(handle, 3, 0);
        assertEquals(methodType(int.class, Object.class, Integer.class, String.class, int[].class), multi.type());
        result = (int) multi.invokeExact((Object) 1, (Integer) 2, "3", new int[] {4, 5, 6});
        expected = 1+2*2+3*3+4*4;
        expected = expected+2*2+3*3+4*5;
        expected = expected+2*2+3*3+4*6;
        assertEquals(expected, result);

        multi = Methods.reduceArray(handle, 3, 1);
        assertEquals(methodType(int.class, Object.class, Integer.class, String.class, int[].class), multi.type());
        result = (int) multi.invokeExact((Object) 1, (Integer) 2, "3", new int[] {4, 5, 6});
        expected = 1+2*2+3*3+4*4;
        expected = 1+2*expected+3*3+4*5;
        expected = 1+2*expected+3*3+4*6;
        assertEquals(expected, result);
    }

    @Test
    void testReduceValueWithInitial() throws Throwable {
        MethodHandle addExact = publicLookup().findStatic(Math.class, "addExact", methodType(int.class, int.class, int.class));
        MethodHandle addCollect = Methods.reduceArray(addExact, 1, 0, null);
        assertTrue(addCollect.isVarargsCollector());
        addCollect = makeHandleAcceptCommaSeparatedString(addCollect, 0);
        int multiSum = (int) addCollect.invokeExact("18,99,-17,-1055");
        assertEquals(18+99-17-1055, multiSum);

        addCollect = Methods.reduceArray(addExact, 0, 1, null);
        assertFalse(addCollect.isVarargsCollector());
        addCollect = makeHandleAcceptCommaSeparatedString(addCollect, 0);
        multiSum = (int) addCollect.invokeExact("7,45,1103");
        assertEquals(7+45+1103, multiSum);

        MethodHandle concat = publicLookup().findVirtual(String.class, "concat", methodType(String.class, String.class));
        MethodHandle concatAll = Methods.reduceArray(concat, 1, 0, "");
        String statement = (String) concatAll.invokeExact("I ,love ,Java".split(","));
        assertEquals("I love Java", statement);
    }

    @Test
    void testReduceValueWithDifferentTypesWithInitial() throws Throwable {
        MethodHandle handle = LOOKUP.bind(this, "doSomething", methodType(int.class, Object.class, Integer.class, String.class, int.class));
        MethodHandle multi = Methods.reduceArray(handle, 0, 1, 4);
        assertEquals(methodType(int.class, Object[].class, String.class, int.class), multi.type());
        int result = (int) multi.invokeExact(new Object[] {1, 2, 3}, "5", 6);
        int expected = 1+2*4+3*5+4*6;
        expected = 2+2*expected+3*5+4*6;
        expected = 3+2*expected+3*5+4*6;
        assertEquals(expected, result);

        multi = Methods.reduceArray(handle, 0, 3, 6);
        assertEquals(methodType(int.class, Object[].class, Integer.class, String.class), multi.type());
        result = (int) multi.invokeExact(new Object[] {1, 2, 3}, (Integer) 4, "5");
        expected = 1+2*4+3*5+4*6;
        expected = 2+2*4+3*5+4*expected;
        expected = 3+2*4+3*5+4*expected;
        assertEquals(expected, result);

        multi = Methods.reduceArray(handle, 1, 0, 1);
        assertEquals(methodType(int.class, Integer[].class, String.class, int.class), multi.type());
        result = (int) multi.invokeExact(new Integer[] {2, 3, 4}, "5", 6);
        expected = 1+2*2+3*5+4*6;
        expected = expected+2*3+3*5+4*6;
        expected = expected+2*4+3*5+4*6;
        assertEquals(expected, result);

        multi = Methods.reduceArray(handle, 1, 3, 6);
        assertEquals(methodType(int.class, Object.class, Integer[].class, String.class), multi.type());
        result = (int) multi.invokeExact((Object) 1, new Integer[] {2, 3, 4}, "5");
        expected = 1+2*2+3*5+4*6;
        expected = 1+2*3+3*5+4*expected;
        expected = 1+2*4+3*5+4*expected;
        assertEquals(expected, result);

        multi = Methods.reduceArray(handle, 2, 0, 1);
        assertEquals(methodType(int.class, Integer.class, String[].class, int.class), multi.type());
        result = (int) multi.invokeExact((Integer) 2, "3,4,5".split(","), 6);
        expected = 1+2*2+3*3+4*6;
        expected = expected+2*2+3*4+4*6;
        expected = expected+2*2+3*5+4*6;
        assertEquals(expected, result);

        multi = Methods.reduceArray(handle, 2, 1, 2);
        assertEquals(methodType(int.class, Object.class, String[].class, int.class), multi.type());
        result = (int) multi.invokeExact((Object) 1, "3,4,5".split(","), 6);
        expected = 1+2*2+3*3+4*6;
        expected = 1+2*expected+3*4+4*6;
        expected = 1+2*expected+3*5+4*6;
        assertEquals(expected, result);

        multi = Methods.reduceArray(handle, 2, 3, 6);
        assertEquals(methodType(int.class, Object.class, Integer.class, String[].class), multi.type());
        result = (int) multi.invokeExact((Object) 1, (Integer) 2, "3,4,5".split(","));
        expected = 1+2*2+3*3+4*6;
        expected = 1+2*2+3*4+4*expected;
        expected = 1+2*2+3*5+4*expected;
        assertEquals(expected, result);

        multi = Methods.reduceArray(handle, 3, 0, 1);
        assertEquals(methodType(int.class, Integer.class, String.class, int[].class), multi.type());
        result = (int) multi.invokeExact((Integer) 2, "3", new int[] {4, 5, 6});
        expected = 1+2*2+3*3+4*4;
        expected = expected+2*2+3*3+4*5;
        expected = expected+2*2+3*3+4*6;
        assertEquals(expected, result);

        multi = Methods.reduceArray(handle, 3, 1, 2);
        assertEquals(methodType(int.class, Object.class, String.class, int[].class), multi.type());
        result = (int) multi.invokeExact((Object) 1, "3", new int[] {4, 5, 6});
        expected = 1+2*2+3*3+4*4;
        expected = 1+2*expected+3*3+4*5;
        expected = 1+2*expected+3*3+4*6;
        assertEquals(expected, result);
    }

    @SuppressWarnings("unused")
    private int doSomething(Object object, Integer integer, String string, int xint) {
        return ((Integer)object) + integer*2 + Integer.parseInt(string)*3 + xint*4;
    }

    private MethodHandle makeHandleAcceptCommaSeparatedString(MethodHandle target, int index) throws NoSuchMethodException, IllegalAccessException {
        MethodHandle stringToInt = publicLookup().findStatic(Integer.class, "parseInt", methodType(int.class, String.class));
        MethodHandle split = publicLookup().findVirtual(String.class, "split", methodType(String[].class, String.class));
        split = MethodHandles.insertArguments(split, 1, ",");
        MethodHandle stringsToInts = Methods.collectArray(stringToInt, 0);
        stringsToInts = MethodHandles.filterArguments(stringsToInts, 0, split);
        return MethodHandles.filterArguments(target, index, stringsToInts);
    }

    @Test
    void testCalculateMedian() throws Throwable {
        MethodHandle add = publicLookup().findVirtual(BigDecimal.class, "add", methodType(BigDecimal.class, BigDecimal.class));
        MethodHandle fromString = publicLookup().findConstructor(BigDecimal.class, methodType(void.class, String.class));
        add = MethodHandles.filterArguments(add, 1, fromString);
        MethodHandle sumOfAll = Methods.reduceArray(add, 1, 0, BigDecimal.ZERO);
        MethodHandle divide = publicLookup().findVirtual(BigDecimal.class, "divide", methodType(BigDecimal.class, BigDecimal.class));
        MethodHandle fromInt = publicLookup().findStatic(BigDecimal.class, "valueOf", methodType(BigDecimal.class, long.class)).asType(methodType(BigDecimal.class, int.class));
        divide = MethodHandles.filterArguments(divide, 1, fromInt);
        divide = MethodHandles.filterArguments(divide, 1, MethodHandles.arrayLength(String[].class));
        MethodHandle median = MethodHandles.foldArguments(divide, sumOfAll);
        MethodHandle split = publicLookup().findVirtual(String.class, "split", methodType(String[].class, String.class));
        split = MethodHandles.insertArguments(split, 1, ",");
        median = MethodHandles.filterArguments(median, 0, split);

        BigDecimal result = (BigDecimal) median.invokeExact("1,17.4,-11.667,25.11014,3.141598,11.03");
        assertEquals(new BigDecimal("7.669123"), result);
    }

    @Test
    void testLambdaFactory() throws Throwable {
        MethodHandle mult = publicLookup().findStatic(Math.class, "multiplyExact", methodType(int.class, int.class, int.class));
        MethodHandle lambdaFactory = Methods.createLambdaFactory(LOOKUP, mult, IntUnaryOperator.class);
        assertNotNull(lambdaFactory);

        IntUnaryOperator multWithTen = (IntUnaryOperator) lambdaFactory.invokeExact(10);
        assertTrue(Methods.wasLambdafiedDirect(multWithTen));
        int result = multWithTen.applyAsInt(1103);
        assertEquals(1103 * 10, result);

        IntUnaryOperator multWithNine = (IntUnaryOperator) lambdaFactory.invokeExact(9);
        assertTrue(Methods.wasLambdafiedDirect(multWithNine));
        result = multWithNine.applyAsInt(99);
        assertEquals(99 * 9, result);
    }

    @Test
    void testLambdaFactoryWithMarkers() throws Throwable {
        MethodHandle mult = publicLookup().findStatic(Math.class, "multiplyExact", methodType(int.class, int.class, int.class));
        MethodHandle lambdaFactory = Methods.createLambdaFactory(LOOKUP, mult, IntBinaryOperator.class,
                ThreadSafe.class, Override.class); // Hehe, annotations as marker interfaces
        assertNotNull(lambdaFactory);

        IntBinaryOperator multiply = (IntBinaryOperator) lambdaFactory.invokeExact();
        assertTrue(Methods.wasLambdafiedDirect(multiply));
        assertTrue(multiply instanceof ThreadSafe);
        assertTrue(multiply instanceof Override);
    }

    @SuppressWarnings("unused")
    private static String sumUp(int a, Integer b) {
        return String.valueOf(a + b);
    }

    interface SumToString {
        Object sumUpNonsense(int a, Object b);
    }

    @Test
    void testLambdaFactoryWithConversion() throws Throwable {
        MethodHandle sum = LOOKUP.findStatic(LOOKUP.lookupClass(), "sumUp", methodType(String.class, int.class, Integer.class));
        MethodHandle lambdaFactory = Methods.createLambdaFactory(LOOKUP, sum, SumToString.class);
        assertNotNull(lambdaFactory);

        SumToString demo = (SumToString) lambdaFactory.invokeExact();
        assertTrue(Methods.wasLambdafiedDirect(demo));
        assertFalse(MethodHandleProxies.isWrapperInstance(demo));
        Object result = demo.sumUpNonsense(5, 6);
        assertEquals("11", result);
    }

    @Test
    void testLambdaFactoryNotDirect() throws NoSuchMethodException, IllegalAccessException {
        MethodHandle sum = LOOKUP.findStatic(LOOKUP.lookupClass(), "sumUp", methodType(String.class, int.class, Integer.class));
        MethodHandle modified = MethodHandles.dropArguments(sum, 1, String.class);
        MethodHandle lambdaFactory = Methods.createLambdaFactory(LOOKUP, modified, SumToString.class);
        assertNull(lambdaFactory);
    }

    @Test
    void testLambdafy() throws Throwable {
        MethodHandle mult = publicLookup().findStatic(Math.class, "multiplyExact", methodType(int.class, int.class, int.class));
        IntUnaryOperator doubleIt = Methods.lambdafy(mult, IntUnaryOperator.class, 2);
        assertFalse(MethodHandleProxies.isWrapperInstance(doubleIt));
        assertEquals(36, doubleIt.applyAsInt(18));

        IntBinaryOperator multiply = Methods.lambdafy(mult, IntBinaryOperator.class);
        assertFalse(MethodHandleProxies.isWrapperInstance(multiply));
        assertTrue(Methods.wasLambdafiedDirect(multiply));
        assertEquals(77 * 19, multiply.applyAsInt(77, 19));
    }

    @Test
    void testLambdafyNotDirect() throws Throwable {
        MethodHandle mult = publicLookup().findStatic(Math.class, "multiplyExact", methodType(int.class, int.class, int.class));
        MethodHandle modified = MethodHandles.dropArguments(mult, 1, String.class);
        IntUnaryOperator doubleIt = Methods.lambdafy(modified, IntUnaryOperator.class, 2, "Ignored");
        assertTrue(MethodHandleProxies.isWrapperInstance(doubleIt));
        assertFalse(Methods.wasLambdafiedDirect(doubleIt));
        assertEquals(36, doubleIt.applyAsInt(18));

        modified = MethodHandles.insertArguments(mult, 1, 5);
        IntUnaryOperator multiplyWithFive = Methods.lambdafy(modified, IntUnaryOperator.class);
        assertTrue(MethodHandleProxies.isWrapperInstance(multiplyWithFive));
        assertFalse(Methods.wasLambdafiedDirect(multiplyWithFive));
        assertEquals(220, multiplyWithFive.applyAsInt(44));
    }

    private static class StringLoop {
        private final int count;

        StringLoop(int count) {
            this.count = count;
        }

        @SuppressWarnings("unused")
        String loop(CharSequence input) {
            StringBuilder sb = new StringBuilder(input.length() * count);
            for (int i=0; i < count; i++) {
                sb.append(input);
            }
            return sb.toString();
        }
    }

    public interface OperatorFactory {
        UnaryOperator<String> createWithCount(int count);
    }

    @Test
    void testLambdaInFull() throws Throwable {
        MethodHandle loop = LOOKUP.findVirtual(StringLoop.class, "loop", methodType(String.class, CharSequence.class));
        MethodHandle lambdaFactory = Methods.createLambdaFactory(LOOKUP, loop, UnaryOperator.class);
        assertNotNull(lambdaFactory);

        MethodHandle constructor = LOOKUP.findConstructor(StringLoop.class, methodType(void.class, int.class));
        MethodHandle withIntArgument = MethodHandles.filterArguments(lambdaFactory, 0, constructor);
        OperatorFactory factory = Methods.lambdafy(withIntArgument, OperatorFactory.class);

        UnaryOperator<String> oneTime = factory.createWithCount(1);
        String result = oneTime.apply("fiolino");
        assertEquals("fiolino", result);

        UnaryOperator<String> fiveTimes = factory.createWithCount(5);
        result = fiveTimes.apply("fiolino");
        assertEquals("fiolinofiolinofiolinofiolinofiolino", result);

        UnaryOperator<String> twelveTimes = factory.createWithCount(12);
        result = twelveTimes.apply("xyz");
        assertEquals("xyzxyzxyzxyzxyzxyzxyzxyzxyzxyzxyzxyz", result);
    }
}
