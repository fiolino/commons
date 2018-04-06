package org.fiolino.common.timer;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.Temporal;
import java.time.temporal.TemporalAdjuster;
import java.util.function.Supplier;

public final class Timers {

    private Timers() {
        throw new AssertionError();
    }

    public static <T extends Temporal> Supplier<T> continuousTemporalSupplier(TemporalAdjuster adjuster, T start) {
        return new ContinuousTemporalSupplier<>(adjuster, start);
    }

    public static Supplier<LocalDateTime> continuousTemporalSupplier(TemporalAdjuster adjuster, Clock clock) {
        return continuousTemporalSupplier(adjuster, LocalDateTime.now(clock));
    }

    public static Supplier<LocalDateTime> continuousTemporalSupplier(TemporalAdjuster adjuster, ZoneId zoneId) {
        return continuousTemporalSupplier(adjuster, LocalDateTime.now(zoneId));
    }

    @SuppressWarnings("unchecked")
    public static <T extends Temporal> Supplier<T> adHocTemporalSupplier(TemporalAdjuster adjuster, Supplier<T> supplier) {
        return () -> (T) adjuster.adjustInto(supplier.get());
    }

    public static Supplier<LocalDateTime> adHocTemporalSupplier(TemporalAdjuster adjuster, Clock clock) {
        return adHocTemporalSupplier(adjuster, () -> LocalDateTime.now(clock));
    }

    public static Supplier<LocalDateTime> adHocTemporalSupplier(TemporalAdjuster adjuster, ZoneId zoneId) {
        return adHocTemporalSupplier(adjuster, () -> LocalDateTime.now(zoneId));
    }

    private static class ContinuousTemporalSupplier<T extends Temporal> implements Supplier<T> {
        private final TemporalAdjuster adjuster;
        private volatile T lastInstant;

        private static final VarHandle LAST_INSTANT;

        static {
            try {
                MethodHandles.Lookup lookup = MethodHandles.lookup();
                LAST_INSTANT = lookup.findVarHandle(lookup.lookupClass(), "lastInstant", Temporal.class);
            } catch (NoSuchFieldException | IllegalAccessException ex) {
                throw new AssertionError(ex);
            }
        }

        ContinuousTemporalSupplier(TemporalAdjuster adjuster, T start) {
            this.adjuster = adjuster;
            lastInstant = start;
        }

        @Override @SuppressWarnings("unchecked")
        public T get() {
            T last = lastInstant;
            T expected, next;
            do {
                expected = last;
                next = (T) adjuster.adjustInto(last);
            } while ((last = (T) LAST_INSTANT.compareAndExchange(this, expected, next)) != expected);

            return next;
        }
    }

}
