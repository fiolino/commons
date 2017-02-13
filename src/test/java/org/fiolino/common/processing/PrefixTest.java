package org.fiolino.common.processing;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Created by kuli on 05.04.16.
 */
public class PrefixTest {
  @Test
  public void testEmpty() {
    Prefix p = Prefix.EMPTY;
    String[] names = p.createNames("test");
    assertEquals("test", names[0]);
  }

  @Test
  public void testDeeper() {
    Prefix p = Prefix.EMPTY;
    p = p.newSubPrefix("sub");
    String[] names = p.createNames("test");
    assertEquals("sub-test", names[0]);
  }

  @Test
  public void testOtherConcatenator() {
    Prefix p = Prefix.empty("~~~");
    p = p.newSubPrefix("sub");
    String[] names = p.createNames("test");
    assertEquals("sub~~~test", names[0]);
  }

  @Test
  public void testMulti() {
    Prefix p = Prefix.EMPTY;
    p = p.newSubPrefix("sub1", "sub2");
    List<String> names = Arrays.asList(p.createNames("test1", "test2", "test3"));
    assertEquals(6, names.size());
    assertTrue(names.contains("sub1-test1"));
    assertTrue(names.contains("sub1-test2"));
    assertTrue(names.contains("sub1-test3"));
    assertTrue(names.contains("sub2-test1"));
    assertTrue(names.contains("sub2-test2"));
    assertTrue(names.contains("sub2-test3"));
  }
}
