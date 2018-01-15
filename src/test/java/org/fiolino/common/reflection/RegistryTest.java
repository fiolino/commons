package org.fiolino.common.reflection;

import org.junit.Test;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.*;

import static java.lang.invoke.MethodHandles.lookup;
import static java.lang.invoke.MethodHandles.publicLookup;
import static java.lang.invoke.MethodType.methodType;
import static org.junit.Assert.*;

/**
 * Created by kuli on 08.03.17.
 */
public class RegistryTest {

    @Test
    public void testNoArgumentsNoReturn() throws Throwable {
        MethodHandles.Lookup lookup = lookup();
        MethodHandle incrementCounter = lookup.findStatic(lookup.lookupClass(), "incrementCounter", methodType(void.class));
        Registry reg = Registry.buildFor(incrementCounter);

        MethodHandle callOnlyOnce = reg.getAccessor();
        counter.set(1);
        callOnlyOnce.invokeExact();
        assertEquals(2, counter.get());
        for (int i=0; i < 100; i++) {
            callOnlyOnce.invokeExact();
            assertEquals(2, counter.get());
        }

        reg.reset();
        for (int i=0; i < 100; i++) {
            callOnlyOnce.invokeExact();
            assertEquals(3, counter.get());
        }

        // Now check that the original accessor will never be invoked if the updater is invoked earlier.
        reg = Registry.buildFor(incrementCounter);

        callOnlyOnce = reg.getAccessor();
        counter.set(1);
        callOnlyOnce.invokeExact();
        assertEquals(2, counter.get());
    }

    @Test
    public void testNoArgumentsButReturnSomething() throws Throwable {
        MethodHandles.Lookup lookup = lookup();
        MethodHandle incrementCounter = lookup.findStatic(lookup.lookupClass(), "incrementAndGet", methodType(int.class));
        Registry reg = Registry.buildFor(incrementCounter);

        MethodHandle callOnlyOnce = reg.getAccessor();
        counter.set(1);
        int value = (int) callOnlyOnce.invokeExact();
        assertEquals(2, value);
        assertEquals(2, counter.get());
        for (int i=0; i < 100; i++) {
            value = (int) callOnlyOnce.invokeExact();
            assertEquals(2, value);
        }

        reg.reset();
        for (int i=0; i < 100; i++) {
            value = (int) callOnlyOnce.invokeExact();
            assertEquals(3, value);
        }

        // Now check that the original accessor will never be invoked if the updater is invoked earlier.
        reg = Registry.buildFor(incrementCounter);

        callOnlyOnce = reg.getAccessor();
        counter.set(1);
        value = (int) callOnlyOnce.invokeExact();
        assertEquals(2, value);
    }

    private static final AtomicInteger counter = new AtomicInteger();

    @SuppressWarnings("unused")
    private static void incrementCounter() {
        counter.incrementAndGet();
    }

    @SuppressWarnings("unused")
    private static int incrementAndGet() {
        return counter.incrementAndGet();
    }

    @Test
    public void testRunnable() {
        AtomicInteger counter = new AtomicInteger(100);
        LambdaRegistry<Runnable> registry = Registry.buildForFunctionalType(counter::incrementAndGet);
        Runnable onlyOnce = registry.getAccessor();
        onlyOnce.run();
        assertEquals(101, counter.get());
        onlyOnce.run();
        assertEquals(101, counter.get());
        counter.set(771177);
        onlyOnce.run();
        assertEquals(771177, counter.get());
    }

    @Test
    public void testCallable() throws Exception {
        AtomicInteger counter = new AtomicInteger(100);
        @SuppressWarnings({"unchecked", "rawTypes"})
        LambdaRegistry<Callable<String>> registry = Registry.buildForFunctionalType(() -> String.valueOf(counter.incrementAndGet()));
        Callable<String> onlyOnce = registry.getAccessor();
        String val = onlyOnce.call();
        assertEquals("101", val);
        assertEquals(101, counter.get());
        val = onlyOnce.call();
        assertEquals("101", val);
        assertEquals(101, counter.get());
        counter.set(771177);
        val = onlyOnce.call();
        assertEquals("101", val);
        assertEquals(771177, counter.get());
    }

    @Test
    public void testConcurrentRunnable() throws InterruptedException {
        AtomicInteger counter = new AtomicInteger(100);
        LambdaRegistry<Runnable> registry = Registry.buildForFunctionalType(() -> {
            try {
                TimeUnit.MILLISECONDS.sleep(500);
            } catch (InterruptedException ex) {
                fail("Interrupted!");
            }
            counter.incrementAndGet();
        });
        Runnable onlyOnce = registry.getAccessor();

        Thread t = new Thread(onlyOnce);
        t.start();
        Thread.yield();
        TimeUnit.MILLISECONDS.sleep(100);
        onlyOnce.run();
        t.join();

        assertEquals(101, counter.get());
    }

    @SuppressWarnings("unused")
    private static int sleepAndIncrement(AtomicInteger ref, int sleep) throws InterruptedException {
        TimeUnit.MILLISECONDS.sleep(sleep);
        return ref.incrementAndGet();
    }

    @Test
    public void setConcurrentSupplier() throws NoSuchMethodException, IllegalAccessException, InterruptedException {
        AtomicInteger counter = new AtomicInteger(500);
        MethodHandles.Lookup lookup = lookup();
        MethodHandle execute = lookup.findStatic(lookup.lookupClass(), "sleepAndIncrement", methodType(int.class, AtomicInteger.class, int.class));
        IntSupplier operator = Methods.lambdafy(execute, IntSupplier.class, counter, 100);
        IntSupplier guarded = Registry.buildForFunctionalType(IntSupplier.class, operator).getAccessor();

        AtomicInteger backgroundResultContainer = new AtomicInteger();
        Thread t = new Thread(() -> backgroundResultContainer.set(guarded.getAsInt()));
        t.start();
        Thread.yield();
        TimeUnit.MILLISECONDS.sleep(50);
        int localResult = guarded.getAsInt();
        t.join();

        assertEquals(501, localResult);
        assertEquals(501, backgroundResultContainer.get());

        long start = System.currentTimeMillis();
        localResult = guarded.getAsInt();
        long used = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis() - start);
        assertTrue(used <= 1);
        assertEquals(501, localResult);
    }

    @Test
    public void testWithParameter() throws Throwable {
        testWithParameter(true);
        testWithParameter(false);
    }

    private void testWithParameter(boolean isVolatile) throws Throwable {
        AtomicReference<String> ref = new AtomicReference<>();
        MethodHandle target = publicLookup().bind(ref, "set", methodType(void.class, Object.class));
        OneTimeRegistryBuilder ex = new OneTimeRegistryBuilder(target, isVolatile);
        ex.getAccessor().invokeExact((Object) "Fritz");
        assertEquals("Fritz", ref.get());
        ex.getAccessor().invokeExact((Object) "Knut");
        // Second call was ignored
        assertEquals("Fritz", ref.get());
        ex.getAccessor().invokeExact((Object) null);
        // Third call was ignored as well
        assertEquals("Fritz", ref.get());
    }

    @Test
    public void testWithParameterAndReturnValue() throws Throwable {
        testWithParameterAndReturnValue(true);
        testWithParameterAndReturnValue(false);
    }

    private void testWithParameterAndReturnValue(boolean isVolatile) throws Throwable {
        AtomicInteger ref = new AtomicInteger(100);
        MethodHandle target = publicLookup().bind(ref, "getAndSet", methodType(int.class, int.class));
        OneTimeRegistryBuilder ex = new OneTimeRegistryBuilder(target, isVolatile);
        int newValue = (int) ex.getAccessor().invokeExact(222);
        assertEquals(100, newValue);
        assertEquals(222, ref.get());
        newValue = (int) ex.getAccessor().invokeExact(444);
        // Second call was ignored
        assertEquals(100, newValue);
        assertEquals(222, ref.get());
    }

    @Test
    public void setConcurrentFunction() throws NoSuchMethodException, IllegalAccessException, InterruptedException {
        AtomicInteger counter = new AtomicInteger(500);
        MethodHandles.Lookup lookup = lookup();
        MethodHandle execute = lookup.findStatic(lookup.lookupClass(), "sleepAndIncrement", methodType(int.class, AtomicInteger.class, int.class));
        IntUnaryOperator operator = Methods.lambdafy(execute, IntUnaryOperator.class, counter);
        IntUnaryOperator guarded = Registry.buildForFunctionalType(IntUnaryOperator.class, operator).getAccessor();

        AtomicInteger backgroundResultContainer = new AtomicInteger();
        Thread t = new Thread(() -> backgroundResultContainer.set(guarded.applyAsInt(200)));
        t.start();
        Thread.yield();
        TimeUnit.MILLISECONDS.sleep(50);
        int localResult = guarded.applyAsInt(200);
        t.join();

        assertEquals(501, localResult);
        assertEquals(501, backgroundResultContainer.get());

        long start = System.currentTimeMillis();
        localResult = guarded.applyAsInt(200);
        long used = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis() - start);
        assertTrue(used <= 1);
        assertEquals(501, localResult);
    }

    private static String sayHello(String name) {
        if ("John".equals(name)) {
            return null;
        }
        return "Hello " + name + " at " + System.currentTimeMillis() + "!";
    }

    @Test
    public void testFunctions() throws InterruptedException {
        LambdaRegistry<UnaryOperator<String>> reg = Registry.buildForFunctionalType(RegistryTest::sayHello);
        UnaryOperator<String> func = reg.getAccessor();

        String fritz = func.apply("Fritz");
        assertTrue(fritz.startsWith("Hello Fritz "));
        String knut = func.apply("Knut");
        assertTrue(knut.startsWith("Hello Knut "));

        // Check null parameter
        String nullName = func.apply(null);
        assertTrue(nullName.startsWith("Hello null "));

        // Check null return value
        String john = func.apply("John");
        assertNull(john);

        // Check that values are cached
        TimeUnit.MILLISECONDS.sleep(50);
        assertEquals(fritz, func.apply("Fritz"));
        assertEquals(knut, func.apply("Knut"));
        assertEquals(nullName, func.apply(null));
        assertNull(func.apply("John"));

        reg.reset();
        TimeUnit.MILLISECONDS.sleep(50);
        assertNotEquals(fritz, func.apply("Fritz"));
        assertNotEquals(knut, func.apply("Knut"));
        assertNotEquals(nullName, func.apply(null));
        assertNull(func.apply("John"));
    }

    @Test
    public void testConsumer() {
        AtomicReference<String> ref = new AtomicReference<>();
        LambdaRegistry<Consumer<String>> reg = Registry.buildForFunctionalType(ref::set);
        Consumer<String> consumer = reg.getAccessor();
        consumer.accept("Hello");
        assertEquals("Hello", ref.get());
        consumer.accept(null);
        assertNull(ref.get());
        consumer.accept("Hello");
        assertNull(ref.get());
        consumer.accept("Goodbye");
        assertEquals("Goodbye", ref.get());
        consumer.accept("Hello");
        assertEquals("Goodbye", ref.get());
    }

    @SuppressWarnings("unused")
    private static String getAndSetSum(AtomicInteger ref, int value, String name) {
        int old = ref.getAndAdd(value);
        if (old == 0) {
            return null;
        }
        return name + " says: " + old + " + " + value + " = " + ref.get();
    }

    @Test
    public void testMultiArguments() throws Throwable {
        AtomicInteger ref = new AtomicInteger(100);
        MethodHandles.Lookup lookup = lookup();
        MethodHandle sum = lookup.findStatic(lookup.lookupClass(), "getAndSetSum", methodType(String.class, AtomicInteger.class, int.class, String.class));
        Registry reg = Registry.buildFor(sum, ref);
        MethodHandle sumX = reg.getAccessor();

        String result = (String) sumX.invokeExact(7, "Frankie");
        assertEquals("Frankie says: 100 + 7 = 107", result);
        assertEquals(107, ref.get());

        ref.set(0);
        result = (String) sumX.invokeExact(7, "Frankie");
        assertEquals("Frankie says: 100 + 7 = 107", result);
        assertEquals(0, ref.get());

        result = (String) sumX.invokeExact(50, "Johnny");
        assertNull(result);
        assertEquals(50, ref.get());

        ref.set(0);
        result = (String) sumX.invokeExact(50, "Johnny");
        assertNull(result);
        assertEquals(0, ref.get());

        ref.set(50);
        result = (String) sumX.invokeExact(51, "Johnny");
        assertEquals("Johnny says: 50 + 51 = 101", result);
        assertEquals(101, ref.get());

        result = (String) sumX.invokeExact(52, "Johnny");
        assertEquals("Johnny says: 101 + 52 = 153", result);
        assertEquals(153, ref.get());

        result = (String) sumX.invokeExact(52, "Lisa");
        assertEquals("Lisa says: 153 + 52 = 205", result);
        assertEquals(205, ref.get());

        result = (String) sumX.invokeExact(7, "Frankie");
        assertEquals("Frankie says: 100 + 7 = 107", result);
        assertEquals(205, ref.get()); // Unchanged

        reg.reset();
        result = (String) sumX.invokeExact(7, "Frankie");
        assertEquals("Frankie says: 205 + 7 = 212", result);
        assertEquals(212, ref.get());
    }

    @Test
    public void testBiConsumer() {
        AtomicReference<String> ref = new AtomicReference<>();
        LambdaRegistry<BiConsumer<AtomicReference<String>, String>> reg = Registry.buildForFunctionalType(AtomicReference::set);
        BiConsumer<AtomicReference<String>, String> set = reg.getAccessor();

        set.accept(ref, "Steffi");
        assertEquals("Steffi", ref.get());
        set.accept(ref, "Heidi");
        assertEquals("Heidi", ref.get());
        set.accept(ref, "Steffi");
        assertEquals("Heidi", ref.get());
        set.accept(ref, "Mona");
        assertEquals("Mona", ref.get());
        set.accept(ref, "Heidi");
        assertEquals("Mona", ref.get());
    }

    @SuppressWarnings("unused")
    private static void sum(AtomicInteger target, int... values) {
        int sum = Arrays.stream(values).sum();
        target.set(sum);
    }

    @Test
    public void testVarArgs() throws Throwable {
        AtomicInteger ref = new AtomicInteger();
        MethodHandles.Lookup lookup = lookup();
        MethodHandle handle = lookup.findStatic(lookup.lookupClass(), "sum", methodType(void.class, AtomicInteger.class, int[].class));
        Registry reg = Registry.buildFor(handle);
        MethodHandle ex1 = reg.getAccessor();

        ex1.invoke(ref, 1, 2, 3);
        assertEquals(6, ref.get());

        ex1.invoke(ref, 1000, 1000, 5000);
        assertEquals(7000, ref.get());

        ex1.invoke(ref, 1, 2, 3);
        assertEquals(7000, ref.get());
    }

    @Test
    public void testVarArgsOnly() throws Throwable {
        AtomicInteger ref = new AtomicInteger();
        MethodHandles.Lookup lookup = lookup();
        MethodHandle handle = lookup.findStatic(lookup.lookupClass(), "sum", methodType(void.class, AtomicInteger.class, int[].class));
        Registry reg = Registry.buildFor(handle, ref);
        MethodHandle ex1 = reg.getAccessor();

        ex1.invoke(1, 2, 3);
        assertEquals(6, ref.get());

        ex1.invoke(1000, 1000, 5000);
        assertEquals(7000, ref.get());

        ex1.invoke(1, 2, 3);
        assertEquals(7000, ref.get());
    }

    @Test
    public void testBiInt() {
        AtomicInteger ref = new AtomicInteger();
        LambdaRegistry<IntBinaryOperator> reg = Registry.buildForFunctionalType((a, b) -> ref.getAndAdd(a + b));
        IntBinaryOperator operator = reg.getAccessor();

        int result = operator.applyAsInt(5, 6);
        assertEquals(0, result);
        assertEquals(11, ref.get());

        result = operator.applyAsInt(99, 9);
        assertEquals(11, result);
        assertEquals(119, ref.get());

        result = operator.applyAsInt(5, 6);
        assertEquals(0, result);
        assertEquals(119, ref.get());

        reg.reset();
        result = operator.applyAsInt(5, 6);
        assertEquals(119, result);
        assertEquals(130, ref.get());

        result = operator.applyAsInt(99, 9);
        assertEquals(130, result);
        assertEquals(238, ref.get());
    }

    @Test
    public void testUpdateable() throws Throwable {
        AtomicReference<String> ref = new AtomicReference<>("Initial");
        MethodHandle setRef = publicLookup().bind(ref, "getAndSet", methodType(Object.class, Object.class));
        OneTimeExecution ex = OneTimeExecution.createFor(setRef);

        Object previous = ex.getAccessor().invokeExact((Object) "First");
        assertEquals("Initial", previous);
        assertEquals("First", ref.get());

        previous = ex.getAccessor().invokeExact((Object) "Second");
        assertEquals("Initial", previous);
        assertEquals("First", ref.get());

        ex.updateTo(TimeUnit.SECONDS);
        previous = ex.getAccessor().invokeExact((Object) "Third");
        assertEquals(TimeUnit.SECONDS, previous);
        assertEquals("First", ref.get());
    }

    @Test
    public void testUpdateFirst() throws Throwable {
        AtomicReference<String> ref = new AtomicReference<>("Initial");
        MethodHandle setRef = publicLookup().bind(ref, "getAndSet", methodType(Object.class, Object.class));
        OneTimeExecution ex = OneTimeExecution.createFor(setRef);
        ex.updateTo(2046);

        Object previous = ex.getAccessor().invokeExact((Object) "First");
        assertEquals(2046, previous);
        assertEquals("Initial", ref.get());

        ex.reset();
        previous = ex.getAccessor().invokeExact((Object) "First");
        assertEquals("Initial", previous);
        assertEquals("First", ref.get());

        previous = ex.getAccessor().invokeExact((Object) "Whatever the...");
        assertEquals("Initial", previous);
        assertEquals("First", ref.get());
    }

    // Enum and boolean types are handles explicitly in Registry of they're the singular parameters
    @Test
    public void testEnumParameter() throws Throwable {
        MethodHandle arrayOfTwoValues = MethodHandles.identity(Object[].class).asCollector(Object[].class, 2);
        arrayOfTwoValues = arrayOfTwoValues.asType(methodType(Object[].class, TimeUnit.class, long.class));
        MethodHandle getNanos = publicLookup().findStatic(System.class, "nanoTime", methodType(long.class));
        arrayOfTwoValues = MethodHandles.collectArguments(arrayOfTwoValues, 1, getNanos);

        // First test general functionality
        Object[] array1 = (Object[])arrayOfTwoValues.invokeExact(TimeUnit.SECONDS);
        TimeUnit.MICROSECONDS.sleep(10);
        Object[] array2 = (Object[])arrayOfTwoValues.invokeExact(TimeUnit.SECONDS);
        assertEquals(TimeUnit.SECONDS, array1[0]);
        assertEquals(TimeUnit.SECONDS, array2[0]);
        assertTrue((long)array1[1] < (long)array2[1]);

        // Now test cached version
        Registry registry = Registry.buildFor(arrayOfTwoValues);
        MethodHandle accessor = registry.getAccessor();
        array1 = (Object[]) accessor.invokeExact(TimeUnit.SECONDS);
        TimeUnit.MICROSECONDS.sleep(10);
        array2 = (Object[])accessor.invokeExact(TimeUnit.SECONDS);
        assertEquals(TimeUnit.SECONDS, array1[0]);
        assertEquals(TimeUnit.SECONDS, array2[0]);
        assertTrue("Both must be the same", (long)array1[1] == (long)array2[1]);
        assertTrue("Even the array should be the same", array1 == array2);

        // Now test that different value returns different result
        array2 = (Object[])accessor.invokeExact(TimeUnit.HOURS);
        assertEquals(TimeUnit.HOURS, array2[0]);
        assertTrue("Second call comes later", (long)array1[1] < (long)array2[1]);
        array2 = (Object[])accessor.invokeExact(TimeUnit.SECONDS);
        assertEquals(TimeUnit.SECONDS, array2[0]);
        assertTrue("Both must be the same", (long)array1[1] == (long)array2[1]);
        assertTrue("Even the array should be the same, again", array1 == array2);

        // Now test reset
        registry.reset();
        array2 = (Object[])accessor.invokeExact(TimeUnit.SECONDS);
        assertEquals(TimeUnit.SECONDS, array2[0]);
        assertTrue((long)array1[1] < (long)array2[1]);
    }

    @Test
    public void testBooleanParameter() throws Throwable {
        AtomicBoolean ref = new AtomicBoolean();
        MethodHandle setBool = publicLookup().bind(ref, "getAndSet", methodType(boolean.class, boolean.class));
        Registry registry = Registry.buildFor(setBool);
        MethodHandle accessor = registry.getAccessor();
        assertFalse((boolean) accessor.invokeExact(true));
        assertTrue(ref.get());
        assertFalse((boolean) accessor.invokeExact(true));
        assertTrue(ref.get());
        ref.set(false);
        assertFalse((boolean) accessor.invokeExact(true));
        assertFalse(ref.get());

        registry.reset();
        assertFalse((boolean) accessor.invokeExact(false));
    }

    @Test
    public void testMapOnlyFirstArgumentToInteger() throws Throwable {
        MethodHandle concat = publicLookup().findVirtual(String.class, "concat", methodType(String.class, String.class));
        MethodHandle toInt = publicLookup().findVirtual(String.class, "length", methodType(int.class)); // Would be a ridiculous mapping
        Registry r = Registry.buildForLimitedRange(concat, toInt, 10, 10, 1);
        MethodHandle accessor = r.getAccessor();

        String s1 = (String) accessor.invokeExact("12345", "First");
        assertEquals("12345First", s1);
        String s2 = (String) accessor.invokeExact("54321", "Second");
        assertEquals("12345First", s2);
        s2 = (String) accessor.invokeExact("5432", "Second");
        assertEquals("5432Second", s2);

        r.reset();
        s1 = (String) accessor.invokeExact("12345", "First");
        assertEquals("12345First", s1);
    }

    @Test
    public void testIndexOutOfRange() throws Throwable {
        MethodHandle concat = publicLookup().findVirtual(String.class, "concat", methodType(String.class, String.class));
        MethodHandle toInt = publicLookup().findVirtual(String.class, "length", methodType(int.class)); // Would be a ridiculous mapping
        MethodHandle sumBoth = publicLookup().findStatic(Math.class, "addExact", methodType(int.class, int.class, int.class));

        Registry r = Registry.buildForLimitedRange(concat, MethodHandles.filterArguments(sumBoth, 0, toInt, toInt), 10, 10, 1);
        MethodHandle accessor = r.getAccessor();

        String s1 = (String) accessor.invokeExact("123", "45");
        assertEquals("12345", s1);
        s1 = (String) accessor.invokeExact("A", "BCDE");
        assertEquals("12345", s1);

        try {
            s1 = (String) accessor.invokeExact("123456", "789012");
        } catch (LimitExceededException ex) {
            assertTrue(ex.getCause() instanceof ArrayIndexOutOfBoundsException);
            assertEquals("Argument value [123456, 789012] resolves to 12 which is beyond 0..9", ex.getMessage());
            return;
        }

        fail("Should not run without exception");
    }

    @Test
    public void testExpandingRange() throws Throwable {
        MethodHandle toInt = publicLookup().findVirtual(String.class, "length", methodType(int.class)); // Would be a ridiculous mapping
        AtomicReference<String> ref = new AtomicReference<>("Initial");
        MethodHandle getAndSet = publicLookup().bind(ref, "getAndSet", methodType(Object.class, Object.class));

        Registry r = Registry.buildForLimitedRange(getAndSet, toInt, 3, -1, 1);
        MethodHandle accessor = r.getAccessor();

        Object old = accessor.invokeExact((Object) "123");
        assertEquals("Initial", old);
        assertEquals("123", ref.get());

        old = accessor.invokeExact((Object) "123456789012345");
        assertEquals("123", old);
        assertEquals("123456789012345", ref.get());

        ref.set("none");
        old = accessor.invokeExact((Object) "123456789012345678901234567890");
        assertEquals("none", old);
        assertEquals("123456789012345678901234567890", ref.get());
    }

    @Test
    public void testNoFilter() throws Throwable {
        AtomicInteger ref = new AtomicInteger();
        MethodHandle getAndSet = publicLookup().bind(ref, "getAndSet", methodType(int.class, int.class));

        Registry r = Registry.buildForLimitedRange(getAndSet, null, 3, 100, 1);
        MethodHandle accessor = r.getAccessor();

        int old = (int) accessor.invokeExact(2);
        assertEquals(0, old);
        assertEquals(2, ref.get());

        old = (int) accessor.invokeExact(15);
        assertEquals(2, old);
        assertEquals(15, ref.get());
    }

    @Test
    public void testFilterWithSimilarType() throws Throwable {
        MethodHandle sumBoth = publicLookup().findStatic(Math.class, "addExact", methodType(int.class, int.class, int.class));
        Set<Object> set = new HashSet<>();
        MethodHandle addAll = publicLookup().findStatic(Collections.class, "addAll", methodType(boolean.class, Collection.class, Object[].class)).bindTo(set);
        addAll = addAll.asCollector(Object[].class, 3);

        Registry r = Registry.buildForLimitedRange(addAll, sumBoth, 10, 10, 1);
        MethodHandle accessor = r.getAccessor();

        boolean wasChanged = (boolean) accessor.invokeExact((Object) 4, (Object) 5, (Object) "This doesn't count");
        assertTrue(wasChanged);
        assertEquals(3, set.size());
        assertTrue(set.contains("This doesn't count"));

        wasChanged = (boolean) accessor.invokeExact((Object) 3, (Object) 6, (Object) "Other value");
        assertTrue(wasChanged); // Because it was cached -- list.addAll wasn't called
        assertEquals(3, set.size());
        assertFalse(set.contains("Other value"));
    }

    @Test
    public void testForFixedValues() throws Throwable {
        MethodHandle target = publicLookup().findStatic(System.class, "nanoTime", methodType(long.class));
        target = MethodHandles.dropArguments(target, 0, String.class);
        Registry registry = Registry.buildForFixedValues(target, "white", "yellow", "green", "red", "blue", "black");
        MethodHandle h = registry.getAccessor();

        long white = (long) h.invokeExact("white");
        TimeUnit.MICROSECONDS.sleep(5);
        long yellow = (long) h.invokeExact("yellow");
        TimeUnit.MICROSECONDS.sleep(5);
        assertTrue(yellow > white);
        long green = (long) h.invokeExact("green");
        TimeUnit.MICROSECONDS.sleep(5);
        assertTrue(green > yellow);
        long red = (long) h.invokeExact("red");
        TimeUnit.MICROSECONDS.sleep(5);
        assertTrue(red > green);
        long blue = (long) h.invokeExact("blue");
        TimeUnit.MICROSECONDS.sleep(5);
        assertTrue(blue > red);
        long black = (long) h.invokeExact("black");
        TimeUnit.MICROSECONDS.sleep(5);
        assertTrue(black > blue);

        long white2 = (long) h.invokeExact("white");
        assertEquals(white, white2);
        long yellow2 = (long) h.invokeExact("yellow");
        assertEquals(yellow, yellow2);
        long green2 = (long) h.invokeExact("green");
        assertEquals(green, green2);
        long red2 = (long) h.invokeExact("red");
        assertEquals(red, red2);
        long blue2 = (long) h.invokeExact("blue");
        assertEquals(blue, blue2);
        long black2 = (long) h.invokeExact("black");
        assertEquals(black, black2);

        registry.reset();

        white2 = (long) h.invokeExact("white");
        assertTrue(white < white2);
        yellow2 = (long) h.invokeExact("yellow");
        assertTrue(yellow < yellow2);
        green2 = (long) h.invokeExact("green");
        assertTrue(green < green2);
        red2 = (long) h.invokeExact("red");
        assertTrue(red < red2);
        blue2 = (long) h.invokeExact("blue");
        assertTrue(blue < blue2);
        black2 = (long) h.invokeExact("black");
        assertTrue(black < black2);
    }

    @Test(expected = LimitExceededException.class)
    public void testForFixedValuesAndTakeWrong() throws Throwable {
        MethodHandle target = publicLookup().findStatic(System.class, "nanoTime", methodType(long.class));
        target = MethodHandles.dropArguments(target, 0, String.class);
        Registry registry = Registry.buildForFixedValues(target, "red", "green", "blue");
        MethodHandle h = registry.getAccessor();

        h.invoke("white");
        // Should fail
    }

    @Test
    public void testForFixedPrimitiveValues() throws Throwable {
        MethodHandle sumBoth = publicLookup().findStatic(Math.class, "addExact", methodType(int.class, int.class, int.class));
        AtomicInteger ref = new AtomicInteger();
        MethodHandle getInt = publicLookup().bind(ref, "get", methodType(int.class));
        MethodHandle target = MethodHandles.collectArguments(sumBoth, 0, getInt);

        Registry registry = Registry.buildForFixedValues(target, 5000, 9000, 3000, 4000, 1000, 10_000, 7000, 2000, 8000, 6000);
        MethodHandle accessor = registry.getAccessor();

        ref.set(250);
        int[] results = new int[10];
        for (int i=0; i < 10; i++) {
            int result = (int) accessor.invokeExact((i + 1) * 1000);
            assertEquals(i * 1000 + 1250, result);
            results[i] = result;
        }
        ref.set(-6666);
        for (int i=0; i < 10; i++) {
            int result = (int) accessor.invokeExact((i + 1) * 1000);
            assertEquals(i * 1000 + 1250, result);
            assertEquals(results[i], result);
        }
    }

    @Test
    public void testForFixedValuesWithComparator() throws Throwable {
        MethodHandle target = publicLookup().findStatic(System.class, "nanoTime", methodType(long.class));
        target = MethodHandles.dropArguments(target, 0, Class.class);
        Registry registry = Registry.buildForFixedValues(target, Comparator.comparing(Class::getName), String.class, Integer.class, Date.class);
        MethodHandle h = registry.getAccessor();

        long string = (long) h.invokeExact(String.class);
        TimeUnit.MICROSECONDS.sleep(5);
        long integer = (long) h.invokeExact(Integer.class);
        TimeUnit.MICROSECONDS.sleep(5);
        assertTrue(integer > string);
        long date = (long) h.invokeExact(Date.class);
        TimeUnit.MICROSECONDS.sleep(5);
        assertTrue(date > integer);

        long string2 = (long) h.invokeExact(String.class);
        assertEquals(string, string2);
        long integer2 = (long) h.invokeExact(Integer.class);
        assertEquals(integer, integer2);
        long date2 = (long) h.invokeExact(Date.class);
        assertEquals(date,  date2);

        registry.reset();

        string2 = (long) h.invokeExact(String.class);
        assertTrue(string < string2);
        integer2 = (long) h.invokeExact(Integer.class);
        assertTrue(integer < integer2);
        date2 = (long) h.invokeExact(Date.class);
        assertTrue(date < date2);
    }
}
