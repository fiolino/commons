package org.fiolino.common.timer;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.Temporal;
import java.time.temporal.TemporalAdjuster;
import java.util.function.Supplier;

public class AdHocTemporalSupplier<T extends Temporal> implements Supplier<T> {
    private final TemporalAdjuster adjuster;
    private final Supplier<T> supplier;

    public AdHocTemporalSupplier(TemporalAdjuster adjuster, Supplier<T> supplier) {
        this.adjuster = adjuster;
        this.supplier = supplier;
    }

    public static AdHocTemporalSupplier<LocalDateTime> forLocalDateTime(TemporalAdjuster adjuster, Clock clock) {
        return new AdHocTemporalSupplier<>(adjuster, () -> LocalDateTime.now(clock));
    }

    public static AdHocTemporalSupplier<LocalDateTime> forLocalDateTime(TemporalAdjuster adjuster, ZoneId zoneId) {
        return new AdHocTemporalSupplier<>(adjuster, () -> LocalDateTime.now(zoneId));
    }

    @Override @SuppressWarnings("unchecked")
    public T get() {
        return (T) adjuster.adjustInto(supplier.get());
    }
}
