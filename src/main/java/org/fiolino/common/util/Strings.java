package org.fiolino.common.util;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.function.IntPredicate;
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
        Extract x = extractUntil(value, 0, c -> false);
        if (x.wasQuoted()) {
            return x.extraction;
        }
        // Was not quoted - there must be some unquoted text somewhere
        return value;
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

    /**
     * Normalizes an uppercased name by lowercasing everything, except letters that
     * follow an underscore, which become camel case then.
     * On top, all $ signs are replaced by dots.
     * <p>
     * For instance, MAKE_ME_LOWER becomes makeMeLower.
     */
    public static String normalizeName(String name) {
        int l = name.length();
        for (int i = 0; i < l; i++) {
            if (Character.isLowerCase(name.charAt(i)))
                return name.replace('$', '.'); // Then the name is already lowercased
        }
        StringBuilder sb = new StringBuilder(l).append(Character.toLowerCase(name.charAt(0)));
        for (int i = 1; i < l; i++) {
            char ch = name.charAt(i);
            if (ch == '_' && ++i < l) {
                sb.append(name.charAt(i));
            } else if (ch == '$') {
                sb.append('.');
            } else {
                sb.append(Character.toLowerCase(ch));
            }
        }
        return sb.toString();
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
    // The first two numbers are 0 by intention

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
            // If more digits are requested than the maximum possible long value has
            digits--;
            sb.append('0');
        }
        while (number < DIGI[digits--]) { // Will stop at last digit latest
            // Attach zeros as long as first real digit isn't reached
            sb.append('0');
        }
        return sb.append(number);
    }

    /**
     * Contains one splitted item of the splitBy() operation.
     */
    public static final class SplitItem {
        /**
         * The index of the splitted item.
         */
        public final int index;

        /**
         * The splitted text part.
         */
        public final String text;

        /**
         * Which character separated this part from the previous one, represented as a code point
         * Will be Character.UNASSIGNED when it's the first item.
         */
        public final int separator;

        /**
         * The start index in the original text, not counting any separator.
         */
        public final int start;

        /**
         * The end index in the original text, not counting any separator.
         */
        public final int end;

        SplitItem(int index, String text, int separator, int start, int end) {
            this.index = index;
            this.text = text;
            this.separator = separator;
            this.start = start;
            this.end = end;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder(text.length() + 6).append('#').append(index).append(": ");
            if (index > 0) {
                sb.appendCodePoint(separator).append('!');
            }
            return sb.append(text).toString();
        }

        @Override
        public boolean equals(Object obj) {
            return obj == this ||
                    obj instanceof SplitItem && text.equals(((SplitItem) obj).text);
        }

        @Override
        public int hashCode() {
            return text.hashCode() * 177;
        }
    }

    /**
     * Splits a given String into some parts, separated by a tester.
     * Similar to String::split, but does not use regular expressions and is therefore less resource hungry.
     *
     * @param input Some input text
     * @param tester Defines the separators
     * @return An array of parts of the string
     */
    public static SplitItem[] splitBy(String input, IntPredicate tester) {
        SplitItem[] splits = new SplitItem[3]; // We need to start with some value. 3 because of why not.
        int index = 0;
        int last = 0;
        int nextSeparator = Character.UNASSIGNED;
        int n = input.length();
        while (last < n) {
            int lastSeparator = nextSeparator;
            int next = last;
            int adder = 1;
            while (next < n) {
                int ch = Character.codePointAt(input, next);
                adder = Character.charCount(ch);
                if (tester.test(ch)) {
                    nextSeparator = ch;
                    break;
                }
                next += adder;
            }
            String part = input.substring(last, next);
            SplitItem item = new SplitItem(index, part, lastSeparator, last, next);
            last = next + adder;
            if (index >= splits.length) {
                splits = Arrays.copyOf(splits, index + 3);
            }
            splits[index++] = item;
        }

        return index == splits.length ? splits : Arrays.copyOf(splits, index);
    }

    /**
     * Tries to find an overlap between the given strings, splitted by a given tester.
     *
     * Example: MY_TEST and THIS_TEST_IS_NICE returns TEST.
     */
    public static String combinationOf(IntPredicate tester, String... values) {
        int n = values.length;
        switch (n) {
            case 0:
                throw new IllegalArgumentException("No values given.");
            case 1:
                return values[0];
        }

        String overlap = findOverlap(tester, values[0], values[1]);
        for (int i = 2; i < n; i++) {
            overlap = findOverlap(tester, overlap, values[i]);
        }
        return overlap;
    }

    /**
     * Finds overlaps between two strings.
     */
    private static String findOverlap(IntPredicate tester, String s1, String s2) {
        SplitItem[] split1 = splitBy(s1, tester);
        SplitItem[] split2 = splitBy(s2, tester);
        for (int i = 0; i < split1.length; i++) {
            SplitItem part1 = split1[i];
            for (int j = 0; j < split2.length; j++) {
                SplitItem part2 = split2[j];
                if (part1.equals(part2)) {
                    StringBuilder sb = new StringBuilder(part1.text);
                    int ix = i + 1, jx = j + 1;
                    while (ix < split1.length && jx < split2.length) {
                        part1 = split1[ix++];
                        part2 = split2[jx++];
                        if (part1.equals(part2)) {
                            sb.appendCodePoint(part1.separator).append(part1.text);
                        } else {
                            break;
                        }
                    }
                    return sb.toString();
                }
            }
        }

        throw new IllegalArgumentException("No overlap between " + s1 + " and " + s2);
    }

    private static final Pattern VARIABLE = Pattern.compile("\\$(\\{[^}]+}|\\w+)");

    /**
     * Returns a String where all variables are replaced by their values.
     * Variables appear in the String with a leading $ sign, followed directly by the name if it
     * only contains letters, digits or underscores, or the name surrounded by curly brackets.
     * <p>
     * If the first repository does not contain the key, then next one is asked. If no repository contains
     * such a key, an {@link IllegalArgumentException} is thrown.
     * <p>
     * To avoid that, you can add an empty repository returning any default value as the last one.
     */
    @SafeVarargs
    public static String insertValues(String input, UnaryOperator<String>... repositories) {
        Matcher m = VARIABLE.matcher(input);
        if (!m.find()) {
            return input;
        }
        StringBuffer sb = new StringBuffer();
        do {
            String keyword = m.group(1);
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
            m.appendReplacement(sb, value);
        } while (m.find());

        m.appendTail(sb);
        return sb.toString();
    }

    /**
     * Result of method extractUtil().
     *
     * Contains the extracted text, the position of the following character, and the following character itself.
     */
    public static final class Extract {
        public enum QuotationStatus {
            /**
             * The text was not quoted at all.
             */
            UNQUOTED {
                @Override
                String toString(Extract x) {
                    return x.wasEOL() ? x.extraction + " EOL" : x.extraction + " --> '" + x.stopSign + "' at " + x.end;
                }
            },
            /**
             * The text was not quoted, but the last character in line was a backslash to indicate an escaped sequence,
             * but no more lines follow.
             */
            UNQUOTED_OPEN {
                @Override
                String toString(Extract x) {
                    return x.extraction + " \\";
                }
            },
            /**
             * The text is quoted at the beginning, and a closing quotation was found.
             */
            QUOTED {
                @Override
                String toString(Extract x) {
                    return x.wasEOL() ? quote(x.extraction) + " EOL" : quote(x.extraction) + " --> '" + x.stopSign + "' at " + x.end;
                }
            },
            /**
             * The text is quoted, but no closing quotation is there.
             */
            QUOTED_OPEN {
                @Override
                String toString(Extract x) {
                    return "\"" + x.extraction + " ...";
                }
            };

            abstract String toString(Extract x);
        }

        /**
         * The extracted text. Will never be null.
         */
        public final String extraction;
        /**
         * The position of the next following character, or -1 if the end of line was reached.
         */
        public final int end;
        /**
         * The character at the next position, or UNASSIGNED if the end of line was reached.
         */
        public final int stopSign;
        /**
         * Whether the extracted text was in quotated form.
         */
        public final QuotationStatus quotationStatus;

        Extract(String extraction, QuotationStatus quotationStatus) {
            this(extraction, -1, (char) Character.UNASSIGNED, quotationStatus);
        }

        Extract(String extraction, int end, int stopSign, QuotationStatus quotationStatus) {
            this.extraction = extraction;
            this.end = end;
            this.stopSign = stopSign;
            this.quotationStatus = quotationStatus;
        }

        /**
         * Returns true if this was the remainder of the given input.
         */
        public boolean wasEOL() {
            return stopSign == Character.UNASSIGNED;
        }

        /**
         * Returns true if the result was quoted, either open or closed.
         */
        public boolean wasQuoted() {
                return quotationStatus != QuotationStatus.UNQUOTED;
        }

        @Override
        public boolean equals(Object obj) {
            return obj == this ||
                    obj instanceof Extract && ((Extract) obj).quotationStatus == quotationStatus
                    && ((Extract) obj).end == end
                    && ((Extract) obj).stopSign == stopSign
                    && ((Extract) obj).extraction.equals(extraction);
        }

        @Override
        public int hashCode() {
            return ((quotationStatus.hashCode() * 31
                    + end) * 31
                    + Integer.hashCode(stopSign)) * 31
                    + extraction.hashCode();
        }

        @Override
        public String toString() {
            return quotationStatus.toString(this);
        }
    }

    /**
     * Extracts some text from an input string.
     *
     * It reads the text until one of these requirements is met:<p>
     * <ol>
     *     <li>One of the given characters from the stopper is reached</li>
     *     <li>The end of the line was reached</li>
     *     <li>The text was quoted (first character is a quote) and the quotation was closed; the stoppers are completely ignored then</li>
     *     <li>The text was not quoted, but a quotation was found</li>
     * </ol>
     *
     * The result is trimmed, except when it was quoted.
     *
     * @param input The input text
     * @param start From which position to start
     * @param stopper Set of characters that should act like delimiters
     * @return The extracted status
     */
    public static Extract extractUntil(String input, int start, IntPredicate stopper) {
        return extractUntil(input, start, stopper, () -> null);
    }

    /**
     * Extracts some text from an input string.
     *
     * It reads the text until one of these requirements is met:<p>
     * <ol>
     *     <li>One of the given characters from the stopper is reached</li>
     *     <li>The end of the line was reached</li>
     *     <li>The text was quoted (first character is a quote) and the quotation was closed; the stoppers are completely ignored then</li>
     *     <li>The text was not quoted, but a quotation was found</li>
     * </ol>
     *
     * The result is trimmed, except when it was quoted.
     *
     * @param input The input text
     * @param start From which position to start
     * @param stopper Set of characters that should act like delimiters
     * @param moreLines Used to retrieve more lines if the line ended openly
     * @return The extracted status
     */
    public static <E extends Throwable> Extract extractUntil(String input, int start, IntPredicate stopper,
                                                             SupplierWithException<String, E> moreLines) throws E {
        int i = nextIndexFrom(input, start-1);
        if (i == -1) return new Extract("", Extract.QuotationStatus.UNQUOTED);

        StringBuilder sb = new StringBuilder(input.length() - start);
        int ch = input.codePointAt(i);
        boolean escaped = false;
        if (ch == '"') {
            // quoted
            do {
                int l = input.length();
                while ((i += Character.charCount(ch)) < l) {
                    ch = input.codePointAt(i);
                    if (escaped) {
                        escaped = false;
                        i = appendEscapedSequence(input, sb, ch, i, l);
                    } else if (ch == '"') {
                        int end = nextIndexFrom(input, i);
                        int stopSign = end == -1 ? Character.UNASSIGNED : input.codePointAt(end);
                        return new Extract(sb.toString(), end, stopSign, Extract.QuotationStatus.QUOTED);
                    } else if (ch == '\\') {
                        escaped = true;
                    } else {
                        sb.appendCodePoint(ch);
                    }
                }

                // EOL but quote still open
                if (!escaped) {
                    sb.append('\n');
                }
                escaped = false;
                input = moreLines.get();
                i = -1;
            } while (input != null);

            // No more lines
            return new Extract(sb.toString(), Extract.QuotationStatus.QUOTED_OPEN);
        }

        // Not quoted
        do {
            int l = input.length();
            while (i < l) {
                ch = input.codePointAt(i);
                if (escaped) {
                    escaped = false;
                    sb.appendCodePoint(ch);
                } else if (stopper.test(ch) || ch == '"') {
                    return new Extract(sb.toString().trim(), i, ch, Extract.QuotationStatus.UNQUOTED);
                } else if (ch == '\\') {
                    escaped = true;
                } else {
                    sb.appendCodePoint(ch);
                }
                i += Character.charCount(ch);
            }
            // EOL
            if (!escaped) {
                return new Extract(sb.toString().trim(), Extract.QuotationStatus.UNQUOTED);
            }
            escaped = false;
            input = moreLines.get();
            i = 0;
        } while (input != null);

        return new Extract(sb.toString().trim(), Extract.QuotationStatus.UNQUOTED_OPEN);
    }

    private static int appendEscapedSequence(String input, StringBuilder sb, int ch, int start, int end) {
        int escapedChar;
        if (ch == 'u' && next4CharactersAreHexadecimal(input, start + 1, end)) {
            try {
                escapedChar = Integer.parseInt(input, start + 1, start + 5, 16);
                try {
                    sb.appendCodePoint(escapedChar);
                    return start + 4;
                } catch (IllegalArgumentException ex2) {
                    // It wasn't a valid unicode character
                    sb.append('u');
                    return start;
                }
            } catch (NumberFormatException ex) {
                // Then keep ch as 'u' and don't increment i
            }
        }
        sb.appendCodePoint(ch < 256 ? escapedToSpecial[ch] : ch);
        return start;
    }

    private static int nextIndexFrom(String input, int start) {
        int l = input.length();
        int i = start;
        do {
            if (l <= ++i) {
                return -1;
            }
        } while (Character.isWhitespace(input.charAt(i)));

        return i;
    }

    private static final CharSet HEXADECIMAL = CharSet.of("0123456789abcdefABCDEF");

    private static boolean next4CharactersAreHexadecimal(String input, int start, int end) {
        if (start > end - 4) return false;
        for (int i=0; i < 4; i++) {
            if (!HEXADECIMAL.test(input.codePointAt(start + i))) return false;
        }

        return true;
    }
}
