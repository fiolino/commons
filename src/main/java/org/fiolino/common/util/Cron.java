package org.fiolino.common.util;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.*;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.LongUnaryOperator;
import java.util.regex.Pattern;

public final class Cron implements Serializable, TemporalAdjuster {
    private static final List<String> MONTHS = Arrays.asList(null, "jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec");
    private static final List<String> WEEK_DAYS = Arrays.asList("sun", "mon", "tue", "wed", "thu", "fri", "sat");

    private static final int IDX_MINUTE = 0;
    private static final int IDX_HOUR = 1;
    private static final int IDX_DAY_OF_MONTH = 2;
    private static final int IDX_MONTH = 3;
    private static final int IDX_DAY_OF_WEEK = 4;

    public static final String WILDCARD_SYMBOL = "*";
    public static final String RANDOM_SYMBOL = "?";

    private final Unit unit;

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
        unit = createFrom(units);
    }

    @Override
    public Temporal adjustInto(Temporal temporal) {
        Temporal t = temporal.with(ChronoField.NANO_OF_SECOND, 0).with(ChronoField.SECOND_OF_MINUTE, 0);
        return unit.adjustInto(t);
    }

    @Override
    public boolean equals(Object obj) {
        return obj == this ||
                obj != null && obj.getClass().equals(getClass()) && unit.equals(((Cron) obj).unit);
    }

    @Override
    public int hashCode() {
        return unit.hashCode() * 31;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("Cron ");
        LocalDateTime nextRun = (LocalDateTime) adjustInto(LocalDateTime.now(ZoneId.systemDefault()));
        return unit.printInto(sb).append(" next match at ").append(nextRun).toString();
    }

    @FunctionalInterface
    public interface Adjuster {
        long adjust(long currentValue, ValueRange range);

        default Adjuster join(Adjuster second) {
            return (v, r) -> {
                long v1 = adjust(v, r);
                long v2 = second.adjust(v, r);
                long min = Math.min(v1, v2);
                if (min >= v) return min;
                long max = Math.max(v1, v2);
                if (max < v) {
                    if (v1 < 0) {
                        return v2 >= 0 ? v2 : max;
                    }
                    return v2 < 0 ? v1 : min;
                }
                return v1 >= v ? v1 : v2;
            };
        }

        static Adjuster staticValue(long value) {
            if (value >= 0) {
                return (v, r) -> Math.max(r.getMinimum(), Math.min(r.getMaximum(), value));
            } else {
                return (v, r) -> r.getMaximum() + value + 1;
            }
        }

        static Adjuster oneOf(long... discreteValues) {
            switch (discreteValues.length) {
                case 0:
                    throw new IllegalArgumentException("Missing values");
                case 1:
                    return staticValue(discreteValues[0]);
                default:
                    long[] values = Arrays.copyOf(discreteValues, discreteValues.length);
                    Arrays.sort(values);
                    return (v, r) -> {
                        int idx = Arrays.binarySearch(values, v);
                        if (idx >= 0) return v;
                        idx = (idx * -1) - 1;
                        if (idx == values.length) return values[0];
                        return Math.max(r.getMinimum(), Math.min(r.getMaximum(), values[idx]));
                    };
            }
        }

        static Adjuster range(long min, long max) {
            if (min == max) return staticValue(min);
            if (min < max) {
                return (v, r) -> v < min || v > max ? min : v;
            }
            // Inverse range
            return (v, r) -> v >= min || v <= max ? v : (min > r.getMaximum() ? r.getMinimum() : min);
        }

        static Adjuster multipleOf(long value) {
            if (value <= 1) throw new IllegalArgumentException(value + " must be 2 or greater");
            return (v, r) -> {
                long m = v % value;
                return m == 0 ? v : v + value - m;
            };
        }

        static Adjuster wildcard() {
            return (v, r) -> v;
        }

        static Adjuster random() {
            return (v, r) -> ThreadLocalRandom.current().nextLong(r.getMinimum() + 1, r.getMaximum() + 1) * -1;
        }

        static Adjuster parse(String input, String... constants) {
            if (WILDCARD_SYMBOL.equals(input)) return wildcard();
            if (RANDOM_SYMBOL.equals(input)) return random();

            String[] items = input.split("\\s*,\\s*");
            List<String> c = Arrays.asList(constants);
            long[] single = null;
            Adjuster adjuster = null;
            for (String i : items) {
                if (i.startsWith("/") || i.startsWith("*/")) {
                    i = i.substring(i.charAt(0) == '*' ? 2 : 1);
                    Adjuster a = multipleOf(getLong(i, c));
                    adjuster = adjuster == null ? a : adjuster.join(a);
                    continue;
                }
                int rangeSplit = i.indexOf('-');
                if (rangeSplit > 0) {
                    long min = getLong(i.substring(0, rangeSplit).trim(), c);
                    long max = getLong(i.substring(rangeSplit+1).trim(), c);
                    Adjuster a = range(min, max);
                    adjuster = adjuster == null ? a : adjuster.join(a);
                    continue;
                }

                // Now only individual numbers are allowed
                long n = getLong(i, c);
                single = single == null ? new long[1] : Arrays.copyOf(single, single.length + 1);
                single[single.length - 1] = n;
            }

            if (single == null) return adjuster;
            Adjuster a = oneOf(single);
            return adjuster == null ? a : adjuster.join(a);
        }
    }

    private static final Pattern INTEGER = Pattern.compile("-?\\d+");

    private static long getLong(String input, List<String> constants) {
        int idx = constants.indexOf(input.toLowerCase());
        if (idx >= 0) return idx;
        if (INTEGER.matcher(input).matches()) {
            try {
                return Long.parseLong(input);
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException(input + " seems to be a number but is not");
            }
        }

        throw new IllegalArgumentException("Number expected instead of " + input);
    }

    private static Unit createFrom(String[] units) {
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

    private static Unit buildFromDay(Unit dayUnit, String[] units) {
        CalendarUnit hour = createFrom(dayUnit, ChronoField.HOUR_OF_DAY, units[IDX_HOUR]);
        CalendarUnit minute = createFrom(hour, ChronoField.MINUTE_OF_HOUR, units[IDX_MINUTE]);
        return minute instanceof WildcardUnit ? new WildcardMinute(hour) : new MinuteWrapper(minute);
    }

    private static CalendarUnit createFrom(Unit parent, TemporalField field, String unit) {
        return createFrom(parent, field, unit, LongUnaryOperator.identity(), Collections.emptyList());
    }

    private static CalendarUnit createFrom(Unit parent, TemporalField field, String unit, LongUnaryOperator op, List<String> constants) {
        if (unit.equals(WILDCARD_SYMBOL)) {
            return new WildcardUnit(field, parent);
        }
        long value = constants.indexOf(unit);
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

    private interface Unit extends TemporalAdjuster {
        StringBuilder printInto(StringBuilder sb);
    }

    private enum FinalUnit implements Unit {
        FINAL_UNIT;

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

    private static final class MinuteWrapper implements Unit {
        private final CalendarUnit realUnit;

        MinuteWrapper(CalendarUnit realUnit) {
            this.realUnit = realUnit;
        }

        @Override
        public Temporal adjustInto(Temporal temporal) {
            return realUnit.adjustInto(temporal.plus(1, realUnit.field.getBaseUnit()));
        }

        @Override
        public boolean equals(Object obj) {
            return obj != null && obj.getClass().equals(MinuteWrapper.class) && ((MinuteWrapper) obj).realUnit.equals(realUnit);
        }

        @Override
        public int hashCode() {
            return realUnit.hashCode() * 17;
        }

        @Override
        public StringBuilder printInto(StringBuilder sb) {
            return realUnit.printInto(sb);
        }
    }

    private static final class WildcardMinute implements Unit {
        private final CalendarUnit hour;

        WildcardMinute(CalendarUnit hour) {
            this.hour = hour;
        }

        @Override
        public Temporal adjustInto(Temporal temporal) {
            Temporal t = temporal.plus(1, hour.field.getBaseUnit());
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

    private static final class AlternativeAdjuster implements Unit {
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
            Temporal t = temporal;
            long check = getValue(t);
            if (actual > check) {
                t = temporal.plus(1, field.getRangeUnit());
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