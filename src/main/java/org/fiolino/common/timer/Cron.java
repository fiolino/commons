package org.fiolino.common.timer;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.*;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.LongUnaryOperator;
import java.util.regex.Pattern;

public final class Cron implements Serializable, TemporalAdjuster {
    private static final List<String> MONTHS = Arrays.asList(null, "jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec");
    private static final List<String> WEEK_DAYS = Arrays.asList("sun", "mon", "tue", "wed", "thu", "fri", "sat");

    private static final int IDX_DAY_OF_WEEK = 1;
    private static final int IDX_MONTH = 2;
    private static final int IDX_DAY_OF_MONTH = 3;
    private static final int IDX_HOUR = 4;
    private static final int IDX_MINUTE = 5;
    private static final int IDX_SECOND = 6;

    public static final String WILDCARD_SYMBOL = "*";
    public static final String RANDOM_SYMBOL = "?";

    private final TemporalAdjuster adjuster;

    public static Cron forDateTime(String cronDef) {
        int comment = cronDef.indexOf('#');
        if (comment >= 0) cronDef = cronDef.substring(0, comment);
        comment = cronDef.indexOf("//");
        if (comment >= 0) cronDef = cronDef.substring(0, comment);

        return forDateTime(cronDef.split("\\s+"));
    }

    public static Cron forDateTime(String... units) {
        if (units.length < 3 || units.length == 4 || units.length > 6) {
            throw new IllegalCronFormatException("Wrong number of arguments: " + units.length);
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
        LocalDateTime nextRun = (LocalDateTime) adjustInto(LocalDateTime.now(ZoneId.systemDefault()));
        return "Cron; next match at " + nextRun;
    }

    public interface Jumper {}

    @FunctionalInterface
    public interface Adjuster {
        long adjust(long currentValue, ValueRange range);

        default Adjuster join(Adjuster second) {
            return (v, r) -> {
                long v1 = adjust(v, r);
                long v2 = second.adjust(v, r);
                return Math.min(v1, v2);
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
            int n = discreteValues.length;
            switch (n) {
                case 0:
                    throw new IllegalArgumentException("Missing values");
                case 1:
                    return staticValue(discreteValues[0]);
                default:
                    long[] values = Arrays.copyOf(discreteValues, n);
                    Arrays.sort(values);
                    if (values[0] < 0) {
                        // These need to be handled in their own
                        Adjuster a = staticValue(values[0]);
                        long[] remainders = new long[--n];
                        System.arraycopy(discreteValues, 1, remainders, 0, n);
                        return a.join(oneOf(remainders));
                    }
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
            return (Jumper & Adjuster) (v, r) -> ThreadLocalRandom.current().nextLong(r.getMinimum() + 1, r.getMaximum() + 1) * -1;
        }

        static Adjuster parse(String input, String... constants) {
            return Cron.parse(input, LongUnaryOperator.identity(), Arrays.asList(constants));
        }
    }

    private static Adjuster parse(String input, LongUnaryOperator op, List<String> constants) {
        if (WILDCARD_SYMBOL.equals(input)) return Adjuster.wildcard();
        if (RANDOM_SYMBOL.equals(input)) return Adjuster.random();

        String[] items = input.split("\\s*,\\s*");
        long[] single = null;
        Adjuster adjuster = null;
        for (String i : items) {
            if (i.startsWith("/") || i.startsWith("*/")) {
                i = i.substring(i.charAt(0) == '*' ? 2 : 1);
                Adjuster a = Adjuster.multipleOf(getLong(i, constants));
                adjuster = adjuster == null ? a : adjuster.join(a);
                continue;
            }
            int rangeSplit = i.indexOf('-');
            if (rangeSplit > 0) {
                long min = getLong(i.substring(0, rangeSplit).trim(), constants);
                long max = getLong(i.substring(rangeSplit+1).trim(), constants);
                Adjuster a = Adjuster.range(min, max);
                adjuster = adjuster == null ? a : adjuster.join(a);
                continue;
            }

            // Now only individual numbers are allowed
            long n = getLong(i, constants);
            n = op.applyAsLong(n);
            single = single == null ? new long[1] : Arrays.copyOf(single, single.length + 1);
            single[single.length - 1] = n;
        }

        if (single == null) return adjuster;
        Adjuster a = Adjuster.oneOf(single);
        return adjuster == null ? a : adjuster.join(a);
    }

    private static final Pattern INTEGER = Pattern.compile("-?\\d+");

    private static long getLong(String input, List<String> constants) {
        int idx = constants.indexOf(input.toLowerCase());
        if (idx >= 0) return idx;
        if (INTEGER.matcher(input).matches()) {
            try {
                return Long.parseLong(input);
            } catch (NumberFormatException ex) {
                throw new IllegalCronFormatException(input + " seems to be a number but is not");
            }
        }

        throw new IllegalCronFormatException("Number expected instead of " + input);
    }

    private static TemporalAdjuster createFrom(String[] units) {
        int n=units.length;
        TemporalAdjuster month = createFrom(t -> t, ChronoField.MONTH_OF_YEAR, units[n-IDX_MONTH], LongUnaryOperator.identity(), MONTHS);
        TemporalAdjuster weekday = createFrom(month, ChronoField.DAY_OF_WEEK, units[n-IDX_DAY_OF_WEEK], x -> x == 7 ? 0 : x, WEEK_DAYS);
        TemporalAdjuster monthday = createFrom(month, ChronoField.DAY_OF_MONTH, units[n-IDX_DAY_OF_MONTH]);
        TemporalAdjuster day;
        if (weekday == month) {
            day = monthday;
        } else if (monthday == month) {
            day = weekday;
        } else {
            day = new AlternativeAdjuster(weekday, monthday);
        }
        if (n == 3) {
            return new StartingUnit(ChronoField.DAY_OF_YEAR, day);
        }

        TemporalAdjuster a = createFrom(day, ChronoField.HOUR_OF_DAY, units[n-IDX_HOUR]);
        a = createFrom(a, ChronoField.MINUTE_OF_HOUR, units[n-IDX_MINUTE]);
        if (n == 5) {
            // No second field
            a = new StartingUnit(ChronoField.MINUTE_OF_DAY, a);
            a = new Initializer(ChronoField.SECOND_OF_MINUTE, a);
        } else {
            // Seconds given
            a = createFrom(a, ChronoField.SECOND_OF_MINUTE, units[n-IDX_SECOND]);
            a = new StartingUnit(ChronoField.SECOND_OF_DAY, a);
        }

        a = new Initializer(ChronoField.MILLI_OF_SECOND, a);
        return new Initializer(ChronoField.NANO_OF_SECOND, a);
    }

    private static TemporalAdjuster createFrom(TemporalAdjuster parent, TemporalField field, String unit) {
        return createFrom(parent, field, unit, LongUnaryOperator.identity(), Collections.emptyList());
    }

    private static TemporalAdjuster createFrom(TemporalAdjuster parent, TemporalField field, String unit, LongUnaryOperator op, List<String> constants) {
        if (unit.equals(WILDCARD_SYMBOL)) {
            return parent;
        }
        Adjuster a = Cron.parse(unit, op, constants);
        return new AdjustingUnit(field, parent, a);
    }

    private static abstract class CalendarUnit implements TemporalAdjuster {
        private final TemporalAdjuster next;
        private final TemporalField field;

        CalendarUnit(TemporalField field, TemporalAdjuster next) {
            this.next = next;
            this.field = field;
        }

        @Override
        public Temporal adjustInto(Temporal t) {
            if (t.isSupported(field)) {
                t = adjustInto(field, t);
            }
            return next.adjustInto(t);
        }

        abstract Temporal adjustInto(TemporalField field, Temporal temporal);

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            if (!super.equals(o)) return false;
            CalendarUnit that = (CalendarUnit) o;
            return field.equals(that.field) && next.equals(that.next);
        }

        @Override
        public int hashCode() {
            return next.hashCode() * 31 + field.hashCode();
        }
    }

    private static final class Initializer extends CalendarUnit {
        Initializer(TemporalField field, TemporalAdjuster next) {
            super(field, next);
        }

        @Override
        public Temporal adjustInto(TemporalField field, Temporal temporal) {
            return temporal.with(field, 0);
        }
    }

    private static final class StartingUnit extends CalendarUnit {
        StartingUnit(TemporalField field, TemporalAdjuster next) {
            super(field, next);
        }

        @Override
        public Temporal adjustInto(TemporalField field, Temporal temporal) {
            return temporal.plus(1, field.getBaseUnit());
        }
    }

    private static final class AlternativeAdjuster implements TemporalAdjuster {
        private final TemporalAdjuster first, second;

        AlternativeAdjuster(TemporalAdjuster first, TemporalAdjuster second) {
            this.first = first;
            this.second = second;
        }

        @Override
        public Temporal adjustInto(Temporal temporal) {
            Temporal t1 = first.adjustInto(temporal);
            Temporal t2 = second.adjustInto(temporal);
            if (ChronoUnit.MILLIS.between(t1, t2) < 0) {
                // t1 is later
                return t2;
            } else {
                return t1;
            }
        }
    }

    private static class AdjustingUnit extends CalendarUnit {

        private final Adjuster adjuster;

        AdjustingUnit(TemporalField field, TemporalAdjuster next, Adjuster adjuster) {
            super(field, next);
            this.adjuster = adjuster;
        }

        @Override
        public Temporal adjustInto(TemporalField field, Temporal temporal) {
            long actual = temporal.getLong(field);
            ValueRange range = temporal.range(field);
            Temporal t = temporal;
            long check;
            if (adjuster instanceof Jumper) {
                t = addOne(field, t);
                check = adjuster.adjust(actual, range);
            } else {
                check = adjuster.adjust(actual, range);
                if (actual > check) {
                    t = addOne(field, t);
                }
            }
            return t.with(field, check);
        }

        private Temporal addOne(TemporalField field, Temporal temporal) {
            TemporalUnit rangeUnit = field.getRangeUnit();
            if (temporal.isSupported(rangeUnit)) {
                return temporal.plus(1, rangeUnit);
            }
            return temporal;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            if (!super.equals(o)) return false;
            AdjustingUnit that = (AdjustingUnit) o;
            return adjuster.equals(that.adjuster);
        }

        @Override
        public int hashCode() {
            return super.hashCode() * 31 + adjuster.hashCode();
        }
    }
}