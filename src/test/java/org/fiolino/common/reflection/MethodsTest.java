package org.fiolino.common.reflection;

import org.junit.Test;

import javax.annotation.concurrent.ThreadSafe;
import java.awt.event.ActionListener;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandleProxies;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.IntBinaryOperator;
import java.util.function.IntUnaryOperator;
import java.util.function.UnaryOperator;

import static java.lang.invoke.MethodHandles.lookup;
import static java.lang.invoke.MethodHandles.publicLookup;
import static java.lang.invoke.MethodType.methodType;
import static org.junit.Assert.*;

/**
 * Created by Michael Kuhlmann on 15.12.2015.
 */
public class MethodsTest {

    private static class Bean {
        int value;

        public int getValue() {
            return value / 10;
        }

        public void setValue(int value) {
            this.value = value * 10;
        }

        public int getValue(int plus) {
            return value + plus;
        }

        public void setValue(int value, int multi) {
            this.value = value * multi;
        }
    }

    @Test
    public void testFindGetter() throws Throwable {
        Field field = Bean.class.getDeclaredField("value");
        MethodHandle handle = Methods.findGetter(lookup(), field);
        assertNotNull(handle);

        Bean bean = new Bean();
        bean.value = 1230;
        int result = (int) handle.invokeExact(bean);
        assertEquals(123, result);
    }

    @Test
    public void testFindSetter() throws Throwable {
        Field field = Bean.class.getDeclaredField("value");
        MethodHandle handle = Methods.findSetter(lookup(), field);
        assertNotNull(handle);

        Bean bean = new Bean();
        handle.invokeExact(bean, 588);
        assertEquals(5880, bean.value);
    }

    @Test
    public void testFindGetterWithInt() throws Throwable {
        Field field = Bean.class.getDeclaredField("value");
        MethodHandle handle = Methods.findGetter(lookup(), field, int.class);
        assertNotNull(handle);

        Bean bean = new Bean();
        bean.value = 1230;
        int result = (int) handle.invokeExact(bean, 70);
        assertEquals(1300, result);
    }

    @Test
    public void testFindSetterWithIntAndString() throws Throwable {
        Field field = Bean.class.getDeclaredField("value");
        MethodHandle handle = Methods.findSetter(lookup(), field, int.class, String.class); // String is ignored
        assertNotNull(handle);

        Bean bean = new Bean();
        handle.invokeExact(bean, 13, 2);
        assertEquals(26, bean.value);
    }

    @Test
    public void testFindGetterWithIntAndDate() throws Throwable {
        Field field = Bean.class.getDeclaredField("value");
        MethodHandle handle = Methods.findGetter(lookup(), field, int.class, Date.class); // Date is ignored
        assertNotNull(handle);

        Bean bean = new Bean();
        bean.value = 55;
        int result = (int) handle.invokeExact(bean, 15);
        assertEquals(70, result);
    }

    @Test
    public void testFindSetterWithInt() throws Throwable {
        Field field = Bean.class.getDeclaredField("value");
        MethodHandle handle = Methods.findSetter(lookup(), field, int.class);
        assertNotNull(handle);

        Bean bean = new Bean();
        handle.invokeExact(bean, 588, -1);
        assertEquals(-588, bean.value);
    }

    private static class BeanWithoutMethods {
        int value;
    }

    @Test
    public void testFindGetterWithoutMethods() throws Throwable {
        Field field = BeanWithoutMethods.class.getDeclaredField("value");
        MethodHandle handle = Methods.findGetter(lookup(), field);
        assertNotNull(handle);

        BeanWithoutMethods bean = new BeanWithoutMethods();
        bean.value = 1103;
        int result = (int) handle.invokeExact(bean);
        assertEquals(1103, result);
    }

    @Test
    public void testFindSetterWithoutMethods() throws Throwable {
        Field field = BeanWithoutMethods.class.getDeclaredField("value");
        MethodHandle handle = Methods.findSetter(lookup(), field);
        assertNotNull(handle);

        BeanWithoutMethods bean = new BeanWithoutMethods();
        handle.invokeExact(bean, 2046);
        assertEquals(2046, bean.value);
    }

    @SuppressWarnings("unused")
    private static class BeanWithBooleanValues {
        boolean isWithIsInGetterAndSetter;
        boolean isWithoutIsInGetterAndSetter;

        public boolean getIsWithIsInGetterAndSetter() {
            return isWithIsInGetterAndSetter;
        }

        public void setIsWithIsInGetterAndSetter(boolean withIsInGetterAndSetter) {
            isWithIsInGetterAndSetter = withIsInGetterAndSetter;
        }

        public boolean isWithoutIsInGetterAndSetter() {
            return isWithoutIsInGetterAndSetter;
        }

        public void setWithoutIsInGetterAndSetter(boolean withoutIsInGetterAndSetter) {
            isWithoutIsInGetterAndSetter = withoutIsInGetterAndSetter;
        }
    }

    @Test
    public void testFindBooleanWithGetAndIs() throws Throwable {
        Field field = BeanWithBooleanValues.class.getDeclaredField("isWithIsInGetterAndSetter");
        MethodHandle getter = Methods.findGetter(lookup(), field);
        assertNotNull(getter);
        MethodHandle setter = Methods.findSetter(lookup(), field);
        assertNotNull(setter);

        BeanWithBooleanValues bean = new BeanWithBooleanValues();
        setter.invokeExact(bean, true);
        assertTrue((boolean) getter.invokeExact(bean));
        assertFalse(bean.isWithoutIsInGetterAndSetter());
    }

    @Test
    public void testFindBooleanWithoutGetAndIs() throws Throwable {
        Field field = BeanWithBooleanValues.class.getDeclaredField("isWithoutIsInGetterAndSetter");
        MethodHandle getter = Methods.findGetter(lookup(), field);
        assertNotNull(getter);
        MethodHandle setter = Methods.findSetter(lookup(), field);
        assertNotNull(setter);

        BeanWithBooleanValues bean = new BeanWithBooleanValues();
        setter.invokeExact(bean, true);
        assertTrue((boolean) getter.invokeExact(bean));
        assertFalse(bean.getIsWithIsInGetterAndSetter());
    }

    @Test
    public void testFindViaConsumer() throws Throwable {
        @SuppressWarnings("unchecked")
        MethodHandle handle = Methods.findUsing(lookup(), List.class, new MethodFinderCallback<List>() {
            @Override
            @SuppressWarnings("unchecked")
            public void callMethodFrom(List l) throws Exception {
                l.add(null);
            }
        });
        assertNotNull(handle);
        List<String> testList = new ArrayList<>();
        boolean added = (boolean) handle.invokeExact(testList, (Object) "Hello World");
        assertTrue(added);
        assertEquals(1, testList.size());
        assertEquals("Hello World", testList.get(0));
    }

    @Test
    public void testBindToConsumer() throws Throwable {
        List<String> testList = new ArrayList<>();
        MethodHandle handle = Methods.bindUsing(lookup(), testList, new MethodFinderCallback<Object>() {
            @Override
            public void callMethodFrom(Object l) throws Exception {
                ((List<?>) l).add(null);
            }
        });
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
    public void testFindUsingPrototype() throws Throwable {
        final MethodHandle[] handles = new MethodHandle[2];
        Methods.findUsing(lookup(), new Prototype(), null, (v, m, handleSupplier) -> {
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
    public void testEnumConversion() throws Throwable {
        MethodHandle handle = Methods.convertStringToEnum(TimeUnit.class);
        assertNotNull(handle);
        TimeUnit unit = (TimeUnit) handle.invokeExact("DAYS");
        assertEquals(TimeUnit.DAYS, unit);
    }

    @Test
    public void testEnumConversionWithNullValue() throws Throwable {
        MethodHandle handle = Methods.convertStringToEnum(TimeUnit.class);
        assertNotNull(handle);
        TimeUnit unit = (TimeUnit) handle.invokeExact((String) null);
        assertNull(unit);
    }

    @Test
    public void testEnumConversionWithNotExisting() throws Throwable {
        final AtomicInteger check = new AtomicInteger();
        ExceptionHandler<IllegalArgumentException> handler = new ExceptionHandler<IllegalArgumentException>() {
            @Override
            public void handleNotExisting(IllegalArgumentException exception, Class<?> valueType, Class<?> outputType, Object inputValue) throws Throwable {
                assertTrue(exception instanceof IllegalArgumentException);
                assertEquals(TimeUnit.class, outputType);
                assertEquals("Boom!", inputValue);
                check.set(199);
            }
        };

        MethodHandle handle = Methods.convertStringToEnum(TimeUnit.class, handler);
        assertNotNull(handle);
        TimeUnit unit = (TimeUnit) handle.invokeExact("Boom!");
        assertNull(unit);
        assertEquals(199, check.intValue());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testEnumConversionWithException() throws Throwable {
        MethodHandle handle = Methods.convertStringToEnum(TimeUnit.class);
        assertNotNull(handle);
        TimeUnit unit = (TimeUnit) handle.invokeExact("Boom!");
        fail("Should not be here!");
    }

    @Test
    public void testEnumToStringNoSpecial() throws Throwable {
        MethodHandle handle = Methods.convertEnumToString(TimeUnit.class, (f, u) -> null);
        for (TimeUnit u : TimeUnit.values()) {
            String name = (String) handle.invokeExact(u);
            assertEquals(u.name(), name);
        }
    }

    @Test
    public void testEnumToStringWithSpecial() throws Throwable {
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
    public void testAnd() throws Throwable {
        AtomicInteger counter = new AtomicInteger();
        MethodHandle check = lookup().findStatic(MethodsTest.class, "checkEquals", methodType(boolean.class, String.class, String.class, AtomicInteger.class));
        check = MethodHandles.insertArguments(check, 2, counter);
        MethodHandle check2 = lookup().findStatic(MethodsTest.class, "checkEqualsConcatenation", methodType(boolean.class, String.class, String.class, String.class, AtomicInteger.class));
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
    public void testOr() throws Throwable {
        AtomicInteger counter = new AtomicInteger();
        MethodHandle check = lookup().findStatic(MethodsTest.class, "checkEquals", methodType(boolean.class, String.class, String.class, AtomicInteger.class));
        check = MethodHandles.insertArguments(check, 2, counter);
        MethodHandle check2 = lookup().findStatic(MethodsTest.class, "checkEqualsConcatenation", methodType(boolean.class, String.class, String.class, String.class, AtomicInteger.class));
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

    @SuppressWarnings("unused")
    private static String concatenate(String head, int number, String middle, boolean bool, String tail) {
        return head + number + middle + bool + tail;
    }

    @SuppressWarnings("unused")
    private static void increment(AtomicInteger counter, Object test) {
        counter.incrementAndGet();
    }

    @Test
    public void testNullCheck() throws Throwable {
        MethodHandle handle = lookup().findStatic(MethodsTest.class, "concatenate", methodType(String.class, String.class, int.class, String.class, boolean.class, String.class));
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

        handle = lookup().findVirtual(String.class, "length", methodType(int.class));
        handle = Methods.secureNull(handle);
        int l = (int) handle.invokeExact("123");
        assertEquals(3, l);
        l = (int) handle.invokeExact((String) null);
        assertEquals(0, l);

        handle = lookup().findVirtual(List.class, "isEmpty", methodType(boolean.class));
        handle = Methods.secureNull(handle);
        boolean isEmpty = (boolean) handle.invokeExact(Collections.emptyList());
        assertTrue(isEmpty);
        isEmpty = (boolean) handle.invokeExact((List) null);
        assertFalse(isEmpty);

        handle = lookup().findStatic(MethodsTest.class, "increment", methodType(void.class, AtomicInteger.class, Object.class));
        handle = Methods.secureNull(handle);
        AtomicInteger counter = new AtomicInteger();
        handle.invokeExact(counter, new Object());
        assertEquals(1, counter.get());

        counter = new AtomicInteger();
        handle.invokeExact(counter, (Object) null);
        assertEquals(0, counter.get());
    }

    @Test
    public void testSecureArgument() throws Throwable {
        MethodHandle addToList = Methods.findUsing(lookup(), List.class, new MethodFinderCallback<List>() {
            @Override
            @SuppressWarnings("unchecked")
            public void callMethodFrom(List prototype) throws Throwable {
                prototype.add(null);
            }
        });
        MethodHandle check = lookup().findVirtual(String.class, "startsWith", methodType(boolean.class, String.class));
        check = MethodHandles.insertArguments(check, 1, "Foo");
        MethodHandle dontAddFoo = Methods.rejectIfArgument(addToList, 1, check);

        List<String> list = new ArrayList<>();
        boolean drop = (boolean) dontAddFoo.invokeExact(list, (Object) "Hello");
        drop = (boolean) dontAddFoo.invokeExact(list, (Object) "Foobar");
        drop = (boolean) dontAddFoo.invokeExact(list, (Object) "World");

        assertEquals(2, list.size());
        assertEquals("Hello", list.get(0));
        assertEquals("World", list.get(1));
        assertFalse(list.contains("Foobar"));
    }

    @Test
    public void testReturnEmpty() throws Throwable {
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
    public void testReturnArgument() throws Throwable {
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
    public void testInstanceCheck() throws Throwable {
        MethodHandle instanceCheck = Methods.instanceCheck(String.class);
        boolean isInstance = (boolean) instanceCheck.invokeExact((Object) 1848);
        assertFalse("Number is not String", isInstance);
        isInstance = (boolean) instanceCheck.invokeExact((Object) "Some string");
        assertTrue("String is a String", isInstance);
        isInstance = (boolean) instanceCheck.invokeExact((Object) null);
        assertFalse("null is not a String", isInstance);
    }

    @Test
    public void testInstanceCheckIsNotNullCheckForObject() throws Throwable {
        MethodHandle instanceCheck = Methods.instanceCheck(Object.class);
        boolean isInstance = (boolean) instanceCheck.invokeExact((Object) 1848);
        assertTrue("Number is not null", isInstance);
        isInstance = (boolean) instanceCheck.invokeExact((Object) "Some string");
        assertTrue("String is not null", isInstance);
        isInstance = (boolean) instanceCheck.invokeExact((Object) null);
        assertFalse("null check", isInstance);
    }

    @Test
    public void testMethodTypesEqual() {
        MethodType prototype = methodType(Date.class, CharSequence.class, int.class);
        Comparison compare = Methods.compare(prototype, methodType(Date.class, CharSequence.class, int.class));
        assertEquals(Comparison.EQUAL, compare);
        compare = Methods.compare(prototype, methodType(Date.class, CharSequence.class, Integer.class));
        assertNotEquals(Comparison.EQUAL, compare);
    }

    @Test
    public void testMethodTypesMoreGeneric() {
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
    public void testMethodTypesMoreSpecific() {
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
    public void testMethodTypesConvertable() {
        MethodType prototype = methodType(Date.class, CharSequence.class, Integer.class);
        Comparison compare = Methods.compare(prototype, methodType(Date.class, Object.class, int.class));
        assertEquals(Comparison.CONVERTABLE, compare);
        compare = Methods.compare(prototype, methodType(Object.class, Object.class, Object.class));
        assertEquals(Comparison.CONVERTABLE, compare);
    }

    @Test
    public void testMethodTypesIncomatible() {
        MethodType prototype = methodType(Date.class, CharSequence.class, Integer.class);
        Comparison compare = Methods.compare(prototype, methodType(Date.class, Date.class, int.class));
        assertEquals(Comparison.INCOMPATIBLE, compare);
        compare = Methods.compare(prototype, methodType(TimeUnit.class, CharSequence.class, Integer.class));
        assertEquals(Comparison.INCOMPATIBLE, compare);
    }

    @Test
    public void testMethodTypesLessArguments() {
        MethodType prototype = methodType(Date.class, CharSequence.class, Integer.class);
        Comparison compare = Methods.compare(prototype, methodType(Date.class, CharSequence.class));
        assertEquals(Comparison.LESS_ARGUMENTS, compare);
    }

    @Test
    public void testMethodTypesMoreArguments() {
        MethodType prototype = methodType(Date.class);
        Comparison compare = Methods.compare(prototype, methodType(Date.class, float.class));
        assertEquals(Comparison.MORE_ARGUMENTS, compare);
    }

    @Test
    public void testFindSingleMethod() throws Throwable {
        final AtomicReference<String> ref = new AtomicReference<>();
        MethodHandle handle = Methods.findMethodHandleOfType(lookup(), new Object() {
            void set(String value) {
                ref.set(value);
            }
        }, methodType(void.class, String.class));
        assertEquals(methodType(void.class, String.class), handle.type());
        handle.invokeExact("Hello world.");
        assertEquals("Hello world.", ref.get());

        handle = Methods.findMethodHandleOfType(lookup(), new Object() {
            void set(String value) {
                ref.set(value);
            }
        }, methodType(void.class, Object.class));
        assertEquals(methodType(void.class, Object.class), handle.type());
        handle.invokeExact((Object) "Hello world.");
        assertEquals("Hello world.", ref.get());
    }

    @Test(expected = NoSuchMethodError.class)
    public void testFindSingleMethodFailed() throws IllegalAccessException {
        Methods.findMethodHandleOfType(lookup(), ActionListener.class, methodType(String.class));
    }

    @Test
    public void findSingleMethodWithNewInstance() throws Throwable {
        MethodHandle handle = Methods.findMethodHandleOfType(publicLookup(), StringBuilder.class,
                methodType(StringBuilder.class, String.class));
        StringBuilder sb1 = (StringBuilder) handle.invokeExact("First");
        assertEquals("First", sb1.toString());
        StringBuilder sb2 = (StringBuilder) handle.invokeExact(" Second");
        assertEquals("First Second", sb2.toString());
        assertEquals(sb1, sb2);
    }

    @Test
    public void findSingleMethodWithSameInstanceOnEveryCall() throws Throwable {
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
    public void findSingleBestMatchingMethod() throws Throwable {
        // Also checks that no new instance ist created because returnHello() is static
        MethodHandle handle = Methods.findMethodHandleOfType(lookup(), WithThreeMethods.class, methodType(String.class));
        String s = (String) handle.invokeExact();
        assertEquals("Hello", s);

        // First find the exact match
        handle = Methods.findMethodHandleOfType(lookup(), new WithThreeMethods(true),
                methodType(String.class, String.class));
        s = (String) handle.invokeExact("My");
        assertEquals("My string", s);

        // then find the more generic one
        handle = Methods.findMethodHandleOfType(lookup(), new WithThreeMethods(true),
                methodType(Object.class, CharSequence.class));
        Object o = handle.invokeExact((CharSequence) "My");
        assertEquals("My", o);
    }

    @Test(expected = AmbiguousMethodException.class)
    public void testFindSingleMethodAmbiguous() throws IllegalAccessException {
        // Both possible methods are more generic
        Methods.findMethodHandleOfType(lookup(), WithThreeMethods.class,
                methodType(CharSequence.class, String.class));
    }

    @Test
    public void testSynchronize() throws Throwable {
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
    public void testSynchronizeWithException() throws Throwable {
        Semaphore p = new Semaphore(1);
        AtomicReference<String> ref = new AtomicReference<>("Initial");
        MethodHandles.Lookup lookup = lookup();
        MethodHandle checkValue = lookup.findStatic(lookup.lookupClass(), "check", methodType(void.class, AtomicReference.class, Object.class));
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
    public void testSynchronizeConcurrent() throws Throwable {
        AtomicReference<String> ref = new AtomicReference<>("Initial");
        MethodHandles.Lookup lookup = lookup();
        MethodHandle sleepAndCompare = lookup.findStatic(lookup.lookupClass(), "sleepAndCompare", methodType(String.class, long.class, AtomicReference.class, String.class));
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
    public void testLambdaFactory() throws Throwable {
        MethodHandle mult = publicLookup().findStatic(Math.class, "multiplyExact", methodType(int.class, int.class, int.class));
        MethodHandle lambdaFactory = Methods.createLambdaFactory(lookup(), mult, IntUnaryOperator.class);
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
    public void testLambdaFactoryWithMarkers() throws Throwable {
        MethodHandle mult = publicLookup().findStatic(Math.class, "multiplyExact", methodType(int.class, int.class, int.class));
        MethodHandle lambdaFactory = Methods.createLambdaFactory(lookup(), mult, IntBinaryOperator.class,
                ThreadSafe.class, Override.class); // Hehe, annotations as marker interfaces
        assertNotNull(lambdaFactory);

        IntBinaryOperator multiply = (IntBinaryOperator) lambdaFactory.invokeExact();
        assertTrue(Methods.wasLambdafiedDirect(multiply));
        assertTrue(multiply instanceof ThreadSafe);
        assertTrue(multiply instanceof Override);
    }

    @SuppressWarnings("unused")
    private static String sumUp(Integer a, Object b) {
        return String.valueOf(a + Integer.parseInt(b.toString()));
    }

    public interface SumToString {
        Object sumUpNonsense(int a, Integer b);
    }

    @Test
    public void testLambdaFactoryWithConversion() throws Throwable {
        MethodHandles.Lookup lookup = lookup();
        MethodHandle sum = lookup.findStatic(lookup.lookupClass(), "sumUp", methodType(String.class, Integer.class, Object.class));
        MethodHandle lambdaFactory = Methods.createLambdaFactory(lookup, sum, SumToString.class);
        assertNotNull(lambdaFactory);

        SumToString demo = (SumToString) lambdaFactory.invokeExact();
        assertTrue(Methods.wasLambdafiedDirect(demo));
        assertFalse(MethodHandleProxies.isWrapperInstance(demo));
        Object result = demo.sumUpNonsense(5, 6);
        assertEquals("11", result);
    }

    @Test
    public void testLambdaFactoryNotDirect() throws NoSuchMethodException, IllegalAccessException {
        MethodHandles.Lookup lookup = lookup();
        MethodHandle sum = lookup.findStatic(lookup.lookupClass(), "sumUp", methodType(String.class, Integer.class, Object.class));
        MethodHandle modified = MethodHandles.dropArguments(sum, 1, String.class);
        MethodHandle lambdaFactory = Methods.createLambdaFactory(lookup, modified, SumToString.class);
        assertNull(lambdaFactory);
    }

    @Test
    public void testLambdafy() throws Throwable {
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
    public void testLambdafyNotDirect() throws Throwable {
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
    public void testLambdaInFull() throws Throwable {
        MethodHandles.Lookup lookup = lookup();
        MethodHandle loop = lookup.findVirtual(StringLoop.class, "loop", methodType(String.class, CharSequence.class));
        MethodHandle lambdaFactory = Methods.createLambdaFactory(lookup, loop, UnaryOperator.class);
        assertNotNull(lambdaFactory);

        MethodHandle constructor = lookup.findConstructor(StringLoop.class, methodType(void.class, int.class));
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
