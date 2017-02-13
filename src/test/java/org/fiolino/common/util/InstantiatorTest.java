package org.fiolino.common.util;

import org.fiolino.common.ioc.PostProcessor;
import org.fiolino.data.annotation.Mandatory;
import org.junit.Test;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.junit.Assert.*;

/**
 * Created by kuli on 08.12.15.
 */
public class InstantiatorTest {

  public static class Normal {
    boolean flag;
  }

  @Test
  public void testNormal() {
    Supplier<Normal> instantiator = Instantiator.creatorFor(Normal.class);
    Normal result = instantiator.get();

    assertNotNull(result);
    assertFalse(result.flag);
  }

  public static class WithPostProcessor extends Normal implements PostProcessor {
    @Override
    public void postConstruct() {
      flag = true;
    }
  }

  @Test
  public void testPostProcessor() {
    Supplier<WithPostProcessor> instantiator = Instantiator.creatorFor(WithPostProcessor.class);
    Normal result = instantiator.get();

    assertNotNull(result);
    assertTrue(result.flag);
  }

  @Test
  public void testWithArgument() {
    Function<String, WithPostProcessor> instantiator = Instantiator.creatorWithArgument(WithPostProcessor.class, String.class);
    Normal result = instantiator.apply("Hello!");

    assertNotNull(result);
    assertTrue(result.flag);
  }

  @Test
  public void testCorrectArgument() {
    Function<String, StringBuilder> instantiator = Instantiator.creatorWithArgument(StringBuilder.class, String.class);
    StringBuilder result = instantiator.apply("Hello!");

    assertNotNull(result);
    assertEquals("Hello!", result.toString());
  }

  @Test
  public void testPrimitive() {
    Function<Integer, AtomicInteger> instantiator = Instantiator.creatorWithArgument(AtomicInteger.class, Integer.class);
    AtomicInteger result = instantiator.apply(123);

    assertNotNull(result);
    assertEquals(123, result.intValue());
  }

  @Test
  public void testPrimitiveInsteadOfObject() {
    Function<Object, AtomicInteger> instantiator = Instantiator.creatorWithArgument(AtomicInteger.class, Object.class);
    AtomicInteger result = instantiator.apply(123);

    assertNotNull(result);
    assertEquals(123, result.intValue());
  }

  @Test(expected = IllegalArgumentException.class)
  public void testWrongArgument() {
    Function<Object, AtomicInteger> instantiator = Instantiator.creatorWithArgument(AtomicInteger.class, Object.class);
    AtomicInteger result = instantiator.apply("Hello!");

    fail("Shouldn't reach this");
  }

  public static class WithOptionalArgument {
    public WithOptionalArgument(String unused) {
    }
  }

  @Test
  public void testNotMandatory() {
    Function<String, WithOptionalArgument> instantiator = Instantiator.creatorWithArgument(WithOptionalArgument.class, String.class);
    WithOptionalArgument result = instantiator.apply(null);

    assertNotNull(result);
  }

  public static class WithMandatoryArgument {
    public WithMandatoryArgument(@Mandatory String unused) {
    }
  }

  @Test
  public void testMandatory() {
    Function<String, WithMandatoryArgument> instantiator = Instantiator.creatorWithArgument(WithMandatoryArgument.class, String.class);
    WithMandatoryArgument result = instantiator.apply("Hello!");

    assertNotNull(result);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testFailedMandatory() {
    Function<String, WithMandatoryArgument> instantiator = Instantiator.creatorWithArgument(WithMandatoryArgument.class, String.class);
    instantiator.apply(null);

    fail("Shouldn't reach this");
  }

  public static class Hidden {

    private Hidden() {
    }

    static MethodHandles.Lookup lookup() {
      return MethodHandles.lookup();
    }
  }

  @Test
  public void testPrivateAccess() {
    Supplier<Hidden> supplier = Instantiator.creatorFor(Hidden.lookup(), Hidden.class);
    Hidden result = supplier.get();
    assertNotNull(result);
  }

  public static class HiddenWithArgument {
    HiddenWithArgument(String unused) {

    }

    static MethodHandles.Lookup lookup() {
      return MethodHandles.lookup();
    }
  }

  @Test
  public void testPrivateAccessWithArgument() {
    Function<String, Hidden> function1 = Instantiator.creatorWithArgument(Hidden.lookup(), Hidden.class, String.class);
    Hidden result1 = function1.apply("Hello!");
    assertNotNull(result1);

    Function<String, HiddenWithArgument> function2 = Instantiator.creatorWithArgument(HiddenWithArgument.lookup(), HiddenWithArgument.class, String.class);
    HiddenWithArgument result2 = function2.apply("Hello!");
    assertNotNull(result2);
  }

//
//    @Test
//    public void testFiltered() {
//        Function<String, StringBuilder> instantiator = Instantiator.creatorWithFilteredArgument(StringBuilder.class, String.class, s -> s + " " + s);
//        StringBuilder result = instantiator.apply("hello");
//
//        assertNotNull(result);
//        assertTrue(result instanceof StringBuilder);
//        assertEquals("hello hello", result.toString());
//    }
//
//    @Test
//    public void testFilteredChangeType() {
//        Function<String, AtomicInteger> instantiator = Instantiator.creatorWithFilteredArgument(AtomicInteger.class, int.class, Integer::parseInt);
//        AtomicInteger result = instantiator.apply("123");
//
//        assertNotNull(result);
//        assertTrue(result instanceof AtomicInteger);
//        assertEquals(123, result.intValue());
//    }
}
