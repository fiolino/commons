package org.fiolino.common.reflection;

import org.junit.Test;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
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

        // Test updater
        MethodHandle updater = reg.getUpdater();
        for (int i=0; i < 100; i++) {
            // Don't do this in production, it's slow
            updater.invokeExact();
            assertEquals(i+4, counter.get());
        }
        callOnlyOnce.invokeExact();
        assertEquals(103, counter.get());
        callOnlyOnce.invokeExact();
        assertEquals(103, counter.get());

        // Now check that the original accessor will never be invoked if the updater is invoked earlier.
        reg = Registry.buildFor(incrementCounter);

        callOnlyOnce = reg.getAccessor();
        counter.set(1);
        callOnlyOnce.invokeExact();
        assertEquals(2, counter.get());

        reg = Registry.buildFor(incrementCounter);

        updater = reg.getUpdater();
        callOnlyOnce = reg.getAccessor();
        counter.set(1);
        updater.invokeExact();
        assertEquals(2, counter.get());
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

        // Test updater
        MethodHandle updater = reg.getUpdater();
        for (int i=0; i < 100; i++) {
            // Don't do this in production, it's slow
            value = (int) updater.invokeExact();
            assertEquals(i+4, value);
        }
        value = (int) callOnlyOnce.invokeExact();
        assertEquals(103, counter.get());
        value = (int) callOnlyOnce.invokeExact();
        assertEquals(103, counter.get());

        // Now check that the original accessor will never be invoked if the updater is invoked earlier.
        reg = Registry.buildFor(incrementCounter);

        callOnlyOnce = reg.getAccessor();
        counter.set(1);
        value = (int) callOnlyOnce.invokeExact();
        assertEquals(2, value);

        reg = Registry.buildFor(incrementCounter);

        updater = reg.getUpdater();
        callOnlyOnce = reg.getAccessor();
        counter.set(1);
        value = (int) updater.invokeExact();
        assertEquals(2, value);
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

        ex.getUpdater().invokeExact((Object) "Tonja");
        assertEquals("Tonja", ref.get());
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

        fritz = func.apply("Fritz");
        nullName = func.apply(null);
        UnaryOperator<String> updater = reg.getUpdater();
        TimeUnit.MILLISECONDS.sleep(50);
        assertEquals(fritz, func.apply("Fritz"));
        String newFritz = updater.apply("Fritz");
        assertNotEquals(fritz, newFritz);
        assertNotEquals(fritz, func.apply("Fritz"));
        assertEquals(newFritz, func.apply("Fritz"));

        assertEquals(nullName, func.apply(null));
        String newNullName = updater.apply(null);
        assertNotEquals(nullName, newNullName);
        assertNotEquals(nullName, func.apply(null));
        assertEquals(newNullName, func.apply(null));
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

        Consumer<String> updater = reg.getUpdater();
        updater.accept("Hello");
        assertEquals("Hello", ref.get());
        consumer.accept(null);
        assertEquals("Hello", ref.get());
        updater.accept(null);
        assertNull(ref.get());
    }

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

        result = (String) reg.getUpdater().invokeExact(52, "Lisa");
        assertEquals("Lisa says: 205 + 52 = 257", result);
        assertEquals(257, ref.get());

        result = (String) sumX.invokeExact(7, "Frankie");
        assertEquals("Frankie says: 100 + 7 = 107", result);
        assertEquals(257, ref.get());

        reg.reset();
        result = (String) sumX.invokeExact(7, "Frankie");
        assertEquals("Frankie says: 257 + 7 = 264", result);
        assertEquals(264, ref.get());
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
        reg.getUpdater().accept(ref, "Heidi");
        assertEquals("Heidi", ref.get());
    }

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

        reg.getUpdater().invoke(ref, 1, 2, 3);
        assertEquals(6, ref.get());
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

        reg.getUpdater().invoke(1, 2, 3);
        assertEquals(6, ref.get());
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

        result = reg.getUpdater().applyAsInt(5, 6);
        assertEquals(119, result);
        assertEquals(130, ref.get());

        result = operator.applyAsInt(5, 6);
        assertEquals(119, result);
        assertEquals(130, ref.get());

        result = operator.applyAsInt(99, 9);
        assertEquals(11, result);
        assertEquals(130, ref.get());
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
    }
}
