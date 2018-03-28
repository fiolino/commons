package org.fiolino.common.util;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.*;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.LongUnaryOperator;

public final class Cron implements Serializable, TemporalAdjuster {
    private static final String[] MONTHS = {
            null, "jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec"
    };
    private static final String[] WEEK_DAYS = {
            "sun", "mon", "tue", "wed", "thu", "fri", "sat"
    };
    private static final int IDX_MINUTE = 0;
    private static final int IDX_HOUR = 1;
    private static final int IDX_DAY_OF_MONTH = 2;
    private static final int IDX_MONTH = 3;
    private static final int IDX_DAY_OF_WEEK = 4;

    public static final String WILDCARD_SYMBOL = "*";

    private final PrintableAdjuster adjuster;

    public static Cron forDateTime(String cronDef) {
        return forDateTime(cronDef.split("\\s+"));
    }

    public static Cron forDateTime(String... units) {
        if (units.length < 5 || units.length > 5 && !units[5].startsWith("#") && !units[5].startsWith("//")) {
            throw new IllegalArgumentException("Bad syntax: " + Arrays.toString(units) + " should be like 'min hour day month weekday'");
        }

        return new Cron(units);
    }

    private Cron(String[] units) {
        adjuster = createFrom(units);
    }

    @Override
    public Temporal adjustInto(Temporal temporal) {
        Temporal t = temporal.with(ChronoField.NANO_OF_SECOND, 0).with(ChronoField.SECOND_OF_MINUTE, 0);
        return adjuster.adjustInto(t);
    }

    @Override
    public boolean equals(Object obj) {
        return obj == this ||
                obj != null && obj.getClass().equals(getClass()) && adjuster.equals(((Cron) obj).adjuster);
    }

    @Override
    public int hashCode() {
        return adjuster.hashCode() * 31;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("Cron ");
        LocalDateTime nextRun = (LocalDateTime) adjustInto(LocalDateTime.now(ZoneId.systemDefault()));
        return adjuster.printInto(sb).append(" next match at ").append(nextRun).toString();
    }

    private static PrintableAdjuster createFrom(String[] units) {
        Unit month = createFrom(FinalUnit.FINAL_UNIT, ChronoField.MONTH_OF_YEAR, units[IDX_MONTH], LongUnaryOperator.identity(), MONTHS);
        Unit weekday = createFrom(month, ChronoField.DAY_OF_WEEK, units[IDX_DAY_OF_WEEK], x -> x == 7 ? 0 : x, WEEK_DAYS);
        Unit monthday = createFrom(month, ChronoField.DAY_OF_MONTH, units[IDX_DAY_OF_MONTH]);
        if (weekday instanceof WildcardUnit) {
            return buildFromDay(monthday, units);
        } else if (monthday instanceof WildcardUnit) {
            return buildFromDay(weekday, units);
        }

        return new AlternativeAdjuster(buildFromDay(monthday, units), buildFromDay(weekday, units));
    }

    private static PrintableAdjuster buildFromDay(Unit dayUnit, String[] units) {
        Unit hour = createFrom(dayUnit, ChronoField.HOUR_OF_DAY, units[IDX_HOUR]);
        Unit minute = createFrom(hour, ChronoField.MINUTE_OF_HOUR, units[IDX_MINUTE]);
        return minute instanceof WildcardUnit ? new WildcardMinute(hour) : new MinuteWrapper(minute);
    }

    private static final String[] NO_CONSTANTS = new String[0];

    private static Unit createFrom(Unit parent, TemporalField field, String unit) {
        return createFrom(parent, field, unit, LongUnaryOperator.identity(), NO_CONSTANTS);
    }

    private static Unit createFrom(Unit parent, TemporalField field, String unit, LongUnaryOperator op, String... constants) {
        if (unit.equals(WILDCARD_SYMBOL)) {
            return new WildcardUnit(field, parent);
        }
        long value = Arrays.binarySearch(constants, unit);
        if (value < 0) {
            try {
                value = Long.parseLong(unit);
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("Bad format in " + unit);
            }
        }
        if (value < 0)
            return new NegativeSingleValueUnit(field, parent, value);
        return new SingleValueUnit(field, parent, op.applyAsLong(value));
    }

    private interface PrintableAdjuster extends TemporalAdjuster {
        StringBuilder printInto(StringBuilder sb);
    }

    private interface Unit extends PrintableAdjuster {
        Temporal addOne(Temporal temporal);
    }

    private enum FinalUnit implements Unit {
        FINAL_UNIT;

        @Override
        public Temporal addOne(Temporal temporal) {
            return temporal;
        }

        @Override
        public Temporal adjustInto(Temporal temporal) {
            return temporal;
        }

        @Override
        public StringBuilder printInto(StringBuilder sb) {
            // Prints nothing
            return sb;
        }
    }

    private static final class MinuteWrapper implements PrintableAdjuster {
        private final Unit realMinute;

        MinuteWrapper(Unit realMinute) {
            this.realMinute = realMinute;
        }

        @Override
        public Temporal adjustInto(Temporal temporal) {
            return realMinute.adjustInto(realMinute.addOne(temporal));
        }

        @Override
        public boolean equals(Object obj) {
            return obj != null && obj.getClass().equals(MinuteWrapper.class) && ((MinuteWrapper) obj).realMinute.equals(realMinute);
        }

        @Override
        public int hashCode() {
            return realMinute.hashCode() * 17;
        }

        @Override
        public StringBuilder printInto(StringBuilder sb) {
            return realMinute.printInto(sb);
        }
    }

    private static final class WildcardMinute implements PrintableAdjuster {
        private final Unit hour;

        WildcardMinute(Unit hour) {
            this.hour = hour;
        }

        @Override
        public Temporal adjustInto(Temporal temporal) {
            Temporal t = hour.addOne(temporal);
            ValueRange r = t.range(ChronoField.MINUTE_OF_HOUR);
            long nextMinute = ThreadLocalRandom.current().nextLong(r.getMinimum(), r.getMaximum() + 1);
            return temporal.with(ChronoField.MINUTE_OF_HOUR, nextMinute);
        }

        @Override
        public boolean equals(Object obj) {
            return obj != null && obj.getClass().equals(WildcardMinute.class) && ((WildcardMinute) obj).hour.equals(hour);
        }

        @Override
        public int hashCode() {
            return hour.hashCode() * 17;
        }

        @Override
        public StringBuilder printInto(StringBuilder sb) {
            sb.append(ChronoField.MINUTE_OF_HOUR.toString()).append(": ?, ");
            return hour.printInto(sb);
        }
    }

    private static final class AlternativeAdjuster implements PrintableAdjuster {
        private final TemporalAdjuster first, second;

        AlternativeAdjuster(TemporalAdjuster first, TemporalAdjuster second) {
            this.first = first;
            this.second = second;
        }

        @Override
        public Temporal adjustInto(Temporal temporal) {
            Temporal t1 = first.adjustInto(temporal);
            Temporal t2 = second.adjustInto(temporal);
            if (ChronoUnit.SECONDS.between(t1, t2) < 0) {
                // t1 is later
                return t2;
            } else {
                return t1;
            }
        }

        @Override
        public StringBuilder printInto(StringBuilder sb) {
            return sb.append("Either (").append(first).append(") or (").append(second).append(")");
        }
    }

    private static abstract class CalendarUnit implements Unit {
        final TemporalField field;
        final Unit next;

        CalendarUnit(TemporalField field, Unit next) {
            this.field = field;
            this.next = next;
        }

        @Override
        public Temporal addOne(Temporal temporal) {
            return temporal.plus(1, field.getBaseUnit());
        }

        @Override
        public final StringBuilder printInto(StringBuilder sb) {
            sb.append(field).append(": ");
            printValue(sb);
            sb.append(", ");
            return next.printInto(sb);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            CalendarUnit that = (CalendarUnit) o;
            return Objects.equals(field, that.field) &&
                    Objects.equals(next, that.next);
        }

        @Override
        public int hashCode() {
            return Objects.hash(field, next);
        }

        abstract void printValue(StringBuilder sb);
    }

    private static class WildcardUnit extends CalendarUnit {
        WildcardUnit(TemporalField field, Unit next) {
            super(field, next);
        }

        @Override
        public Temporal adjustInto(Temporal temporal) {
            return next.adjustInto(temporal);
        }

        @Override
        void printValue(StringBuilder sb) {
            sb.append('*');
        }
    }

    private static class SingleValueUnit extends CalendarUnit {

        private final long value;

        SingleValueUnit(TemporalField field, Unit next, long value) {
            super(field, next);
            this.value = value;
            assert value >= 0;
        }

        @Override
        public Temporal adjustInto(Temporal temporal) {
            long actual = temporal.getLong(field);
            Temporal t;
            long check = getValue(temporal);
            if (actual > check) {
                t = next.addOne(temporal);
            } else {
                t = temporal;
            }
            t = t.with(field, check);
            return next.adjustInto(t);
        }

        long getValue(Temporal temporal) {
            return value;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            if (!super.equals(o)) return false;
            SingleValueUnit that = (SingleValueUnit) o;
            return value == that.value;
        }

        @Override
        public int hashCode() {
            return Objects.hash(super.hashCode(), value);
        }

        @Override
        void printValue(StringBuilder sb) {
            sb.append(value);
        }
    }

    private static class NegativeSingleValueUnit extends SingleValueUnit {

        NegativeSingleValueUnit(TemporalField field, Unit next, long value) {
            super(field, next, value + 1);
            assert value < 0;
        }

        @Override
        long getValue(Temporal temporal) {
            ValueRange r = temporal.range(field);
            return r.getMaximum() + super.getValue(temporal); // value is negative
        }
    }
}