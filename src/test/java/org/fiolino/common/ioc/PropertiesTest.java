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
        assertEquals(1, v.length);
        assertEquals("value1", v[0]);
        v = Properties.getMultipleEntries("key2");
        assertEquals(1, v.length);
        assertEquals("value2", v[0]);
        v = Properties.getMultipleEntries("key3");
        assertEquals(1, v.length);
        assertEquals("value3", v[0]);
        v = Properties.getMultipleEntries("key4");
        assertNull(v);
    }

    @Test
    public void testMultiProperties() {
        String[] v = Properties.getMultipleEntries("multi1");
        assertEquals(2, v.length);
        assertEquals("v1", v[0]);
        assertEquals("v2", v[1]);

        v = Properties.getMultipleEntries("multi2");
        assertEquals(2, v.length);
        assertEquals(" With, comma \\", v[0]);
        assertEquals("More from this", v[1]);

        v = Properties.getMultipleEntries("multi3");
        assertEquals(2, v.length);
        assertEquals("", v[0]);
        assertEquals("", v[1]);

        v = Properties.getMultipleEntries("multi4");
        assertEquals(3, v.length);
        assertEquals("First entry", v[0]);
        assertEquals("Second entry", v[1]);
        assertEquals("Third entry", v[2]);
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
