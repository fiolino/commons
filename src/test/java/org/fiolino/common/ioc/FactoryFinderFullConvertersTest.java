package org.fiolino.common.ioc;

import junit.framework.AssertionFailedError;
import org.fiolino.common.reflection.MethodLocator;
import org.fiolino.common.reflection.Methods;
import org.fiolino.common.reflection.NoMatchingConverterException;
import org.junit.jupiter.api.Test;

import java.lang.annotation.ElementType;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Date;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static java.lang.invoke.MethodHandles.lookup;
import static java.lang.invoke.MethodHandles.publicLookup;
import static java.lang.invoke.MethodType.methodType;
import static org.junit.jupiter.api.Assertions.*;

class FactoryFinderFullConvertersTest {
    private void checkIsLambda(Object func) {
        assertTrue(Methods.wasLambdafiedDirect(func), "Should be a lambda function");
    }

    private final MethodHandles.Lookup l = lookup();
    
    private MethodHandle getHandle(Class<?> returnValue, Object provider) {
        return MethodLocator.forLocal(l, provider.getClass()).methods()
                .filter(info -> info.getMethod().getDeclaringClass() != Object.class)
                .reduce((info1, info2) -> {
                    throw new AssertionFailedError(provider + " should contain exactly one method");
                })
                .map(info -> info.getStaticHandle(() -> provider))
                .map(h -> FactoryFinder.full().convertReturnTypeTo(h, returnValue))
                .orElseThrow();
    }

    //private <T> T getLambda(Class<T> functionalType, Object prrovider) {

    //}

    @Test
    void testFromInt() throws Throwable {
        Object getInt = new Object() {
            int getInt() {
                return 15;
            }
        };
        MethodHandle c = getHandle(int.class, getInt);
        assertEquals(15, (int) c.invokeExact());
        c = getHandle(long.class, getInt);
        assertEquals(15L, (long) c.invokeExact());
        c = getHandle(boolean.class, getInt);
        assertTrue((boolean) c.invokeExact());
        c = getHandle(float.class, getInt);
        assertEquals(15.0f, (float) c.invokeExact(), 0.1f);
        c = getHandle(String.class, getInt);
        assertEquals("15", (String) c.invokeExact());
        c = getHandle(Integer.class, getInt);
        assertEquals((Integer) 15, (Integer) c.invokeExact());
    }

    @Test
    void testFromInteger() throws Throwable {
        Object getInteger = new Object() {
            Integer getInteger() {
                return 15;
            }
        };
        MethodHandle c = getHandle(String.class, getInteger);
        assertEquals("15", (String) c.invokeExact());
        c = getHandle(int.class, getInteger);
        assertEquals(15, (int) c.invokeExact());
        c = getHandle(long.class, getInteger);
        assertEquals(15L, (long) c.invokeExact());
        c = getHandle(double.class, getInteger);
        assertEquals(15.0, (double) c.invokeExact(), 0.0001);
    }

    @Test
    void testFromEnum() throws Throwable {
        Object getEnum = new Object() {
            TimeUnit getEnum() {
                return TimeUnit.DAYS;
            }
        };
        MethodHandle c = getHandle(Object.class, getEnum);
        assertEquals(TimeUnit.DAYS, (Object) c.invokeExact());
        c = getHandle(String.class, getEnum);
        assertEquals("DAYS", (String) c.invokeExact());
    }

    @Test
    void testFromNullEnum() throws Throwable {
        Object getEnum = new Object() {
            TimeUnit getEnum() {
                return null;
            }
        };
        MethodHandle c = getHandle(Object.class, getEnum);
        assertNull((Object) c.invokeExact());
        MethodHandle fail = getHandle(String.class, getEnum).asType(methodType(void.class));
        assertThrows(NullPointerException.class, fail::invokeExact);
    }

    public static class ValueOfTest {
        final int len;

        private ValueOfTest(int len) {
            this.len = len;
        }

        @SuppressWarnings("unused")
        public static ValueOfTest valueOf(String input) {
            return new ValueOfTest(input.length());
        }

        @Override
        public boolean equals(Object obj) {
            return obj != null && obj.getClass().equals(ValueOfTest.class) && ((ValueOfTest) obj).len == len;
        }

        @Override
        public int hashCode() {
            return len;
        }
    }

    @Test
    void testFromString() throws Throwable {
        Object getString = new Object() {
            @SuppressWarnings("unused")
            String getString(String val) {
                return val;
            }
        };
        MethodHandle c = getHandle(Object.class, getString);
        assertEquals("xx", (Object) c.invokeExact("xx"));
        c = getHandle(TimeUnit.class, getString);
        assertEquals(TimeUnit.MINUTES, (TimeUnit) c.invokeExact("MINUTES"));
        c = getHandle(int.class, getString);
        assertEquals(188, (int) c.invokeExact("188"));
        c = getHandle(float.class, getString);
        assertEquals(-1.234f, (float) c.invokeExact("-1.234"), 0.1f);
        c = getHandle(Long.class, getString);
        assertEquals((Long) 999999999999999L, (Long) c.invokeExact("999999999999999"));
        c = getHandle(boolean.class, getString);
        assertTrue((boolean) c.invokeExact("t"));
        assertFalse((boolean) c.invokeExact("try something longer"));
        assertFalse((boolean) c.invokeExact(""));
        c = getHandle(ValueOfTest.class, getString);
        assertEquals(new ValueOfTest(7), (ValueOfTest) c.invokeExact("1234567"));
        c = getHandle(BigInteger.class, getString);
        assertEquals(new BigInteger("100000"), (BigInteger) c.invokeExact("100000"));
        c = getHandle(BigDecimal.class, getString);
        assertEquals(new BigDecimal("555.666777"), (BigDecimal) c.invokeExact("555.666777"));
        c = getHandle(char.class, getString);
        assertEquals('~', (char) c.invokeExact("~/path"));
    }

    @SuppressWarnings("unused")
    public static class ToPrimitiveTester {
        public int intValue() {
            return 33;
        }

        public long longValue() {
            return -9191919191919191L;
        }

        public float floatValue() {
            return 0.01f;
        }

        public boolean booleanValue() {
            return true;
        }
    }

    @Test
    void testToPrimitive() throws Throwable {
        Object getTester = new Object() {
            ToPrimitiveTester getTester() {
                return new ToPrimitiveTester();
            }
        };
        MethodHandle c = getHandle(int.class, getTester);
        assertEquals(33, (int) c.invokeExact());
        c = getHandle(long.class, getTester);
        assertEquals(-9191919191919191L, (long) c.invokeExact());
        c = getHandle(float.class, getTester);
        assertEquals(0.01f, (float) c.invokeExact(), 0.0001f);
        c = getHandle(boolean.class, getTester);
        assertTrue((boolean) c.invokeExact());
    }

    @Test
    void testDateConversion() throws Throwable {
        Object getDate = new Object() {
            Date getData(long time) {
                return new Date(time);
            }
        };
        long current = System.currentTimeMillis();
        MethodHandle c = getHandle(long.class, getDate);
        assertEquals(current, (long) c.invokeExact(current));

        getDate = new Object() {
            java.sql.Date getSqlDate(long time) {
                return new java.sql.Date(time);
            }
        };
        c = getHandle(long.class, getDate);
        assertEquals(current, (long) c.invokeExact(current));
        c = getHandle(Date.class, getDate);
        assertEquals(new Date(current), (Date) c.invokeExact(current));

        getDate = new Object() {
            java.sql.Time getSqlTime(long time) {
                return new java.sql.Time(time);
            }
        };
        c = getHandle(long.class, getDate);
        assertEquals(current, (long) c.invokeExact(current));
        c = getHandle(Date.class, getDate);
        assertEquals(new Date(current), (Date) c.invokeExact(current));

        getDate = new Object() {
            java.sql.Timestamp getSqlTimestamp(long time) {
                return new java.sql.Timestamp(time);
            }
        };
        c = getHandle(long.class, getDate);
        assertEquals(current, (long) c.invokeExact(current));
        c = getHandle(Date.class, getDate);
        assertEquals(new Date(current), (Date) c.invokeExact(current));
    }

    @Test
    void testBooleanAndChar() throws Throwable {
        Object getBool = new Object() {
            boolean getBool(boolean b) {
                return b;
            }
        };
        Object getChar = new Object() {
            char getChar(char c) {
                return c;
            }
        };
        MethodHandle c = getHandle(char.class, getBool);
        assertEquals('t', (char) c.invokeExact(true));
        assertEquals('f', (char) c.invokeExact(false));
        c = getHandle(String.class, getBool);
        assertEquals("true", (String) c.invokeExact(true));
        assertEquals("false", (String) c.invokeExact(false));
        c = getHandle(boolean.class, getChar);
        assertTrue((boolean) c.invokeExact('t'));
        assertFalse((boolean) c.invokeExact('f'));
        assertTrue((boolean) c.invokeExact('1'));
        assertFalse((boolean) c.invokeExact('0'));
        c = getHandle(String.class, getChar);
        for (char ch = '0'; ch <= 'Z'; ch++) {
            String s = (String) c.invokeExact(ch);
            assertEquals(1, s.length());
            assertEquals(ch, s.charAt(0));
        }
    }


    @Test
    void testDirectConverter() throws Throwable {
        // Test unnecessary converter
        Optional<MethodHandle> c = FactoryFinder.full().find(Number.class, Integer.class);
        assertFalse(c.isPresent());
        MethodHandle h = FactoryFinder.full().findOrFail(int.class, Number.class);
        assertNotNull(h);
        assertEquals(methodType(int.class, Number.class), h.type());
        int n = (int) h.invokeExact((Number) 12L);
        assertEquals(12, n);

        // Test enum converter
        h = FactoryFinder.full().findOrFail(String.class, TimeUnit.class);
        assertEquals(methodType(String.class, TimeUnit.class), h.type());
        String s = (String) h.invokeExact(TimeUnit.SECONDS);
        assertEquals("SECONDS", s);
    }


    @Test
    void testConvertParameter() throws Throwable {
        MethodHandle appendCharSeq = publicLookup().findVirtual(StringBuilder.class, "append",
                methodType(StringBuilder.class, CharSequence.class));
        // First try something that doesn't need to be converted
        MethodHandle appendString = FactoryFinder.full().convertArgumentTypesTo(appendCharSeq, 1, String.class);
        // then with a converter
        MethodHandle appendInt = FactoryFinder.full().convertArgumentTypesTo(appendString, 1, int.class);
        StringBuilder sb = new StringBuilder("The ");
        sb = (StringBuilder) appendCharSeq.invokeExact(sb, (CharSequence) "number ");
        sb = (StringBuilder) appendString.invokeExact(sb, "is ");
        sb = (StringBuilder) appendInt.invokeExact(sb, 42);
        assertEquals("The number is 42", sb.toString());
    }

    // Can't convert from any primitive to another wrapper
    @Test
    void testWrongPrimitive() throws NoMatchingConverterException {
        Optional<MethodHandle> converter = FactoryFinder.full().find(Integer.class, long.class);
        assertFalse(converter.isPresent());
    }

    // But can convert from any wrapper to some primitive as long as there's an xxxValue() method
    @Test
    void testConvertablePrimitive() throws Throwable {
        Optional<MethodHandle> converter = FactoryFinder.full().find(long.class, Integer.class);
        long val = (long) converter.get().invokeExact((Integer) 12345);
        assertEquals(12345L, val);
    }

    @SuppressWarnings("unused")
    private static String concat(String first, long second, Date last, boolean butOnlyIfTrue) {
        if (butOnlyIfTrue) {
            return first + second + " " + last.getTime();
        } else {
            return first + second;
        }
    }

    public static class StringWrapper {
        final String val;

        private StringWrapper(String val) {
            this.val = val;
        }

        public static StringWrapper valueOf(String v) {
            return new StringWrapper(v);
        }
    }

    @Test
    void testConvertType() throws Throwable {
        MethodHandle concat = l.findStatic(l.lookupClass(), "concat", methodType(
                String.class, String.class, long.class, Date.class, boolean.class));
        MethodHandle different = FactoryFinder.full().convertTo(concat,
                methodType(StringWrapper.class, BigDecimal.class, Long.class, long.class, char.class));

        long now = System.currentTimeMillis();
        StringWrapper result = (StringWrapper) different.invokeExact(
                new BigDecimal("10.6"), (Long) 559955L, now, 'f');
        assertEquals("10.6559955", result.val);
        result = (StringWrapper) different.invokeExact(
                new BigDecimal("0.0"), (Long) 99999L, now, 't');
        assertEquals("0.099999 " + now, result.val);
    }

    @Test
    void testLessArguments() throws Throwable {
        MethodHandle concat = l.findStatic(l.lookupClass(), "concat", methodType(
                String.class, String.class, long.class, Date.class, boolean.class));
        Date now = new Date();
        MethodHandle lessArgs = FactoryFinder.full().convertTo(concat,
                methodType(StringWrapper.class, TimeUnit.class), now, now, true);
        StringWrapper result = (StringWrapper) lessArgs.invokeExact(TimeUnit.HOURS);
        long ms = now.getTime();
        assertEquals("HOURS" + ms + " " + ms, result.val);

        lessArgs = FactoryFinder.full().convertTo(concat,
                methodType(StringWrapper.class), ElementType.FIELD, null, null, null);
        result = (StringWrapper) lessArgs.invokeExact();
        assertEquals("FIELD0", result.val);
    }

    @Test
    void testMoreArguments() throws Throwable {
        MethodHandle max = l.findStatic(Math.class, "max", methodType(int.class, int.class, int.class));
        MethodHandle more = FactoryFinder.full().convertTo(max,
                methodType(String.class, Float.class, BigInteger.class, Date.class, TimeUnit.class));
        String result = (String) more.invokeExact((Float) 2.0f, new BigInteger("1000"), new Date(), TimeUnit.MICROSECONDS);
        assertEquals("1000", result);
    }
}
