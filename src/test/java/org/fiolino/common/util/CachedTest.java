package org.fiolino.common.util;

import org.junit.Test;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class CachedTest {

    @Test
    public void testDelay() throws InterruptedException {
        Cached<Long> getTimestamp = Cached.updateEvery(200).milliseconds().with(System::currentTimeMillis);
        long start = getTimestamp.get();
        TimeUnit.MILLISECONDS.sleep(100);
        long next = getTimestamp.get();
        assertEquals(start, next);
        TimeUnit.MILLISECONDS.sleep(120);
        next = getTimestamp.get();
        assertTrue(next > start);
    }

    @Test
    public void testParse() throws InterruptedException {
        Cached<Long> getTimestamp = Cached.updateEvery("80 millis and 120000 micros").with(System::currentTimeMillis);
        long start = getTimestamp.get();
        TimeUnit.MILLISECONDS.sleep(100);
        long next = getTimestamp.get();
        assertEquals(start, next);
        TimeUnit.MILLISECONDS.sleep(120);
        next = getTimestamp.get();
        assertTrue(next > start);
    }

    @Test
    public void testForever() throws InterruptedException {
        Cached<Long> getTimestamp = Cached.with(System::currentTimeMillis);
        long start = getTimestamp.get();
        TimeUnit.SECONDS.sleep(2);
        long next = getTimestamp.get();
        assertEquals(start, next);
    }

    @Test
    public void testAlways() {
        Cached<String> appendX = Cached.updateAlways("", s -> s + "x");
        for (int i=1; i < 100; i++) {
            String val = appendX.get();
            assertEquals(i, val.length());
            for (int j=0; j < i; j++) {
                assertEquals('x', val.charAt(j));
            }
        }
    }

    @Test(expected = NullPointerException.class)
    public void failWithNull() {
        Cached<Object> getNull = Cached.with(() -> null);
        getNull.get();
    }

    @Test
    public void allowNull() {
        Cached<Object> getNull = Cached.withNullable(() -> null);
        Object result = getNull.get();
        assertNull(result);
    }

    @Test
    public void testRefreshNotPossible() throws InterruptedException {
        AtomicInteger counter = new AtomicInteger(0);
        Cached<String> appendX = Cached.updateEvery(10).milliseconds().with("", s -> {
            if (counter.incrementAndGet() > 3) throw new Cached.RefreshNotPossibleException();
            else return s + "x";
        });

        String x = appendX.get();
        assertEquals("x", x);
        x = appendX.get();
        assertEquals("x", x);
        TimeUnit.MILLISECONDS.sleep(25);
        x = appendX.get();
        assertEquals("xx", x);
        TimeUnit.MILLISECONDS.sleep(25);
        x = appendX.get();
        assertEquals("xxx", x);
        TimeUnit.MILLISECONDS.sleep(25);
        x = appendX.get();
        assertEquals("xxx", x);
        TimeUnit.MILLISECONDS.sleep(25);
        x = appendX.get();
        assertEquals("xxx", x);
        TimeUnit.MILLISECONDS.sleep(25);
        x = appendX.get();
        assertEquals("xxx", x);
    }

    @Test(expected = IllegalStateException.class)
    public void testRefreshNotPossibleOnInitialCall() {
        Cached<Object> neverGetAnything = Cached.with(() -> {
            throw new Cached.RefreshNotPossibleException();
        });
        neverGetAnything.get();
    }

    @Test
    public void testRefreshNotPossibleOnInitialCallNullable() {
        Cached<Object> neverGetAnything = Cached.withNullable(() -> {
            throw new Cached.RefreshNotPossibleException();
        });
        assertNull(neverGetAnything.get());
    }
}
