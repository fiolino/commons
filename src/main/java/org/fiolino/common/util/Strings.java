package org.fiolino.common.util;

import java.util.concurrent.TimeUnit;
import java.util.function.IntUnaryOperator;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.concurrent.TimeUnit.*;

/**
 * Various utility methods to handle Strings.
 *
 * Created by kuli on 26.03.15.
 */
public final class Strings {

    private static final int DIFF_DIGIT_TO_LETTER = 'a' - '0' - 10;

    private Strings() {
        throw new AssertionError("Static class");
    }

    private static final int[] escapedToSpecial, specialToEscaped;
    private static final CharSet specialChars;

    static {
        escapedToSpecial = new int[256];
        specialToEscaped = new int[256];
        for (int i=0; i < 256; i++) {
            escapedToSpecial[i] = i;
        }
        CharSet cs = CharSet.empty();

        String translations = "\tt\bb\nn\rr\ff\'\'\"\"\\\\";
        for (int i=0; i < translations.length();) {
            char special = translations.charAt(i++);
            char esc = translations.charAt(i++);

            escapedToSpecial[(int) esc] = special;
            specialToEscaped[(int) special] = esc;
            cs = cs.add(special);
        }

        specialChars = cs;
    }

    /**
     * Appends a quoted version of the given input string to the string builder.
     * Quotation marks and other special characters are being escaped with a leading backslash.
     */
    public static StringBuilder appendQuotedString(StringBuilder sb, String string) {
        sb.append('"');
        int lastStop = 0;
        do {
            int nextStop = specialChars.nextIndexIn(string, lastStop);

            if (nextStop >= 0) {
                int ch = string.charAt(nextStop);
                sb.append(string, lastStop, nextStop);
                sb.append('\\').append((char) specialToEscaped[ch]);
                lastStop = nextStop + 1;
            } else {
                sb.append(string, lastStop, string.length());
                break;
            }
        } while (true);

        return sb.append('"');
    }

    /**
     * Returns a quoted String.
     * Quotation marks and other special characters are being escaped with a leading backslash.
     */
    public static String quote(String value) {
        return appendQuotedString(new StringBuilder(), value).toString();
    }

    /**
     * Gets the text from some quoted input string.
     * If the string starts with quotation marks, then the quoted content is returned - any following text is ignored.
     * If it's not quoted in the beginning, then the original input is returned.
     *
     * @param value The input string
     * @return The result
     */
    public static String unquote(String value) {
        if (value.isEmpty() || value.charAt(0) != '"') {
            return value;
        }
        return value.codePoints().skip(1).collect(() -> new CharFinderContainer('"', value.length()),
                CharFinderContainer::append, CharFinderContainer::merge).toString();
    }

    private static class CharFinderContainer {
        private CharFinder current;

        CharFinderContainer(int stopper, int expectedLength) {
            current = new DefaultCharFinder(stopper, expectedLength);
        }

        void append(int codePoint) {
            current = current.append(codePoint);
        }

        void merge(CharFinderContainer other) {
            current = current.merge(other.current);
        }

        @Override
        public String toString() {
            return current.toString();
        }
    }

    private abstract static class CharFinder {
        abstract CharFinder append(int codePoint);
        abstract CharFinder merge(CharFinder other);
    }

    private static class FinishedCharFinder extends CharFinder {
        private String result;

        FinishedCharFinder(String result) {
            this.result = result;
        }

        @Override
        CharFinder append(int codePoint) {
            // Ignore the rest
            return this;
        }

        @Override
        CharFinder merge(CharFinder other) {
            result += other.toString();
            return this;
        }

        @Override
        public String toString() {
            return result;
        }
    }

    private static class DefaultCharFinder extends CharFinder {
        private final int stopper;
        private final StringBuilder sb;
        private EscapedCharFinder escaped;

        DefaultCharFinder(int stopper, int expectedLength) {
            this.stopper = stopper;
            sb = new StringBuilder(expectedLength);
        }

        CharFinder appendDirect(int codePoint) {
            sb.appendCodePoint(codePoint);
            return this;
        }

        @Override
        CharFinder append(int codePoint) {
            if (codePoint == stopper) {
                return new FinishedCharFinder(sb.toString());
            }
            if (codePoint == '\\') {
                if (escaped == null) {
                    escaped = new EscapedCharFinder();
                }
                return escaped;
            }
            return appendDirect(codePoint);
        }

        CharFinder merge(CharFinder other) {
            sb.append(other.toString());
            return this;
        }

        @Override
        public String toString() {
            // Quote not closed
            return "\"" + sb.toString();
        }

        private class EscapedCharFinder extends CharFinder {
            @Override
            CharFinder append(int codePoint) {
                if (codePoint == 'u') {
                    // Unicode
                    return new UnicodeReader();
                }
                int ch = codePoint > 255 ? codePoint : escapedToSpecial[codePoint];
                return appendDirect(ch);
            }

            @Override
            CharFinder merge(CharFinder other) {
                throw new UnsupportedOperationException();
            }

            @Override
            public String toString() {
                // Quote not closed and trailing backslash
                return appendDirect('\\').toString();
            }
        }

        private class UnicodeReader extends CharFinder {
            private int digits = 4;
            private int result;
            private final int[] read = new int[4];

            @Override
            CharFinder append(int codePoint) {
                read[--digits] = codePoint;
                if (codePoint > 'f' || codePoint < '0') return cancel();
                int ch = Character.toLowerCase(codePoint);
                ch -= '0';
                if (ch > 9) ch -= DIFF_DIGIT_TO_LETTER;
                if (ch < 0 || ch > 15) return cancel();
                result = (result << 4) | ch;

                return digits == 0 ? appendDirect(result) : this;
            }

            private CharFinder cancel() {
                CharFinder result = appendDirect('u');
                for (int i=3; i >= digits; i--) {
                    result = result.append(read[i]);
                }
                return result;
            }

            @Override
            CharFinder merge(CharFinder other) {
                throw new UnsupportedOperationException();
            }

            @Override
            public String toString() {
                return cancel().toString();
            }
        }
    }

    /**
     * Removes the leading prefix of the given input.
     * Returns the unchanged input if it doesn't start with the prefix.
     * Lowers the beginning of the remainder otherwise.
     */
    public static String removeLeading(String input, String prefix) {
        if (!input.startsWith(prefix)) {
            return input;
        }
        int leadingLength = prefix.length();
        return lowerCaseFirst(input, leadingLength);
    }

    /**
     * Adds a prefix to the given input String, whose first letter will be in uppercase then.
     */
    public static String addLeading(String input, String prefix) {
        if (input.length() == 0) {
            return prefix;
        }
        return prefix + Character.toUpperCase(input.charAt(0)) + input.substring(1);
    }

    /**
     * Lowercases the beginning of the given input string.
     * If the input starts with multiple uppercase characters, then all of them are lowercased except the last one,
     * e.g. ABCName becomes abcName.
     */
    public static String lowerCaseFirst(String s) {
        return lowerCaseFirst(s, 0);
    }

    private static String lowerCaseFirst(String s, int start) {
        int fullLength = s.length();
        int remainingLength = fullLength - start;
        if (remainingLength <= 0) {
            return s;
        }
        StringBuilder sb = new StringBuilder(remainingLength);
        int i = start;
        while (i < fullLength) {
            char c = s.charAt(i);
            if ((i++ == start || i == fullLength || Character.isUpperCase(s.charAt(i))) && Character.isUpperCase(c)) {
                sb.append(Character.toLowerCase(c));
                continue;
            }
            sb.append(c);
            break;
        }
        sb.append(s, i, s.length());

        return sb.toString();
    }

    /**
     * Makes an uppercase representation of some string.
     * If the input contains camel case characters, then an underscore is added.
     * <p>
     * For instance, makeMeUpper becomes MAKE_ME_UPPER.
     */
    public static String toUpperCase(String input) {
        int n = input.length();
        if (n == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder(n + 5);
        boolean wasLower = false;
        boolean wasLetter = false;
        for (int i = 0; i < n; i++) {
            char c = input.charAt(i);
            boolean isUpper = Character.isUpperCase(c);
            if (wasLetter && isUpper &&
                    (wasLower || (i < n - 1 && Character.isLowerCase(input.charAt(i + 1))))) {
                sb.append('_');
            }
            wasLower = !isUpper;
            sb.append(Character.toUpperCase(c));
            wasLetter = Character.isLetterOrDigit(c);
        }

        return sb.toString();
    }

    private static final class NormalizedStringBuilder {
        private final IntUnaryOperator modifier;
        private final StringBuilder sb;
        private boolean wasUnderscore;

        NormalizedStringBuilder(IntUnaryOperator modifier, int l) {
            this.modifier = modifier;
            sb = new StringBuilder(l);
        }

        void append(int codePoint) {
            if (codePoint == '_') {
                if (wasUnderscore) sb.append('_');
                else wasUnderscore = true;
            } else if (wasUnderscore) {
                sb.appendCodePoint(modifier.applyAsInt(codePoint));
                wasUnderscore = false;
            } else {
                sb.appendCodePoint(modifier.applyAsInt(Character.toLowerCase(codePoint)));
            }
        }

        // This will never be called, and it's ignoring wasUnderscore
        void append(NormalizedStringBuilder nsb) {
            sb.append(nsb.sb);
        }

        @Override
        public String toString() {
            if (wasUnderscore) {
                sb.append('_');
                wasUnderscore = false;
            }
            return sb.toString();
        }
    }

    /**
     * Normalizes an uppercased name by lowercasing everything, except letters that
     * follow an underscore, which become camel case then.
     * <p>
     * For instance, MAKE_ME_LOWER becomes makeMeLower.
     */
    public static String normalizeName(String name) {
        return normalizeName(name, ch -> ch);
    }

    /**
     * Normalizes an uppercased name by lowercasing everything, except letters that
     * follow an underscore, which become camel case then.
     * <p>
     * A modifier may be given, which can transform individual characters after their case has changed.
     * <p>
     * For instance, MAKE_ME_LOWER becomes makeMeLower.
     */
    public static String normalizeName(String name, IntUnaryOperator modifier) {
        int l = name.length();
        if (l == 0) return name;
        return name.codePoints().collect(() -> new NormalizedStringBuilder(modifier, l),
                NormalizedStringBuilder::append, NormalizedStringBuilder::append).toString();
    }

    /**
     * Gets a String representation of a time delay in nanoseconds.
     * <p>
     * Examples are MM:SS or HH:MM:SS:mmm.nnnnnn
     * <p>
     * The hours are added only if the duration is at least one hour.
     * <p>
     * The finest parameter specifies the fines displayed time unit.
     */
    public static String printDuration(long durationInNanos, TimeUnit finest) {
        return appendDuration(new StringBuilder(), durationInNanos, finest).toString();
    }

    /**
     * Appends a String representation of a time delay in nanoseconds to a StringBuilder.
     * <p>
     * Examples are MM:SS or HH:MM:SS:mmm.nnnnnn
     * <p>
     * The hours are added only if the duration is at least one hour.
     * <p>
     * The finest parameter specifies the fines displayed time unit.
     */
    public static StringBuilder appendDuration(StringBuilder sb, long durationInNanos, TimeUnit finest) {
        if (durationInNanos < 0) {
            sb.append('-');
            durationInNanos *= -1;
        }
        long nanos = durationInNanos % 1000000L;
        long duration = durationInNanos / 1000000L;
        long millis = duration % 1000L;
        duration /= 1000L;
        long seconds = duration % 60L;
        duration /= 60L;
        long minutes = duration % 60L;
        duration /= 60L;
        long hours = duration % 24L;
        duration /= 24L;
        long days = duration;

        if (days > 0) {
            sb.append(days).append(':');
            appendNumber(sb, hours, 2).append(':');
        } else if (hours > 0) {
            appendNumber(sb, hours, 2).append(':');
        } else if (minutes > 0 || finest.compareTo(MINUTES) >= 0) {
            appendNumber(sb, minutes, 2);
            if (finest.compareTo(MINUTES) < 0) {
                sb.append(':');
            }
        }
        if (finest.compareTo(MINUTES) < 0) {
            appendNumber(sb, seconds, 2);
            if (finest.compareTo(SECONDS) < 0) {
                appendNumber(sb.append(':'), millis, 3);
                if (nanos > 0 && finest == NANOSECONDS) {
                    appendNumber(sb.append('.'), nanos, 6);
                }
            }
        }

        return sb;
    }

    private static final TimeUnit[] TIME_UNITS = {
            DAYS, HOURS, MINUTES, SECONDS, MILLISECONDS, MICROSECONDS, NANOSECONDS
    };

    private static final String[] TIME_UNIT_REPRESENTATIONS = {
            "day", "hour", "minute", "second", "millisecond", "microsecond", "nanosecond"
    };

    /**
     * Adds a long readable representation of the given duration in nanoseconds.
     * Examples are "1 hour 13 minutes 56 seconds" or so.
     * Adds only time units whose values would not be zero.
     */
    public static StringBuilder appendLongDuration(StringBuilder sb, long durationInNanos) {
        return appendLongDuration(sb, durationInNanos, NANOSECONDS);
    }

    /**
     * Adds a long readable representation of the given duration.
     * Examples are "1 hour 13 minutes 56 seconds" or so.
     * Adds only time units whose values would not be zero.
     */
    public static StringBuilder appendLongDuration(StringBuilder sb, long duration, TimeUnit unit) {
        if (duration == 0) {
            sb.append('0');
            return sb;
        }

        boolean isEmpty = true;
        long remaining = duration;
        for (int i=0; i < TIME_UNITS.length && remaining != 0; i++) {
            TimeUnit u = TIME_UNITS[i];
            long thisVal = u.convert(remaining, unit);
            if (thisVal == 0) continue;
            remaining -= unit.convert(thisVal, u);
            if (isEmpty) isEmpty = false;
            else sb.append(remaining == 0 ? " and " : ", ");
            sb.append(thisVal).append(' ').append(TIME_UNIT_REPRESENTATIONS[i]);
            if (Math.abs(thisVal) > 1) sb.append('s');
        }

        return sb;
    }

    /**
     * Prints a long readable representation of the given duration in nanoseconds.
     * Examples are "1 hour 13 minutes 56 seconds" or so.
     * Adds only time units whose values would not be zero.
     */
    public static String printLongDuration(long durationInNanos) {
        return appendLongDuration(new StringBuilder(), durationInNanos).toString();
    }

    /**
     * Prints a long readable representation of the given duration.
     * Examples are "1 hour 13 minutes 56 seconds" or so.
     * Adds only time units whose values would not be zero.
     */
    public static String printLongDuration(long duration, TimeUnit unit) {
        return appendLongDuration(new StringBuilder(), duration, unit).toString();
    }

    private static final Pattern NUMBERS_AND_STRINGS = Pattern.compile("(-?\\d+)\\s*(\\w+)");

    /**
     * Parses a long representation of a duration as in the result of printLongDuration().
     * The format is:
     * number + time unit, number + time unit...
     * The time unit must be the unique start of some TimeUnit constant.
     * Multiple entries can be added after each other.
     */
    public static long parseLongDuration(String desc) {
        return parseLongDuration(desc, NANOSECONDS);
    }

    /**
     * Parses a long representation of a duration as in the result of printLongDuration().
     * The format is:
     * number + time unit, number + time unit...
     * The time unit must be the unique start of some TimeUnit constant.
     * Multiple entries can be added after each other.
     *
     * A target unit specifies in which unit the result is returned.
     */
    public static long parseLongDuration(String desc, TimeUnit targetUnit) {
        Matcher m = NUMBERS_AND_STRINGS.matcher(desc);
        if (!m.find()) {
            throw new IllegalArgumentException("No duration description: " + desc);
        }
        long result = 0;
        do {
            long longVal = Long.parseLong(m.group(1));
            String unitVal = m.group(2).toUpperCase();

            TimeUnit found = null;
            for (TimeUnit u : TimeUnit.values()) {
                if (u.name().startsWith(unitVal)) {
                    if (found != null) {
                        throw new IllegalArgumentException(desc + " has ambiguous time unit " + unitVal);
                    }
                    found = u;
                }
            }

            if (found == null) {
                switch (unitVal) {
                    case "ms":
                    case "msec":
                        found = MILLISECONDS;
                        break;
                    case "µs":
                    case "µsec":
                        found = MICROSECONDS;
                        break;
                    default:
                        throw new IllegalArgumentException(desc + " has undefined time unit " + unitVal);
                }
            }

            long asTarget = targetUnit.convert(longVal, found);
            result += asTarget;
        } while (m.find());

        return result;
    }

    private static final long[] DIGI; // 0, 0, 10, 100, 1000, ...
    // The first two values are 0 by intention

    static {
        int n = String.valueOf(Long.MAX_VALUE).length()+1;
        DIGI = new long[n];
        long exp = 1L;

        for (int i = 2; i < n; i++) {
            exp *= 10L;
            DIGI[i] = exp;
        }
    }

    /**
     * Prints a fixed number with a given length of digits into a StringBuilder, filled with leading zeros.
     */
    public static StringBuilder appendNumber(StringBuilder sb, long number, int minimumDigits) {
        if (minimumDigits < 0) {
            throw new IllegalArgumentException("" + minimumDigits);
        }
        int digits = minimumDigits;
        if (number < 0) {
            // Minus sign does not count into digits param
            sb.append('-');
            number *= -1;
        }
        while (digits >= DIGI.length) {
            // If more digits are requested than the size of the maximum possible long value
            digits--;
            sb.append('0');
        }
        while (number < DIGI[digits--]) { // Will stop at last digit latest
            // Attach zeros as long as first real digit isn't reached
            sb.append('0');
        }
        return sb.append(number);
    }

    private static final Pattern VARIABLE = Pattern.compile("((^|[^\\\\])\\$(\\{[^}]*}|\\w+(\\.\\w+)*))|(\\\\\\$)");

    /**
     * Returns a String where all variables are replaced by their values.
     * Variables appear in the String with a leading $ sign, followed directly by some token if it
     * only contains letters, digits, underscores and surrounded single dots, or any token surrounded by curly brackets.
     * <p>
     * If the first repository does not contain the token, then next one is asked. If no repository contains
     * this, an {@link IllegalArgumentException} is thrown.
     * <p>
     * To avoid that, you can add an empty repository returning any default value as the last one.
     */
    @SafeVarargs
    public static String replace(String input, UnaryOperator<String>... repositories) {
        Matcher m = VARIABLE.matcher(input);
        if (!m.find()) {
            return input;
        }
        StringBuilder sb = new StringBuilder(input.length()); // length is just a guess
        do {
            String keyword = m.group(3);
            if (keyword == null) {
                // Then it's a quoted dollar sign - \$
                m.appendReplacement(sb, "\\$");
                continue;
            }
            if (keyword.charAt(0) == '{') {
                keyword = keyword.substring(1, keyword.length() - 1);
            }
            String value = null;
            for (UnaryOperator<String> op : repositories) {
                value = op.apply(keyword);
                if (value != null) break;
            }
            if (value == null) {
                throw new IllegalArgumentException("No key " + keyword + " available in pattern " + input);
            }
            m.appendReplacement(sb, m.group(2) + value);
        } while (m.find());

        m.appendTail(sb);
        return sb.toString();
    }
}
