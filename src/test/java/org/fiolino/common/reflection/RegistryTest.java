package org.fiolino.common.reflection;

import org.fiolino.common.reflection.Registry;
import org.fiolino.common.reflection.RegistryBuilder;
import org.junit.Test;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.IntSupplier;
import java.util.function.IntUnaryOperator;

import static java.lang.invoke.MethodHandles.lookup;
import static java.lang.invoke.MethodType.methodType;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Created by kuli on 08.03.17.
 */
public class RegistryTest {

    @Test
    public void testNoArgumentsNoReturn() throws Throwable {
        MethodHandles.Lookup lookup = lookup();
        MethodHandle incrementCounter = lookup.findStatic(lookup.lookupClass(), "incrementCounter", methodType(void.class));
        HandleRegistry reg = Registry.buildFor(incrementCounter);

        MethodHandle callOnlyOnce = reg.getAccessor();
        counter.set(1);
        callOnlyOnce.invokeExact();
        assertEquals(2, counter.get());
        for (int i=0; i < 100; i++) {
            callOnlyOnce.invokeExact();
            assertEquals(2, counter.get());
        }

        reg.clear();
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
        HandleRegistry reg = Registry.buildFor(incrementCounter);

        MethodHandle callOnlyOnce = reg.getAccessor();
        counter.set(1);
        int value = (int) callOnlyOnce.invokeExact();
        assertEquals(2, value);
        assertEquals(2, counter.get());
        for (int i=0; i < 100; i++) {
            value = (int) callOnlyOnce.invokeExact();
            assertEquals(2, value);
        }

        reg.clear();
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
        TimeUnit.SECONDS.sleep(sleep);
        return ref.incrementAndGet();
    }

    @Test
    public void setConcurrentSupplier() throws NoSuchMethodException, IllegalAccessException, InterruptedException {
        AtomicInteger counter = new AtomicInteger(500);
        MethodHandles.Lookup lookup = lookup();
        MethodHandle execute = lookup.findStatic(lookup.lookupClass(), "sleepAndIncrement", methodType(int.class, AtomicInteger.class, int.class));
        IntSupplier operator = Methods.lambdafy(execute, IntSupplier.class, counter, 2);
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
    public void setConcurrentFunction() throws NoSuchMethodException, IllegalAccessException, InterruptedException {
        AtomicInteger counter = new AtomicInteger(500);
        MethodHandles.Lookup lookup = lookup();
        MethodHandle execute = lookup.findStatic(lookup.lookupClass(), "sleepAndIncrement", methodType(int.class, AtomicInteger.class, int.class));
        IntUnaryOperator operator = Methods.lambdafy(execute, IntUnaryOperator.class, counter);
        IntUnaryOperator guarded = Registry.buildForFunctionalType(IntUnaryOperator.class, operator).getAccessor();

        AtomicInteger backgroundResultContainer = new AtomicInteger();
        Thread t = new Thread(() -> backgroundResultContainer.set(guarded.applyAsInt(3)));
        t.start();
        Thread.yield();
        TimeUnit.MILLISECONDS.sleep(50);
        int localResult = guarded.applyAsInt(3);
        t.join();

        assertEquals(501, localResult);
        assertEquals(501, backgroundResultContainer.get());

        long start = System.currentTimeMillis();
        localResult = guarded.applyAsInt(3);
        long used = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis() - start);
        assertTrue(used <= 1);
        assertEquals(501, localResult);
    }
}
