package org.fiolino.common.util;

import org.junit.jupiter.api.Test;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class CachedTest {

    @Test
    void testDelay() throws InterruptedException {
        AtomicInteger counter = new AtomicInteger(0);
        Cached<Integer> getNext = Cached.updateEvery(200).milliseconds().with(counter::incrementAndGet);
        int start = getNext.get();
        assertEquals(1, start);
        TimeUnit.MILLISECONDS.sleep(100);
        int next = getNext.get();
        assertEquals(start, next);
        TimeUnit.MILLISECONDS.sleep(120);
        next = getNext.get();
        assertTrue(next > start);
        assertEquals(2, next);
    }

    @Test
    void testParse() throws InterruptedException {
        AtomicInteger counter = new AtomicInteger(0);
        Cached<Integer> getNext = Cached.updateEvery("80 millis and 120000 micros").with(counter::incrementAndGet);
        int start = getNext.get();
        assertEquals(1, start);
        TimeUnit.MILLISECONDS.sleep(100);
        int next = getNext.get();
        assertEquals(start, next);
        TimeUnit.MILLISECONDS.sleep(120);
        next = getNext.get();
        assertTrue(next > start);
        assertEquals(2, next);
    }

    @Test
    void testForever() throws InterruptedException {
        Cached<Long> getTimestamp = Cached.with(System::currentTimeMillis);
        long start = getTimestamp.get();
        TimeUnit.SECONDS.sleep(2);
        long next = getTimestamp.get();
        assertEquals(start, next);
    }

    @Test
    void testAlways() {
        Cached<String> appendX = Cached.updateAlways("", s -> s + "x");
        for (int i=1; i < 100; i++) {
            String val = appendX.get();
            assertEquals(i, val.length());
            for (int j=0; j < i; j++) {
                assertEquals('x', val.charAt(j));
            }
        }
    }

    @Test
    void failWithNull() {
        Cached<Object> getNull = Cached.with(() -> null);
        assertThrows(NullPointerException.class, getNull::get);
    }

    @Test
    void allowNull() {
        Cached<Object> getNull = Cached.withNullable(() -> null);
        Object result = getNull.get();
        assertNull(result);
    }

    @Test
    void testRefreshNotPossible() throws InterruptedException {
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

    @Test
    void testRefreshNotPossibleOnInitialCall() {
        Cached<Object> neverGetAnything = Cached.with(() -> {
            throw new Cached.RefreshNotPossibleException();
        });
        assertThrows(IllegalStateException.class, neverGetAnything::get);
    }

    @Test
    void testRefreshNotPossibleOnInitialCallNullable() {
        Cached<Object> neverGetAnything = Cached.withNullable(() -> {
            throw new Cached.RefreshNotPossibleException();
        });
        assertNull(neverGetAnything.get());
    }

    @Test
    void testConcurrentStart() throws InterruptedException, ExecutionException {
        AtomicInteger counter = new AtomicInteger(0);
        Cached<Integer> getNextWithDelay = Cached.updateEvery("5 sec").with(() -> {
            try {
                TimeUnit.MILLISECONDS.sleep(222);
            } catch (InterruptedException ex) {
                fail("Interrupted");
            }
            return counter.incrementAndGet();
        });

        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<Integer> getFromBackground = executor.submit(getNextWithDelay::get);
        TimeUnit.MILLISECONDS.sleep(100);
        long beforeAll = System.currentTimeMillis();
        int fromRunning = getNextWithDelay.get();
        long between = System.currentTimeMillis();
        int fromBackground = getFromBackground.get();
        long afterAll = System.currentTimeMillis();
        assertEquals(1, fromRunning);
        assertEquals(1, fromBackground);

        // Check times
        assertTrue(between - beforeAll >= 120); // Had to wait until background task was ready
        assertTrue(between - beforeAll < 200); // But some time already passed in first sleep call
        assertTrue(afterAll - between < 10); // Couldn't be mich time after first call was done
    }

    @Test
    void testConcurrentAccess() throws InterruptedException, ExecutionException {
        AtomicInteger counter = new AtomicInteger(0);
        Cached<Integer> getNextWithDelay = Cached.updateEvery("200 millis").with(() -> {
            if (counter.get() > 0) {
                try {
                    TimeUnit.MILLISECONDS.sleep(300);
                } catch (InterruptedException ex) {
                    fail("Interrupted");
                }
            }
            return counter.incrementAndGet();
        });

        int start = getNextWithDelay.get();
        assertEquals(1, start);
        TimeUnit.MILLISECONDS.sleep(30);
        int next = getNextWithDelay.get();
        assertEquals(1, next);
        TimeUnit.MILLISECONDS.sleep(300); // Now it should be expired
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<Integer> getFromBackground = executor.submit(getNextWithDelay::get);
        TimeUnit.MILLISECONDS.sleep(30);
        // Background thread should still be active
        next = getNextWithDelay.get(); // Since background is active, the cached value is returned
        assertEquals(1, next);
        int fromBackground = getFromBackground.get();
        assertEquals(2, fromBackground);
        next = getNextWithDelay.get();
        assertEquals(2, next);
    }
}
