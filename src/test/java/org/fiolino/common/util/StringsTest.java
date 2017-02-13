package org.fiolino.common.util;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;

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

        num = Strings.appendNumber(new StringBuilder(), 0, 15).toString();
        assertEquals("000000000000000", num);

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
        Strings.insertValues("$bbb", new HashMap<String, String>());
    }

}
