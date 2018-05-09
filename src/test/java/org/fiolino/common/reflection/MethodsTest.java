package org.fiolino.common.reflection;

import org.junit.jupiter.api.Test;

import javax.annotation.concurrent.ThreadSafe;
import java.awt.event.ActionListener;
import java.io.FileNotFoundException;
import java.lang.invoke.*;
import java.lang.reflect.Field;
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
import static java.lang.invoke.MethodHandles.whileLoop;
import static java.lang.invoke.MethodType.methodType;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Created by Michael Kuhlmann on 15.12.2015.
 */
class MethodsTest {

    private static final MethodHandles.Lookup LOOKUP = lookup();

    @SuppressWarnings("unused")
    private static class Bean {
        int value;

        int getValue() {
            return value / 10;
        }

        void setValue(int value) {
            this.value = value * 10;
        }

        int getValue(int plus) {
            return value + plus;
        }

        void setValue(int value, int multi) {
            this.value = value * multi;
        }
    }

    @Test
    void testFindGetter() throws Throwable {
        Field field = Bean.class.getDeclaredField("value");
        MethodHandle handle = Methods.findGetter(LOOKUP, field);
        assertNotNull(handle);

        Bean bean = new Bean();
        bean.value = 1230;
        int result = (int) handle.invokeExact(bean);
        assertEquals(123, result);
    }

    @Test
    void testFindSetter() throws Throwable {
        Field field = Bean.class.getDeclaredField("value");
        MethodHandle handle = Methods.findSetter(LOOKUP, field);
        assertNotNull(handle);

        Bean bean = new Bean();
        handle.invokeExact(bean, 588);
        assertEquals(5880, bean.value);
    }

    @Test
    void testFindGetterWithInt() throws Throwable {
        Field field = Bean.class.getDeclaredField("value");
        MethodHandle handle = Methods.findGetter(LOOKUP, field, int.class);
        assertNotNull(handle);

        Bean bean = new Bean();
        bean.value = 1230;
        int result = (int) handle.invokeExact(bean, 70);
        assertEquals(1300, result);
    }

    @Test
    void testFindSetterWithIntAndString() throws Throwable {
        Field field = Bean.class.getDeclaredField("value");
        MethodHandle handle = Methods.findSetter(LOOKUP, field, int.class, String.class); // String is ignored
        assertNotNull(handle);

        Bean bean = new Bean();
        handle.invokeExact(bean, 13, 2);
        assertEquals(26, bean.value);
    }

    @Test
    void testFindGetterWithIntAndDate() throws Throwable {
        Field field = Bean.class.getDeclaredField("value");
        MethodHandle handle = Methods.findGetter(LOOKUP, field, int.class, Date.class); // Date is ignored
        assertNotNull(handle);

        Bean bean = new Bean();
        bean.value = 55;
        int result = (int) handle.invokeExact(bean, 15);
        assertEquals(70, result);
    }

    @Test
    void testFindSetterWithInt() throws Throwable {
        Field field = Bean.class.getDeclaredField("value");
        MethodHandle handle = Methods.findSetter(LOOKUP, field, int.class);
        assertNotNull(handle);

        Bean bean = new Bean();
        handle.invokeExact(bean, 588, -1);
        assertEquals(-588, bean.value);
    }

    private static class BeanWithoutMethods {
        int value;
    }

    @Test
    void testFindGetterWithoutMethods() throws Throwable {
        Field field = BeanWithoutMethods.class.getDeclaredField("value");
        MethodHandle handle = Methods.findGetter(LOOKUP, field);
        assertNotNull(handle);

        BeanWithoutMethods bean = new BeanWithoutMethods();
        bean.value = 1103;
        int result = (int) handle.invokeExact(bean);
        assertEquals(1103, result);
    }

    @Test
    void testFindSetterWithoutMethods() throws Throwable {
        Field field = BeanWithoutMethods.class.getDeclaredField("value");
        MethodHandle handle = Methods.findSetter(LOOKUP, field);
        assertNotNull(handle);

        BeanWithoutMethods bean = new BeanWithoutMethods();
        handle.invokeExact(bean, 2046);
        assertEquals(2046, bean.value);
    }

    @SuppressWarnings("unused")
    private static class BeanWithBooleanValues {
        boolean isWithIsInGetterAndSetter;
        boolean isWithoutIsInGetterAndSetter;

        boolean getIsWithIsInGetterAndSetter() {
            return isWithIsInGetterAndSetter;
        }

        void setIsWithIsInGetterAndSetter(boolean withIsInGetterAndSetter) {
            isWithIsInGetterAndSetter = withIsInGetterAndSetter;
        }

        boolean isWithoutIsInGetterAndSetter() {
            return isWithoutIsInGetterAndSetter;
        }

        void setWithoutIsInGetterAndSetter(boolean withoutIsInGetterAndSetter) {
            isWithoutIsInGetterAndSetter = withoutIsInGetterAndSetter;
        }
    }

    @Test
    void testFindBooleanWithGetAndIs() throws Throwable {
        Field field = BeanWithBooleanValues.class.getDeclaredField("isWithIsInGetterAndSetter");
        MethodHandle getter = Methods.findGetter(LOOKUP, field);
        assertNotNull(getter);
        MethodHandle setter = Methods.findSetter(LOOKUP, field);
        assertNotNull(setter);

        BeanWithBooleanValues bean = new BeanWithBooleanValues();
        setter.invokeExact(bean, true);
        assertTrue((boolean) getter.invokeExact(bean));
        assertFalse(bean.isWithoutIsInGetterAndSetter());
    }

    @Test
    void testFindBooleanWithoutGetAndIs() throws Throwable {
        Field field = BeanWithBooleanValues.class.getDeclaredField("isWithoutIsInGetterAndSetter");
        MethodHandle getter = Methods.findGetter(LOOKUP, field);
        assertNotNull(getter);
        MethodHandle setter = Methods.findSetter(LOOKUP, field);
        assertNotNull(setter);

        BeanWithBooleanValues bean = new BeanWithBooleanValues();
        setter.invokeExact(bean, true);
        assertTrue((boolean) getter.invokeExact(bean));
        assertFalse(bean.getIsWithIsInGetterAndSetter());
    }

    @Test
    void testFindViaConsumer() throws Throwable {
        @SuppressWarnings("unchecked")
        MethodHandle handle = Methods.findUsing(LOOKUP, List.class, l -> l.add(null));
        assertNotNull(handle);
        List<String> testList = new ArrayList<>();
        boolean added = (boolean) handle.invokeExact(testList, (Object) "Hello World");
        assertTrue(added);
        assertEquals(1, testList.size());
        assertEquals("Hello World", testList.get(0));
    }

    @Test
    void testBindUsing() throws Throwable {
        List<String> testList = new ArrayList<>();
        MethodHandle handle = Methods.bindUsing(LOOKUP, testList, l -> l.add(null)).orElse(null);
        assertNotNull(handle);
        boolean added = (boolean) handle.invokeExact((Object) "Hello World");
        assertTrue(added);
        assertEquals(1, testList.size());
        assertEquals("Hello World", testList.get(0));
    }

    private static class Prototype {
        @MethodFinder
        @SuppressWarnings("unused")
        int createAddHandle(List<Object> someList) {
            someList.add(new Object());
            fail("Should never reach here!");
            return 0;
        }

        @ExecuteDirect
        @SuppressWarnings("unused")
        int getDoubledSize(List<?> someList) {
            return someList.size() * 2;
        }
    }

    @Test
    void testFindUsingPrototype() throws Throwable {
        final MethodHandle[] handles = new MethodHandle[2];
        Methods.findUsing(LOOKUP, new Prototype(), null, (v, m, handleSupplier) -> {
            handles[m.getName().equals("createAddHandle") ? 0 : 1] = handleSupplier.get();
            return null;
        });
        assertNotNull(handles[0]);
        assertNotNull(handles[1]);
        List<String> testList = new ArrayList<>();
        boolean added = (boolean) handles[0].invokeExact(testList, (Object) "Hello World");
        assertTrue(added);
        assertEquals(1, testList.size());
        assertEquals("Hello World", testList.get(0));
        int doubledSize = (int) handles[1].invokeExact(testList);
        assertEquals(2, doubledSize);
    }

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
    private static void booom() throws FileNotFoundException {
        throw new FileNotFoundException("Booom!");
    }
    
    @Test
    void testRethrowExceptionNoArguments() throws Throwable {
        MethodHandle booom = LOOKUP.findStatic(LOOKUP.lookupClass(), "booom", methodType(void.class));
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
        MethodHandle booom = LOOKUP.findStatic(LOOKUP.lookupClass(), "booom", methodType(void.class));
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
    private static void booom(int a, String b) throws FileNotFoundException {
        throw new FileNotFoundException(b);
    }

    @Test
    void testRethrowExceptionWithParameters() throws Throwable {
        MethodHandle booom = LOOKUP.findStatic(LOOKUP.lookupClass(), "booom", methodType(void.class, int.class, String.class));
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
        MethodHandle booom = LOOKUP.findStatic(LOOKUP.lookupClass(), "booom", methodType(void.class, int.class, String.class));
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
        MethodHandle booom = LOOKUP.findStatic(LOOKUP.lookupClass(), "booom", methodType(void.class));
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
        MethodHandle booom = LOOKUP.findStatic(LOOKUP.lookupClass(), "booom", methodType(void.class, int.class, String.class));
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
        MethodHandle booom = LOOKUP.findStatic(LOOKUP.lookupClass(), "booom", methodType(void.class));
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
    private static boolean checkEquals(String first, String second, AtomicInteger counter) {
        counter.incrementAndGet();
        return first.equals(second);
    }

    @SuppressWarnings("unused")
    private static boolean checkEqualsConcatenation(String first, String second, String secondTail, AtomicInteger counter) {
        counter.incrementAndGet();
        return first.equals(second + " " + secondTail);
    }

    @Test
    void testAnd() throws Throwable {
        AtomicInteger counter = new AtomicInteger();
        MethodHandle check = LOOKUP.findStatic(LOOKUP.lookupClass(), "checkEquals", methodType(boolean.class, String.class, String.class, AtomicInteger.class));
        check = MethodHandles.insertArguments(check, 2, counter);
        MethodHandle check2 = LOOKUP.findStatic(LOOKUP.lookupClass(), "checkEqualsConcatenation", methodType(boolean.class, String.class, String.class, String.class, AtomicInteger.class));
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
        MethodHandle check = LOOKUP.findStatic(LOOKUP.lookupClass(), "checkEquals", methodType(boolean.class, String.class, String.class, AtomicInteger.class));
        check = MethodHandles.insertArguments(check, 2, counter);
        MethodHandle check2 = LOOKUP.findStatic(LOOKUP.lookupClass(), "checkEqualsConcatenation", methodType(boolean.class, String.class, String.class, String.class, AtomicInteger.class));
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
    private static String concatenate(String head, int number, String middle, boolean bool, String tail) {
        return head + number + middle + bool + tail;
    }

    @SuppressWarnings("unused")
    private static void increment(AtomicInteger counter, Object test) {
        counter.incrementAndGet();
    }

    @Test
    void testNullCheck() throws Throwable {
        MethodHandle handle = LOOKUP.findStatic(LOOKUP.lookupClass(), "concatenate", methodType(String.class, String.class, int.class, String.class, boolean.class, String.class));
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

        handle = LOOKUP.findStatic(LOOKUP.lookupClass(), "increment", methodType(void.class, AtomicInteger.class, Object.class));
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
        MethodHandle addToList = Methods.findUsing(List.class, p -> p.add(null));
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
        MethodHandle addToList = Methods.findUsing(List.class, p -> p.add(null));
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
        MethodHandle addToList = Methods.findUsing(List.class, p -> p.add(null));
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

    private MethodHandle printInput(MethodHandle h)throws NoSuchMethodException, IllegalAccessException {
        MethodHandle out = publicLookup().bind(System.out, "println", methodType(void.class, h.type().parameterType(0)));
        return MethodHandles.foldArguments(h, out);
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
        assertTrue(sb1 == sb);
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
        MethodHandles.Lookup lookup = lookup();
        MethodHandle sum = lookup().findStatic(lookup.lookupClass(), "add", methodType(void.class, AtomicInteger.class, Integer.class));
        List<Integer> numbers = Arrays.asList(33, 27, 0, -10);
        Iterable<Integer> iterable = new ContractBreakingIterable<>(numbers, x -> x + x);
        MethodHandle iterator = Methods.iterate(lookup, sum, 1);
        iterator.invokeExact(iterable, counter);
        assertEquals(100, counter.get());

        counter = new AtomicInteger();
        iterator = Methods.iterate(lookup, sum, 1, counter);
        iterator.invokeExact(iterable);
        assertEquals(100, counter.get());
    }

    @Test
    void testIteratorWithPrimitive() throws Throwable {
        AtomicInteger counter = new AtomicInteger();
        MethodHandles.Lookup lookup = lookup();
        MethodHandle sum = lookup().findStatic(lookup.lookupClass(), "add", methodType(void.class, AtomicInteger.class, int.class));
        List<Integer> numbers = Arrays.asList(33, 27, 0, -10);
        Iterable<Integer> iterable = new ContractBreakingIterable<>(numbers, x -> x + x);
        MethodHandle iterator = Methods.iterate(lookup, sum, 1);
        iterator.invokeExact(iterable, counter);
        assertEquals(50, counter.get());

        counter = new AtomicInteger();
        iterator = Methods.iterate(lookup, sum, 1, counter);
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
        iterator.invokeExact(iterable, counter);
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
        MethodHandles.Lookup lookup = lookup();
        MethodHandle sum = lookup.findVirtual(Calculator.class, "sumUp", methodType(void.class, int.class, String.class, Long.class));
        List<Long> vals = Arrays.asList(20L, 30L, 40L, 100L, -10L);
        Iterable<Long> it = new ContractBreakingIterable<>(vals, x -> x + x);

        MethodHandle loop = Methods.iterate(lookup, sum, 3);
        Calculator c = new Calculator();
        loop.invokeExact(it, c, 17, "11");
        assertEquals(28*5 + 40 + 60 + 80 + 200 - 20, c.result);
        c = new Calculator();
        c.result = 1000;
        loop.invokeExact(it, c, 1, "-20");
        assertEquals(1000 - 19*5 + 40 + 60 + 80 + 200 - 20, c.result);

        c = new Calculator();
        loop = Methods.iterate(lookup, sum, 3, c);
        loop.invokeExact(it, 5, "9");
        assertEquals(14*5 + 40 + 60 + 80 + 200 - 20, c.result);

        c = new Calculator();
        loop = Methods.iterate(lookup, sum, 3, c, 18);
        loop.invokeExact(it, "7");
        assertEquals(25*5 + 40 + 60 + 80 + 200 - 20, c.result);

        c = new Calculator();
        loop = Methods.iterate(lookup, sum, 3, c, 12, "99");
        loop.invokeExact(it);
        assertEquals(111*5 + 40 + 60 + 80 + 200 - 20, c.result);

        // Now we switch to iterator style
        List<String> vals2 = Arrays.asList("1", "2", "3");
        Iterable<String> it2 = new ContractBreakingIterable<>(vals2, x -> x + "0");

        c = new Calculator();
        loop = Methods.iterate(lookup, sum, 2);
        loop.invokeExact(it2, c, 11, Long.valueOf(99L));
        assertEquals(110*3 + 1 + 2 + 3, c.result);

        c = new Calculator();
        loop = Methods.iterate(lookup, sum, 2, c);
        loop.invokeExact(it2, 11, Long.valueOf(99L));
        assertEquals(110*3 + 1 + 2 + 3, c.result);

        c = new Calculator();
        loop = Methods.iterate(lookup, sum, 2, c, 5);
        loop.invokeExact(it2, Long.valueOf(23L));
        assertEquals(28*3 + 1 + 2 + 3, c.result);

        c = new Calculator();
        loop = Methods.iterate(lookup, sum, 2, c, 237, 46L);
        loop.invokeExact(it2);
        assertEquals((237+46)*3 + 1 + 2 + 3, c.result);

        Calculator c1 = new Calculator();
        Calculator c2 = new Calculator();
        Calculator c3 = new Calculator();
        Calculator c4 = new Calculator();
        c1.result = 10;
        c2.result = 20;
        c3.result = 30;
        c4.result = 40;
        Iterable<Calculator> vals3 = Arrays.asList(c1, c2, c3, c4);

        loop = Methods.iterate(lookup, sum, 0);
        loop.invokeExact(vals3, -18, "44", Long.valueOf(77));
        assertEquals(10 - 18 + 44 + 77, c1.result);
        assertEquals(20 - 18 + 44 + 77, c2.result);
        assertEquals(30 - 18 + 44 + 77, c3.result);
        assertEquals(40 - 18 + 44 + 77, c4.result);

        c1.result = 10;
        c2.result = 20;
        c3.result = 30;
        c4.result = 40;
        loop = Methods.iterate(lookup, sum, 0, -171);
        loop.invokeExact(vals3, "-543", Long.valueOf(2828));
        assertEquals(10 - 171 - 543 + 2828, c1.result);
        assertEquals(20 - 171 - 543 + 2828, c2.result);
        assertEquals(30 - 171 - 543 + 2828, c3.result);
        assertEquals(40 - 171 - 543 + 2828, c4.result);

        c1.result = 10;
        c2.result = 20;
        c3.result = 30;
        c4.result = 40;
        loop = Methods.iterate(lookup, sum, 0, -11, "2046");
        loop.invokeExact(vals3, Long.valueOf(1183));
        assertEquals(10 - 11 + 2046 + 1183, c1.result);
        assertEquals(20 - 11 + 2046 + 1183, c2.result);
        assertEquals(30 - 11 + 2046 + 1183, c3.result);
        assertEquals(40 - 11 + 2046 + 1183, c4.result);

        c1.result = 10;
        c2.result = 20;
        c3.result = 30;
        c4.result = 40;
        loop = Methods.iterate(lookup, sum, 0, 199, "1284", 3333L);
        loop.invokeExact(vals3);
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
    void testLambdaFactory() throws Throwable {
        MethodHandle mult = publicLookup().findStatic(Math.class, "multiplyExact", methodType(int.class, int.class, int.class));
        MethodHandle lambdaFactory = Methods.createLambdaFactory(LOOKUP, mult, IntUnaryOperator.class);
        assertNotNull(lambdaFactory);

        IntUnaryOperator multWithTen = (IntUnaryOperator) lambdaFactory.invokeExact(10);
        assertTrue(Methods.wasLambdafiedDirect(multWithTen));
        int result = multWithTen.applyAsInt(1103);
        assertEquals(11030, result);

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
