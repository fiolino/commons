package org.fiolino.common.time;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.*;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.LongUnaryOperator;
import java.util.regex.Pattern;

/**
 * Creates a {@link TemporalAdjuster} that sets a given {@link Temporal} to next next defined cron date.
 * <p></p>
 * You can parse cron formats analogue to the Unix cron format:
 * <p></p>
 * min(0-59) hour(0-23) dayOfMonth(1-31) month(1-12 or JAN,FEB,...) dayOfWeek(0-7 or MON,TUE,...)
 * <p></p>
 * In this case, the next date will be set to the beginning of the defined minute.
 * <p></p>
 * You can also specify a leading value for the seconds(0-59); in this case, you have to specify six elements.
 * Then the next date will be set to the beginning of the defined second.
 * <p></p>
 * You can also specify only the last three elements and get an adjuster only for dates; in this case, the fields for
 * hour, minute and second remain untouched, so the next returned date will not necessarily be at the beginning of a day
 * when the Temporal object is time based.
 * <p></p>
 * For each element, you have the following options:
 * <p></p>
 */
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
        return adjuster.adjustInto(temporal);
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

    /**
     * When an adjuster implements this marker interface, then the field's range unit value will <b>always</b> be increased
     * by one, while it's normally only increased when the adjuster's new value is smaller than the existing one.
     */
    public interface Jumper {}

    /**
     * When an adjuster implements this marker interface, then it will be ignored completely if multiple adjusters
     * for the same {@link TemporalUnit} exists.
     * Otherwise, these adjusters will be joined so that the next following value of both is taken.
     */
    public interface Ignorable {}

    @FunctionalInterface
    public interface Adjuster extends Serializable {
        long adjust(long currentValue, ValueRange range);

        default Adjuster join(Adjuster second) {
            if (second instanceof Ignorable) return this;
            if (this instanceof Ignorable) return second;
            return (v, r) -> {
                long v1 = adjust(v, r);
                long v2 = second.adjust(v, r);
                return v1 >= v ? (v2 >= v ? Math.min(v1, v2) : v1) : (v2 >= v ? v2 : Math.min(v1, v2));
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
            return (Ignorable & Adjuster) (v, r) -> v;
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
            int rangeSplit = i.indexOf("..", 1);
            int splitLength = 2;
            if (rangeSplit < 0) {
                rangeSplit = i.indexOf('-', 1);
                splitLength = 1;
            }
            if (rangeSplit > 0) {
                long min = getLong(i.substring(0, rangeSplit).trim(), constants);
                long max = getLong(i.substring(rangeSplit + splitLength).trim(), constants);
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
            return new StartingUnit(day, ChronoUnit.DAYS, ChronoUnit.MONTHS, ChronoUnit.YEARS);
        }

        TemporalAdjuster a = createFrom(day, ChronoField.HOUR_OF_DAY, units[n-IDX_HOUR]);
        a = createFrom(a, ChronoField.MINUTE_OF_HOUR, units[n-IDX_MINUTE]);
        if (n == 5) {
            // No second field
            a = new StartingUnit(a, ChronoUnit.MINUTES, ChronoUnit.HOURS, ChronoUnit.DAYS, ChronoUnit.MONTHS, ChronoUnit.YEARS);
            a = new Initializer(ChronoField.SECOND_OF_MINUTE, a);
        } else {
            // Seconds given
            a = createFrom(a, ChronoField.SECOND_OF_MINUTE, units[n-IDX_SECOND]);
            a = new StartingUnit(a, ChronoUnit.SECONDS, ChronoUnit.MINUTES, ChronoUnit.HOURS, ChronoUnit.DAYS, ChronoUnit.MONTHS, ChronoUnit.YEARS);
        }

        a = new Initializer(ChronoField.MILLI_OF_SECOND, a);
        return new Initializer(ChronoField.NANO_OF_SECOND, a);
    }

    private static TemporalAdjuster createFrom(TemporalAdjuster parent, TemporalField field, String unit) {
        return createFrom(parent, field, unit, LongUnaryOperator.identity(), Collections.emptyList());
    }

    private static TemporalAdjuster createFrom(TemporalAdjuster parent, TemporalField field, String unit, LongUnaryOperator op, List<String> constants) {
        Adjuster a = Cron.parse(unit, op, constants);
        return a instanceof Ignorable ? parent : new AdjustingUnit(field, parent, a);
    }

    public static final class Builder {
        private class Entry implements Comparable<Entry> {
            final TemporalField field;
            final Adjuster adjuster;
            final int index;

            Entry(TemporalField field, Adjuster adjuster, int index) {
                this.field = field;
                this.adjuster = adjuster;
                this.index = index;
            }

            @Override
            public int compareTo(Entry o) {
                if (field instanceof Comparable && o.field instanceof Comparable) {
                    @SuppressWarnings("unchecked")
                    int ret = ((Comparable<TemporalField>) field).compareTo(o.field);
                    return ret;
                }
                return o.index - index;
            }
        }

        private final List<Entry> entries = new ArrayList<>();

        public Builder and(TemporalField field, Adjuster adjuster) {
            Entry e = new Entry(field, adjuster, entries.size());
            entries.add(e);
            return this;
        }

        private Entry[] findSuccessors(Entry entry) {
            TemporalUnit rangeUnit = entry.field.getRangeUnit();
            return entries.stream().filter(e -> rangeUnit.equals(e.field.getBaseUnit())).toArray(Entry[]::new);
        }

        private Entry[] findPossibleStartNodes() {
            return entries.stream().filter(e -> entries.stream().noneMatch(e2 -> e.field.getBaseUnit().equals(e2.field.getRangeUnit()))).toArray(Entry[]::new);
        }

        /**
         * Returns the elements with the smallest units coming first
         */
        private List<Entry[]> sortedEntries() {
            List<Entry[]> sortedEntries = new ArrayList<>();
            Entry[] possibleStartNodes = findPossibleStartNodes();
            Arrays.sort(possibleStartNodes);
            int startLength = possibleStartNodes.length;
            Entry[][] startGroups = new Entry[startLength][];
            int g = 0;
            outer:
            for (Entry start : possibleStartNodes) {
                TemporalUnit base = start.field.getBaseUnit();
                for (int i=0; i<g; i++) {
                    Entry[] group = startGroups[i];
                    if (group[0].field.getBaseUnit().equals(base)) {
                        int l = group.length;
                        group = Arrays.copyOf(group, l + 1);
                        group[l] = start;
                        startGroups[i] = group;
                        continue outer;
                    }
                }
                startGroups[g++] = new Entry[] { start };
            }
            if (g < startLength) {
                startGroups = Arrays.copyOf(startGroups, g);
            }
            for (Entry[] startGroup : startGroups) {
                addEntries(sortedEntries, startGroup);
            }

            return sortedEntries;
        }

        private void addEntries(List<Entry[]> sortedEntries, Entry[] start) {
            sortedEntries.add(start);
            for (Entry s : start) {
                Entry[] successors = findSuccessors(s);
                if (successors.length == 0) return;
                Arrays.sort(successors);
                sortedEntries.add(successors);
                addEntries(sortedEntries, successors);
            }
        }

        public TemporalAdjuster withResetted(TemporalField... resettedFields) {
            List<Entry[]> sortedEntries = sortedEntries();
            TemporalAdjuster a = t -> t;
            for (int index=sortedEntries.size() - 1; index >= 0; index--) {
                Entry[] arr = sortedEntries.get(index);
                int l = arr.length;
                if (l == 1) {
                    a = new AdjustingUnit(arr[0].field, a, arr[0].adjuster);
                } else {
                    TemporalAdjuster[] adjusters = new TemporalAdjuster[l];
                    for (int i = 0; i < l; i++) {
                        adjusters[i] = new AdjustingUnit(arr[i].field, a, arr[i].adjuster);
                    }
                    a = new AlternativeAdjuster(adjusters);
                }
            }

            TemporalUnit[] units = sortedEntries.stream().map(e -> e[0].field.getBaseUnit()).distinct().toArray(TemporalUnit[]::new);
            a = new StartingUnit(a, units);
            for (TemporalField f : resettedFields) {
                entries.stream().filter(e -> f.equals(e.field)).findAny().ifPresent(e -> {
                    throw new IllegalArgumentException("Cannot reset " + f + " because it's already set in " + e);
                });
                a = new Initializer(f, a);
            }

            return a;
        }
    }

    private static abstract class StackedUnit implements TemporalAdjuster, Serializable {

        private final TemporalAdjuster next;

        StackedUnit(TemporalAdjuster next) {
            this.next = next;
        }

        @Override
        public final Temporal adjustInto(Temporal temporal) {
            Temporal t = adjustThis(temporal);
            return next.adjustInto(t);
        }

        abstract Temporal adjustThis(Temporal temporal);

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            StackedUnit that = (StackedUnit) o;
            return next.equals(that.next);
        }

        @Override
        public int hashCode() {
            return next.hashCode() * 31;
        }
    }

    private static abstract class CalendarUnit extends StackedUnit {

        private final TemporalField field;

        CalendarUnit(TemporalField field, TemporalAdjuster next) {
            super(next);
            this.field = field;
        }

        @Override
        Temporal adjustThis(Temporal t) {
            if (t.isSupported(field)) {
                return adjustInto(field, t);
            }
            return t;
        }

        abstract Temporal adjustInto(TemporalField field, Temporal temporal);

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            if (!super.equals(o)) return false;
            CalendarUnit that = (CalendarUnit) o;
            return field.equals(that.field);
        }

        @Override
        public int hashCode() {
            return super.hashCode() + field.hashCode();
        }
    }

    private static final class Initializer extends CalendarUnit {

        private static final long serialVersionUID = -2195127869900604874L;

        Initializer(TemporalField field, TemporalAdjuster next) {
            super(field, next);
        }

        @Override
        public Temporal adjustInto(TemporalField field, Temporal temporal) {
            return temporal.with(field, 0);
        }
    }

    private static final class StartingUnit extends StackedUnit {

        private static final long serialVersionUID = -1823173113615531961L;

        private final TemporalUnit[] units;

        StartingUnit(TemporalAdjuster next, TemporalUnit... units) {
            super(next);
            this.units = units;
        }

        @Override
        Temporal adjustThis(Temporal temporal) {
            for (TemporalUnit u : units) {
                if (temporal.isSupported(u)) {
                    return temporal.plus(1, u);
                }
            }
            throw new IllegalArgumentException(temporal + " does not support any of my units: " + Arrays.toString(units));
        }
    }

    private static final class AlternativeAdjuster implements TemporalAdjuster, Serializable {

        private static final long serialVersionUID = -4567999393131106473L;

        private final TemporalAdjuster[] adjusters;

        AlternativeAdjuster(TemporalAdjuster... adjusters) {
            assert adjusters.length >= 2;
            this.adjusters = adjusters;
        }

        @Override
        public Temporal adjustInto(Temporal temporal) {
            Temporal t1 = adjusters[0].adjustInto(temporal);
            for (int i=1; i < adjusters.length; i++) {
                Temporal t2 = adjusters[i].adjustInto(temporal);
                @SuppressWarnings("unchecked")
                Comparable<Temporal> comparable = (Comparable<Temporal>) t2; // Must be comparable according to Temporal's Java doc
                if (comparable.compareTo(t1) <= 0) {
                    t1 = t2;
                }
            }

            return t1;
        }
    }

    private static class AdjustingUnit extends CalendarUnit {

        private static final long serialVersionUID = -5486360270949135638L;

        private final Adjuster adjuster;

        AdjustingUnit(TemporalField field, TemporalAdjuster next, Adjuster adjuster) {
            super(field, next);
            this.adjuster = adjuster;
        }

        @Override
        public Temporal adjustInto(TemporalField field, Temporal temporal) {
            long actual = temporal.getLong(field);
            Temporal t = temporal;
            long check;
            if (adjuster instanceof Jumper) {
                t = addOne(field, t);
                check = adjuster.adjust(actual, t.range(field));
            } else {
                check = adjuster.adjust(actual, t.range(field));
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