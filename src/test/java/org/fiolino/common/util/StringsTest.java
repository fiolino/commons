package org.fiolino.common.util;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Created by kuli on 25.11.15.
 */
public class StringsTest {

    @Test
    public void testQuote() {
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
    public void testUnquote() {
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

        unquoted = Strings.unquote("\"Text with \\n, \\t, and \\r and \\b; also \\f plus \\\\ and \\'. \"");
        assertEquals("Text with \n, \t, and \r and \b; also \f plus \\ and '. ", unquoted);

        unquoted = Strings.unquote("\"Text with strange \\x or \\0.\"");
        assertEquals("Text with strange x or 0.", unquoted);
    }

    @Test
    public void testRemoveLeading() {
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
    public void testLowercaseFirst() {
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
    public void testUppercase() {
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
    public void testNormalize() {
        String normal = Strings.normalizeName("Hello World!");
        assertEquals("Hello World!", normal);

        normal = Strings.normalizeName("HELLO_WORLD!");
        assertEquals("helloWorld!", normal);

        normal = Strings.normalizeName("HELLO_WORLD$$HI_JOHN!");
        assertEquals("helloWorld..hiJohn!", normal);

        normal = Strings.normalizeName("HELLO$_WORLD!");
        assertEquals("hello.World!", normal);
    }

    @Test
    public void appendNumberTest() {
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
    public void testOverlap() {
        String overlap = Strings.combinationOf("one_two_three_four", "two_three_foooour");
        assertEquals("two_three", overlap);

        overlap = Strings.combinationOf("one_two_three_four", "two_three_foooour", "bla_three_three");
        assertEquals("three", overlap);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testFailedOverlap() {
        Strings.combinationOf("one_two_three_four", "two_three_foooour", "two_two", "three_three");
    }

    @Test
    public void testInsertValues() {
        Map<String, String> map = new HashMap<>();
        map.put("Normal_value", "Normal-Replacement");
        map.put("123", "987");
        map.put("   -- !!!! --   ", "weird");

        String replaceAll = Strings.insertValues("This is a $Normal_value, this is a $123 number, and this is ${   -- !!!! --   } of course.", map);
        assertEquals("This is a Normal-Replacement, this is a 987 number, and this is weird of course.", replaceAll);

        String withDollars = Strings.insertValues(" $$$ ", map);
        assertEquals(" $$$ ", withDollars);

        String only = Strings.insertValues("$123", map);
        assertEquals("987", only);

        String multi = Strings.insertValues("1. ${   -- !!!! --   }, and 2. ${   -- !!!! --   }", map);
        assertEquals("1. weird, and 2. weird", multi);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testFailedInsertValues() {
        Strings.insertValues("$bbb", new HashMap<>());
    }

    @Test
    public void testExtractUntil() {
        String text = " Some text # comment";
        int sign = text.indexOf('#');
        Strings.Extract x = Strings.extractUntil(text, 0, CharSet.of("#%!"));
        assertEquals("Some text", x.extraction);
        assertEquals(sign, x.end);
        assertEquals('#', x.stopSign);
        assertFalse(x.wasEOL());
        assertEquals(Strings.Extract.QuotationStatus.UNQUOTED, x.quotationStatus);

        x = Strings.extractUntil(text, sign, CharSet.of('#'));
        assertEquals("", x.extraction);
        assertEquals(sign, x.end);
        assertEquals('#', x.stopSign);
        assertFalse(x.wasEOL());
        assertEquals(Strings.Extract.QuotationStatus.UNQUOTED, x.quotationStatus);

        x = Strings.extractUntil(text, text.length(), CharSet.of('#'));
        assertEquals("", x.extraction);
        assertEquals(-1, x.end);
        assertEquals(Character.UNASSIGNED, x.stopSign);
        assertTrue(x.wasEOL());
        assertEquals(Strings.Extract.QuotationStatus.UNQUOTED, x.quotationStatus);

        text = " Begin with something \" Then quoted\\\\\\\"\" Then not again";
        sign = " Begin with something ".length();
        x = Strings.extractUntil(text, 0, CharSet.of('#'));
        assertEquals("Begin with something", x.extraction);
        assertEquals(sign, x.end);
        assertEquals('"', x.stopSign);
        assertFalse(x.wasEOL());
        assertEquals(Strings.Extract.QuotationStatus.UNQUOTED, x.quotationStatus);

        x = Strings.extractUntil(text, sign, CharSet.of('#'));
        sign = " Begin with something \" Then quoted\\\\\\\"\" ".length();
        assertEquals(" Then quoted\\\"", x.extraction);
        assertEquals(sign, x.end);
        assertEquals('T', x.stopSign);
        assertFalse(x.wasEOL());
        assertEquals(Strings.Extract.QuotationStatus.QUOTED, x.quotationStatus);

        x = Strings.extractUntil("  \" Quoted ###  \"", 0, CharSet.of('#'));
        assertEquals(" Quoted ###  ", x.extraction);
        assertEquals(-1, x.end);
        assertTrue(x.wasEOL());
        assertEquals(Strings.Extract.QuotationStatus.QUOTED, x.quotationStatus);

        x = Strings.extractUntil("\" Quotation left open  ", 0, CharSet.of('#'));
        assertEquals(" Quotation left open  \n", x.extraction);
        assertEquals(-1, x.end);
        assertTrue(x.wasEOL());
        assertEquals(Strings.Extract.QuotationStatus.QUOTED_OPEN, x.quotationStatus);
    }

    @Test
    public void testPrintLongDuration0() {
        String representation = Strings.printLongDuration(0);
        assertEquals("0", representation);
    }

    @Test
    public void testPrintLongDurationNanos() {
        String representation = Strings.printLongDuration(123);
        assertEquals("123 nanoseconds", representation);
    }

    @Test
    public void testPrintLongDurationDays() {
        long duration = TimeUnit.DAYS.toNanos(5);
        duration += TimeUnit.HOURS.toNanos(23);
        duration += TimeUnit.MINUTES.toNanos(1);
        duration += TimeUnit.MICROSECONDS.toNanos(99);
        // MILLIS and NANOS are 0 by intention
        String representation = Strings.printLongDuration(duration);
        assertEquals("5 days 23 hours 1 minute 99 microseconds", representation);
    }

    @Test
    public void testReadDuration() {
        long duration = Strings.parseLongDuration("1 hour, 40 minutes and 33 sec.");
        long expected = TimeUnit.HOURS.toNanos(1) + TimeUnit.MINUTES.toNanos(40) + TimeUnit.SECONDS.toNanos(33);
        assertEquals(expected, duration);
    }

    @Test
    public void testReadDurationIgnoreGarbage() {
        long duration = Strings.parseLongDuration("The duration should be 5 minutes, not more!");
        long expected = TimeUnit.MINUTES.toNanos(5);
        assertEquals(expected, duration);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testReadDurationFailed1() {
        Strings.parseLongDuration("");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testReadDurationFailed2() {
        Strings.parseLongDuration("100 years");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testReadDurationFailed3() {
        Strings.parseLongDuration("351");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testReadDurationFailed4() {
        Strings.parseLongDuration("seconds: 5");
    }
}
