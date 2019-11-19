package org.fiolino.common.util;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Created by kuli on 25.11.15.
 */
class StringsTest {

    @Test
    void testQuote() {
        String quoted = Strings.quote("Hello World!");
        assertEquals("\"Hello World!\"", quoted);

        quoted = Strings.quote("Here \"this\" is already quoted.");
        assertEquals("\"Here \\\"this\\\" is already quoted.\"", quoted);

        quoted = Strings.quote("\"Here the whole string is already quoted.\"");
        assertEquals("\"\\\"Here the whole string is already quoted.\\\"\"", quoted);

        quoted = Strings.quote("\"Here the whole string is already quoted.\"");
        assertEquals("\"\\\"Here the whole string is already quoted.\\\"\"", quoted);

        quoted = Strings.quote("Now a string with \"parenthesises\", \\slashes\\, and \\\"quoted parenthesises\\\".");
        assertEquals("\"Now a string with \\\"parenthesises\\\", \\\\slashes\\\\, and \\\\\\\"quoted parenthesises\\\\\\\".\"", quoted);

        quoted = Strings.quote("Text with \n, \t, and \r and \b; also \f plus \\ and '. ");
        assertEquals("\"Text with \\n, \\t, and \\r and \\b; also \\f plus \\\\ and \\'. \"", quoted);
    }

    @Test
    void testUnquote() {
        String unquoted = Strings.unquote("\"Hello World!\"");
        assertEquals("Hello World!", unquoted);

        unquoted = Strings.unquote("\"Here \\\"this\\\" is already quoted.\"");
        assertEquals("Here \"this\" is already quoted.", unquoted);

        unquoted = Strings.unquote("\"\\\"Here the whole string is already quoted.\\\"\"");
        assertEquals("\"Here the whole string is already quoted.\"", unquoted);

        unquoted = Strings.unquote("\"\\\"Here the whole string is already quoted.\\\"\"");
        assertEquals("\"Here the whole string is already quoted.\"", unquoted);

        unquoted = Strings.unquote("\"Now a string with \\\"parenthesises\\\", \\\\slashes\\\\, and \\\\\\\"quoted parenthesises\\\\\\\".\"");
        assertEquals("Now a string with \"parenthesises\", \\slashes\\, and \\\"quoted parenthesises\\\".", unquoted);

        unquoted = Strings.unquote("Some unquoted \"and quoted\".");
        assertEquals("Some unquoted \"and quoted\".", unquoted);

        unquoted = Strings.unquote("\"Some quoted \" and unquoted.");
        assertEquals("Some quoted ", unquoted);

        unquoted = Strings.unquote("\"Some \\\"double quoted\\\"\"");
        assertEquals("Some \"double quoted\"", unquoted);

        unquoted = Strings.unquote("\"Text with \\n, \\t, and \\r and \\b; also \\f plus \\\\ and \\'. \"");
        assertEquals("Text with \n, \t, and \r and \b; also \f plus \\ and '. ", unquoted);

        unquoted = Strings.unquote("\"Text with strange \\x or \\0.\"");
        assertEquals("Text with strange x or 0.", unquoted);

        unquoted = Strings.unquote("Totally unquoted.");
        assertEquals("Totally unquoted.", unquoted);

        unquoted = Strings.unquote("\"String with \\u0041 and \\uaffe\"");
        assertEquals("String with A and \uaffe", unquoted);

        unquoted = Strings.unquote("\"String with \\unicode\"");
        assertEquals("String with unicode", unquoted);

        unquoted = Strings.unquote("\"String with \\u123\"");
        assertEquals("String with u123", unquoted);

        unquoted = Strings.unquote("\"String with \\u123 oh no!\"");
        assertEquals("String with u123 oh no!", unquoted);

        unquoted = Strings.unquote("\"String with open quote");
        assertEquals("\"String with open quote", unquoted);

        unquoted = Strings.unquote("\"String with open escape character \\");
        assertEquals("\"String with open escape character \\", unquoted);
    }

    @Test
    void testRemoveLeading() {
        String removed = Strings.removeLeading("setName", "set");
        assertEquals("name", removed);

        removed = Strings.removeLeading("setnAme", "set");
        assertEquals("nAme", removed);

        removed = Strings.removeLeading("set", "set");
        assertEquals("set", removed);

        removed = Strings.removeLeading("setName", "get");
        assertEquals("setName", removed);

        removed = Strings.removeLeading("setACRONYMAndName", "set");
        assertEquals("acronymAndName", removed);

    }

    @Test
    void testLowercaseFirst() {
        String lower = Strings.lowerCaseFirst("startsWithLowercase.");
        assertEquals("startsWithLowercase.", lower);

        String normal = Strings.lowerCaseFirst("Normal");
        assertEquals("normal", normal);

        String multiUpper = Strings.lowerCaseFirst("ABCNameAndMore");
        assertEquals("abcNameAndMore", multiUpper);

        String allUpper = Strings.lowerCaseFirst("UPPER");
        assertEquals("upper", allUpper);

        String almostAllUpper = Strings.lowerCaseFirst("UPPEr");
        assertEquals("uppEr", almostAllUpper);
    }

    @Test
    void testUppercase() {
        String upper = Strings.toUpperCase("Hello World!");
        assertEquals("HELLO WORLD!", upper);

        upper = Strings.toUpperCase("HelloWorld!");
        assertEquals("HELLO_WORLD!", upper);

        upper = Strings.toUpperCase("HelloCRUELWorld!");
        assertEquals("HELLO_CRUEL_WORLD!", upper);

        upper = Strings.toUpperCase("HelloWORLDMyLOVE");
        assertEquals("HELLO_WORLD_MY_LOVE", upper);
    }

    @Test
    void testNormalize() {
        String normal = Strings.normalizeName("Hello World!");
        assertEquals("hello world!", normal);

        normal = Strings.normalizeName("HELLO_WORLD!");
        assertEquals("helloWorld!", normal);

        normal = Strings.normalizeName("HELLO_WORLD$$HI_JOHN!", ch -> ch == '$' ? '.' : ch);
        assertEquals("helloWorld..hiJohn!", normal);

        normal = Strings.normalizeName("HELLO$_WORLD!", ch -> ch == '$' ? '.' : ch);
        assertEquals("hello.World!", normal);
    }

    @Test
    void appendNumberTest() {
        String num = Strings.appendNumber(new StringBuilder(), 1313, 5).toString();
        assertEquals("01313", num);

        num = Strings.appendNumber(new StringBuilder(), -1313, 5).toString();
        assertEquals("-01313", num);

        num = Strings.appendNumber(new StringBuilder(), 1313, 2).toString();
        assertEquals("1313", num);

        num = Strings.appendNumber(new StringBuilder(), 0, 20).toString();
        assertEquals("00000000000000000000", num);

        num = Strings.appendNumber(new StringBuilder(), 0, 0).toString();
        assertEquals("0", num);
    }

    @Test
    void testSplit() {
        Strings.SplitItem[] splits = Strings.splitBy("Me and_my.monkey", CharSet.of(" ._-!"));
        assertEquals(4, splits.length);
        Strings.SplitItem item = splits[0];
        assertEquals(0, item.index);
        assertEquals("Me", item.text);
        assertEquals(Character.UNASSIGNED, item.separator);
        assertEquals(0, item.start);
        assertEquals(2, item.end);

        item = splits[1];
        assertEquals(1, item.index);
        assertEquals("and", item.text);
        assertEquals(' ', item.separator);
        assertEquals(3, item.start);
        assertEquals(6, item.end);

        item = splits[2];
        assertEquals(2, item.index);
        assertEquals("my", item.text);
        assertEquals('_', item.separator);
        assertEquals(7, item.start);
        assertEquals(9, item.end);

        item = splits[3];
        assertEquals(3, item.index);
        assertEquals("monkey", item.text);
        assertEquals('.', item.separator);
        assertEquals(10, item.start);
        assertEquals(16, item.end);
    }

    @Test
    void testOverlap() {
        String overlap = Strings.combinationOf(x -> x == '_', "one_two_three_four", "two_three_foooour");
        assertEquals("two_three", overlap);

        overlap = Strings.combinationOf(CharSet.of("_!."), "one_two!three_four", "two.three_foooour", "bla!three!three");
        assertEquals("three", overlap);
    }

    @Test
    void testFailedOverlap() {
        assertThrows(IllegalArgumentException.class,
                () -> Strings.combinationOf(CharSet.of('_'), "one_two_three_four", "two_three_foooour", "two_two", "three_three"));
    }

    @Test
    void testReplace() {
        Map<String, String> map = new HashMap<>();
        map.put("Normal_value", "Normal-Replacement");
        map.put("123", "987");
        map.put("   -- !!!! --   ", "weird");

        String replaceAll = Strings.replace("This is a $Normal_value, this is a $123 number, this is \\$something, and this is ${   -- !!!! --   } of course.", map::get);
        assertEquals("This is a Normal-Replacement, this is a 987 number, this is $something, and this is weird of course.", replaceAll);

        String withDollars = Strings.replace(" $$$ ", map::get);
        assertEquals(" $$$ ", withDollars);

        String only = Strings.replace("$123", map::get);
        assertEquals("987", only);

        String multi = Strings.replace("1. ${   -- !!!! --   }, and 2. ${   -- !!!! --   }", map::get);
        assertEquals("1. weird, and 2. weird", multi);
    }

    @Test
    void testFailedReplace() {
        assertThrows(IllegalArgumentException.class,
                () -> Strings.replace("$bbb", x -> null));
    }

    @Test
    void testReplaceBeginning() {
        String replacement = Strings.replace("$a.b**$o...${}", x -> x.equals("a.b") ? "1" : null, x -> x.equals("o") ? "2" : null, x -> x.equals("") ? "3" : null);
        assertEquals("1**2...3", replacement);
    }

    @Test
    void testPrintLongDuration0() {
        String representation = Strings.printLongDuration(0);
        assertEquals("0", representation);
    }

    @Test
    void testPrintLongDurationNanos() {
        String representation = Strings.printLongDuration(123);
        assertEquals("123 nanoseconds", representation);
    }

    @Test
    void testPrintLongDurationDays() {
        long duration = TimeUnit.DAYS.toNanos(5);
        duration += TimeUnit.HOURS.toNanos(23);
        duration += TimeUnit.MINUTES.toNanos(1);
        duration += TimeUnit.MICROSECONDS.toNanos(99);
        // MILLIS and NANOS are 0 by intention
        String representation = Strings.printLongDuration(duration);
        assertEquals("5 days, 23 hours, 1 minute and 99 microseconds", representation);
    }

    @Test
    void testPrintLongNegativeDuration() {
        long duration = TimeUnit.DAYS.toNanos(5);
        duration += TimeUnit.HOURS.toNanos(23);
        duration += TimeUnit.MINUTES.toNanos(1);
        duration += TimeUnit.MICROSECONDS.toNanos(99);
        // MILLIS and NANOS are 0 by intention
        String representation = Strings.printLongDuration(-duration);
        assertEquals("-5 days, -23 hours, -1 minute and -99 microseconds", representation);
    }

    @Test
    void testPrintLongDurationSeconds() {
        String representation = Strings.printLongDuration(18, TimeUnit.SECONDS);
        assertEquals("18 seconds", representation);
    }

    @Test
    void testReadDuration() {
        long duration = Strings.parseLongDuration("1 hour, 40 minutes and 33 sec.");
        long expected = TimeUnit.HOURS.toNanos(1) + TimeUnit.MINUTES.toNanos(40) + TimeUnit.SECONDS.toNanos(33);
        assertEquals(expected, duration);
    }

    @Test
    void testReadDurationInHours() {
        long duration = Strings.parseLongDuration("7days", TimeUnit.HOURS);
        assertEquals(7 * 24, duration);
    }

    @Test
    void testReadDurationIgnoreGarbage() {
        long duration = Strings.parseLongDuration("The delay should be 5 minutes, not more!");
        long expected = TimeUnit.MINUTES.toNanos(5);
        assertEquals(expected, duration);
    }

    @Test
    void testReadNegativeDuration() {
        long duration = Strings.parseLongDuration("-1 day 12 hours -30 minutes", TimeUnit.MINUTES);
        assertEquals(-TimeUnit.DAYS.toMinutes(1) + TimeUnit.HOURS.toMinutes(11) + 30, duration);
    }

    @Test
    void testReadDurationFailed1() {
        assertThrows(IllegalArgumentException.class,
                () -> Strings.parseLongDuration(""));
    }

    @Test
    void testReadDurationFailed2() {
        assertThrows(IllegalArgumentException.class,
                () -> Strings.parseLongDuration("100 years"));
    }

    @Test
    void testReadDurationFailed3() {
        assertThrows(IllegalArgumentException.class,
                () -> Strings.parseLongDuration("351"));
    }

    @Test
    void testReadDurationFailed4() {
        assertThrows(IllegalArgumentException.class,
                () -> Strings.parseLongDuration("seconds: 5"));
    }

    //@RepeatedTest(100) -- Alas this does not work in Intellij
    @Test
    void testRandomDurations() {
        for (int i=0; i < 100; i++) {
            long time = ThreadLocalRandom.current().nextLong();
            String repr = Strings.printLongDuration(time);
            long reverse = Strings.parseLongDuration(repr);
            assertEquals(time, reverse, () -> "Random duration " + time + " printed as " + repr + " is read to " + reverse);
        }
    }
}
