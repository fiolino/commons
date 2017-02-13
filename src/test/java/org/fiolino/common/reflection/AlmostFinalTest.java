package org.fiolino.common.reflection;

import org.junit.Test;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static java.lang.invoke.MethodType.methodType;
import static org.junit.Assert.*;

/**
 * Created by Kuli on 10/11/2016.
 */
public class AlmostFinalTest {
  private static final MethodHandle GET_STRING;
  private static final AlmostFinal<String> STRING_VAL;
  private static final MethodHandle GET_INT;
  private static final AlmostFinal<Integer> INT_VAL;
  private static final MethodHandle GET_LONG;
  private static final AlmostFinal<Long> LONG_VAL;
  private static final MethodHandle GET_DOUBLE;
  private static final AlmostFinal<Double> DOUBLE_VAL;
  private static final MethodHandle GET_BOOLEAN;
  private static final AlmostFinal<Boolean> BOOLEAN_VAL;

  static {
    STRING_VAL = AlmostFinal.forReference(String.class, "Initial");
    GET_STRING = STRING_VAL.createGetter();

    INT_VAL = AlmostFinal.forInt(2046);
    GET_INT = INT_VAL.createGetter();

    LONG_VAL = AlmostFinal.forLong(199191991919919L);
    GET_LONG = LONG_VAL.createGetter();

    DOUBLE_VAL = AlmostFinal.forDouble(Math.PI);
    GET_DOUBLE = DOUBLE_VAL.createGetter();

    BOOLEAN_VAL = AlmostFinal.forBoolean(false);
    GET_BOOLEAN = BOOLEAN_VAL.createGetter();
  }

  @Test
  public void testString() throws Throwable {
    String s = (String) GET_STRING.invokeExact();
    assertEquals("Initial", s);
    MethodHandle longer = MethodHandles.publicLookup().findVirtual(String.class, "concat",
            methodType(String.class, String.class));
    longer = MethodHandles.insertArguments(longer, 1, " value");
    longer = MethodHandles.foldArguments(longer, GET_STRING);
    String l = (String)longer.invokeExact();
    assertEquals("Initial value", l);

    STRING_VAL.updateTo("New");
    s = (String) GET_STRING.invokeExact();
    assertEquals("New", s);
    l = (String)longer.invokeExact();
    assertEquals("New value", l);
  }

  private static boolean checkBool() {
    try {
      return (boolean) GET_BOOLEAN.invokeExact();
    } catch (Throwable t) {
      throw new AssertionError(t);
    }
  }

  @Test(timeout = 60000)
  public void testMultiThreads() throws Throwable {
    assertFalse(checkBool());
    final AtomicInteger falseCounter = new AtomicInteger();
    final AtomicInteger trueCounter = new AtomicInteger();
    final AtomicBoolean running = new AtomicBoolean(true);
    Thread t = new Thread(() -> {
      while (running.get()) {
        if (checkBool()) {
          trueCounter.incrementAndGet();
        } else {
          falseCounter.incrementAndGet();
        }
      }
    });
    t.start();
    TimeUnit.SECONDS.sleep(1);
    assertTrue("Background thread didn't start yet?", falseCounter.get() > 100);
    assertEquals("Wrong target counter", 0, trueCounter.get());

    BOOLEAN_VAL.updateTo(true);
    TimeUnit.SECONDS.sleep(1);
    assertTrue("Background thread didn't notice value update?", trueCounter.get() > 100);
  }

  @Test
  public void testInt() throws Throwable {
    int i = (int) GET_INT.invokeExact();
    assertEquals(2046, i);
    MethodHandle negate = MethodHandles.publicLookup().findStatic(Math.class, "negateExact",
            methodType(int.class, int.class));
    negate = MethodHandles.filterReturnValue(GET_INT, negate);
    int n = (int) negate.invokeExact();
    assertEquals(-2046, n);

    INT_VAL.updateTo(-1103);
    i = (int) GET_INT.invokeExact();
    assertEquals(-1103, i);
    n = (int) negate.invokeExact();
    assertEquals(1103, n);
  }

  @Test
  public void testLong() throws Throwable {
    long l = (long) GET_LONG.invokeExact();
    assertEquals(199191991919919L, l);
    MethodHandle negate = MethodHandles.publicLookup().findStatic(Math.class, "negateExact",
            methodType(long.class, long.class));
    negate = MethodHandles.filterReturnValue(GET_LONG, negate);
    long n = (long) negate.invokeExact();
    assertEquals(-199191991919919L, n);

    LONG_VAL.updateTo(-44444444L);
    l = (long) GET_LONG.invokeExact();
    assertEquals(-44444444, l);
    n = (long) negate.invokeExact();
    assertEquals(44444444, n);
  }

  @Test
  public void testDouble() throws Throwable {
    double d = (double) GET_DOUBLE.invokeExact();
    assertEquals(Math.PI, d, 0.0001);
    MethodHandle rint = MethodHandles.publicLookup().findStatic(Math.class, "rint",
            methodType(double.class, double.class));
    rint = MethodHandles.filterReturnValue(GET_DOUBLE, rint);
    double n = (double) rint.invokeExact();
    assertEquals(3.0, n, 0.0001);

    DOUBLE_VAL.updateTo(-88.888);
    d = (double) GET_DOUBLE.invokeExact();
    assertEquals(-88.888, d, 0.0001);
    n = (double) rint.invokeExact();
    assertEquals(-89.0, n, 0.0001);
  }
}
