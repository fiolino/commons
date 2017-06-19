package org.fiolino.common.ioc;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Created by kuli on 16.06.17.
 */

public class PropertiesTest {

    @Test
    public void testSingleProperties() {
        String v = Properties.getSingleEntry("key1");
        assertEquals("value1", v);
        v = Properties.getSingleEntry("key2");
        assertEquals("value2", v);
        v = Properties.getSingleEntry("key3");
        assertEquals("value3", v);
        v = Properties.getSingleEntry("key4");
        assertNull(v);
    }

    @Test
    public void testSinglePropertiesAsMulti() {
        String[] v = Properties.getMultipleEntries("key1");
        assertArrayEquals(new String[] {"value1"}, v);
        v = Properties.getMultipleEntries("key2");
        assertArrayEquals(new String[] {"value2"}, v);
        v = Properties.getMultipleEntries("key3");
        assertArrayEquals(new String[] {"value3"}, v);
        v = Properties.getMultipleEntries("key4");
        assertArrayEquals(new String[] {}, v);
    }

    @Test
    public void testMultiProperties() {
        String[] v = Properties.getMultipleEntries("multi1");
        assertArrayEquals(new String[] {"v1", "v2"}, v);

        v = Properties.getMultipleEntries("multi2");
        assertArrayEquals(new String[] {" With, comma \\", "More from this"}, v);

        v = Properties.getMultipleEntries("multi3");
        assertArrayEquals(new String[] {"", ""}, v);

        v = Properties.getMultipleEntries("multi4");
        assertArrayEquals(new String[] {"First entry", "Second entry", "Third entry"}, v);
    }

    @Test
    public void testMultiPropertiesAsSingle() {
        String v = Properties.getSingleEntry("multi1");
        assertNull(v); // Because the only entry has multiple values

        v = Properties.getSingleEntry("multi2");
        assertEquals("More from this", v); // Because test2.properties defines a single entry

        v = Properties.getSingleEntry("multi3");
        assertNull(v); // Because the only entry has multiple values

        v = Properties.getSingleEntry("multi4");
        assertEquals("First entry", v); // Because test.properties defines a single entry and test2.properties multiple
    }

    @Test
    public void testIndividualLines() {
        assertTrue(Properties.isIndividualTerm("Individual line"));
        assertFalse(Properties.isIndividualTerm("No such line"));
    }
}
