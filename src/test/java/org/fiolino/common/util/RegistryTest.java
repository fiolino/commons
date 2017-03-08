package org.fiolino.common.util;

import org.junit.Test;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.util.concurrent.atomic.AtomicInteger;

import static java.lang.invoke.MethodHandles.lookup;
import static java.lang.invoke.MethodType.methodType;
import static org.junit.Assert.assertEquals;

/**
 * Created by kuli on 08.03.17.
 */
public class RegistryTest {

    @Test
    public void testNoArgumentsNoReturn() throws Throwable {
        MethodHandles.Lookup lookup = lookup();
        MethodHandle incrementCounter = lookup.findStatic(lookup.lookupClass(), "incrementCounter", methodType(void.class));
        Registry reg = new RegistryBuilder().buildFor(incrementCounter);

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
        reg = new RegistryBuilder().buildFor(incrementCounter);

        callOnlyOnce = reg.getAccessor();
        counter.set(1);
        callOnlyOnce.invokeExact();
        assertEquals(2, counter.get());

        reg = new RegistryBuilder().buildFor(incrementCounter);

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
        Registry reg = new RegistryBuilder().buildFor(incrementCounter);

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
        reg = new RegistryBuilder().buildFor(incrementCounter);

        callOnlyOnce = reg.getAccessor();
        counter.set(1);
        value = (int) callOnlyOnce.invokeExact();
        assertEquals(2, value);

        reg = new RegistryBuilder().buildFor(incrementCounter);

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
}
