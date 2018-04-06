package org.fiolino.common.timer;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimersTest {
    @Test
    void testContinuousSupplier() throws InterruptedException {
        Instant start = Instant.now();
        Supplier<Instant> s = Timers.continuousTemporalSupplier(i -> i.plus(5, ChronoUnit.MILLIS), start);
        TimeUnit.MILLISECONDS.sleep(20);
        Instant next = s.get();
        assertEquals(5, next.toEpochMilli() - start.toEpochMilli());
        assertTrue(Instant.now().toEpochMilli() - next.toEpochMilli() >= 15);
        TimeUnit.MILLISECONDS.sleep(20);
        next = s.get();
        assertEquals(10, next.toEpochMilli() - start.toEpochMilli());
        assertTrue(Instant.now().toEpochMilli() - next.toEpochMilli() >= 30);
    }

    @Test
    void testAdHocSupplier() throws InterruptedException {
        Instant start = Instant.now();
        Supplier<Instant> s = Timers.adHocTemporalSupplier(i -> i.plus(5, ChronoUnit.MILLIS), Instant::now);
        TimeUnit.MILLISECONDS.sleep(20);
        Instant next = s.get();
        assertTrue(next.toEpochMilli() - start.toEpochMilli() >= 25);
        TimeUnit.MILLISECONDS.sleep(20);
        next = s.get();
        assertTrue(next.toEpochMilli() - start.toEpochMilli() >= 45);
    }
}
