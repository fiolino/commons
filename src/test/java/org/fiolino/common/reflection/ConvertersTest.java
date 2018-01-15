package org.fiolino.common.reflection;

import org.junit.Test;

import java.lang.annotation.ElementType;
import java.lang.invoke.MethodHandle;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Date;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static java.lang.invoke.MethodHandles.lookup;
import static java.lang.invoke.MethodHandles.publicLookup;
import static java.lang.invoke.MethodType.methodType;
import static org.junit.Assert.*;

/**
 * Created by Michael Kuhlmann on 15.12.2015.
 */
public class ConvertersTest {

    private static class DataContainer {
        final int number;
        final String string;
        final Object object;
        final TimeUnit timeUnit;
        final BigDecimal decimal;

        DataContainer(int number, String string, Object object, TimeUnit timeUnit, BigDecimal decimal) {
            this.number = number;
            this.string = string;
            this.object = object;
            this.timeUnit = timeUnit;
            this.decimal = decimal;
        }
    }

    @Test
    public void testAcceptStrings() throws Throwable {
        MethodHandle constructor = lookup().findConstructor(DataContainer.class, methodType(void.class, int.class, String.class, Object.class, TimeUnit.class, BigDecimal.class));
        Object object = new AtomicInteger(9997);
        BigDecimal dec = new BigDecimal("888888888888");

        MethodHandle acceptString1 = Converters.acceptString(constructor, 0);
        DataContainer container = (DataContainer) acceptString1.invokeExact((Object) 115, "String", object, TimeUnit.SECONDS, dec);
        assertEquals(115, container.number);

        container = (DataContainer) acceptString1.invokeExact((Object) "-331133", "String", object, TimeUnit.SECONDS, dec);
        assertEquals(-331133, container.number);

        MethodHandle acceptString2 = Converters.acceptString(constructor, 1);
        container = (DataContainer) acceptString2.invokeExact(115, (Object) "String", object, TimeUnit.SECONDS, dec);
        assertEquals("String", container.string);

        MethodHandle acceptString3 = Converters.acceptString(constructor, 2);
        container = (DataContainer) acceptString3.invokeExact(115, "String", object, TimeUnit.SECONDS, dec);
        assertEquals(object, container.object);

        container = (DataContainer) acceptString3.invokeExact(115, "String", (Object) "47", TimeUnit.SECONDS, dec);
        assertEquals("47", container.object);

        MethodHandle acceptString4 = Converters.acceptString(constructor, 3);
        container = (DataContainer) acceptString4.invokeExact(115, "String", object, (Object) TimeUnit.MICROSECONDS, dec);
        assertEquals(TimeUnit.MICROSECONDS, container.timeUnit);

        container = (DataContainer) acceptString4.invokeExact(115, "String", object, (Object) "MICROSECONDS", dec);
        assertEquals(TimeUnit.MICROSECONDS, container.timeUnit);

        MethodHandle acceptString5 = Converters.acceptString(constructor, 4);
        container = (DataContainer) acceptString5.invokeExact(115, "String", object, TimeUnit.SECONDS, (Object) dec);
        assertEquals(dec, container.decimal);

        container = (DataContainer) acceptString5.invokeExact(115, "String", object, TimeUnit.SECONDS, (Object) "1999");
        assertEquals(new BigDecimal("1999"), container.decimal);
    }

    private MethodHandle getHandle(ConverterLocator loc, Class<?> returnValue, Object provider) {
        MethodHandle pHandle = Methods.visitMethodsWithStaticContext(lookup(), provider, null, (v, m, handleSupplier) -> {
            if (m.getDeclaringClass() != Object.class) {
                return handleSupplier.get();
            }
            return v;
        });
        return Converters.convertReturnTypeTo(pHandle, loc, returnValue);
    }

    @Test
    public void testFromInt() throws Throwable {
        Object getInt = new Object() {
            int getInt() {
                return 15;
            }
        };
        MethodHandle c = getHandle(Converters.defaultConverters, int.class, getInt);
        assertEquals(15, (int) c.invokeExact());
        c = getHandle(Converters.defaultConverters, long.class, getInt);
        assertEquals(15L, (long) c.invokeExact());
        c = getHandle(Converters.defaultConverters, boolean.class, getInt);
        assertEquals(true, (boolean) c.invokeExact());
        c = getHandle(Converters.defaultConverters, float.class, getInt);
        assertEquals(15.0f, (float) c.invokeExact(), 0.1f);
        c = getHandle(Converters.defaultConverters, String.class, getInt);
        assertEquals("15", (String) c.invokeExact());
        c = getHandle(Converters.defaultConverters, Integer.class, getInt);
        assertEquals((Integer) 15, (Integer) c.invokeExact());
    }

    @Test
    public void testFromInteger() throws Throwable {
        Object getInteger = new Object() {
            Integer getInteger() {
                return 15;
            }
        };
        MethodHandle c = getHandle(Converters.defaultConverters, String.class, getInteger);
        assertEquals("15", (String) c.invokeExact());
        c = getHandle(Converters.defaultConverters, int.class, getInteger);
        assertEquals(15, (int) c.invokeExact());
        c = getHandle(Converters.defaultConverters, long.class, getInteger);
        assertEquals(15L, (long) c.invokeExact());
        c = getHandle(Converters.defaultConverters, double.class, getInteger);
        assertEquals(15.0, (double) c.invokeExact(), 0.0001);
    }

    @Test
    public void testFromEnum() throws Throwable {
        Object getEnum = new Object() {
            TimeUnit getEnum() {
                return TimeUnit.DAYS;
            }
        };
        MethodHandle c = getHandle(Converters.defaultConverters, Object.class, getEnum);
        assertEquals(TimeUnit.DAYS, (Object) c.invokeExact());
        c = getHandle(Converters.defaultConverters, String.class, getEnum);
        assertEquals("DAYS", (String) c.invokeExact());
    }

    @Test
    public void testFromNullEnum() throws Throwable {
        Object getEnum = new Object() {
            TimeUnit getEnum() {
                return null;
            }
        };
        MethodHandle c = getHandle(Converters.defaultConverters, Object.class, getEnum);
        assertNull((Object) c.invokeExact());
        c = getHandle(Converters.defaultConverters, String.class, getEnum);
        assertNull((String) c.invokeExact());
    }

    public static class ValueOfTest {
        final int len;

        private ValueOfTest(int len) {
            this.len = len;
        }

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
    public void testFromString() throws Throwable {
        Object getString = new Object() {
            @SuppressWarnings("unused")
            String getString(String val) {
                return val;
            }
        };
        MethodHandle c = getHandle(Converters.defaultConverters, Object.class, getString);
        assertEquals("xx", (Object) c.invokeExact("xx"));
        c = getHandle(Converters.defaultConverters, TimeUnit.class, getString);
        assertEquals(TimeUnit.MINUTES, (TimeUnit) c.invokeExact("MINUTES"));
        assertNull((TimeUnit) c.invokeExact((String) null));
        c = getHandle(Converters.defaultConverters, int.class, getString);
        assertEquals(188, (int) c.invokeExact("188"));
        c = getHandle(Converters.defaultConverters, float.class, getString);
        assertEquals(-1.234f, (float) c.invokeExact("-1.234"), 0.1f);
        c = getHandle(Converters.defaultConverters, Long.class, getString);
        assertEquals((Long) 999999999999999L, (Long) c.invokeExact("999999999999999"));
        c = getHandle(Converters.defaultConverters, boolean.class, getString);
        assertTrue((boolean) c.invokeExact("trara with t"));
        assertFalse((boolean) c.invokeExact("something without t"));
        assertFalse((boolean) c.invokeExact(""));
        c = getHandle(Converters.defaultConverters, ValueOfTest.class, getString);
        assertEquals(new ValueOfTest(7), (ValueOfTest) c.invokeExact("1234567"));
        c = getHandle(Converters.defaultConverters, BigInteger.class, getString);
        assertEquals(new BigInteger("100000"), (BigInteger) c.invokeExact("100000"));
        c = getHandle(Converters.defaultConverters, BigDecimal.class, getString);
        assertEquals(new BigDecimal("555.666777"), (BigDecimal) c.invokeExact("555.666777"));
        c = getHandle(Converters.defaultConverters, char.class, getString);
        assertEquals('~', (char) c.invokeExact("~/path"));
    }

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
    public void testToPrimitive() throws Throwable {
        Object getTester = new Object() {
            ToPrimitiveTester getTester() {
                return new ToPrimitiveTester();
            }
        };
        MethodHandle c = getHandle(Converters.defaultConverters, int.class, getTester);
        assertEquals(33, (int) c.invokeExact());
        c = getHandle(Converters.defaultConverters, long.class, getTester);
        assertEquals(-9191919191919191L, (long) c.invokeExact());
        c = getHandle(Converters.defaultConverters, float.class, getTester);
        assertEquals(0.01f, (float) c.invokeExact(), 0.0001f);
        c = getHandle(Converters.defaultConverters, boolean.class, getTester);
        assertTrue((boolean) c.invokeExact());
    }

    @Test
    public void testDateConversion() throws Throwable {
        Object getDate = new Object() {
            Date getData(long time) {
                return new Date(time);
            }
        };
        long current = System.currentTimeMillis();
        MethodHandle c = getHandle(Converters.defaultConverters, long.class, getDate);
        assertEquals(current, (long) c.invokeExact(current));

        getDate = new Object() {
            java.sql.Date getSqlDate(long time) {
                return new java.sql.Date(time);
            }
        };
        c = getHandle(Converters.defaultConverters, long.class, getDate);
        assertEquals(current, (long) c.invokeExact(current));
        c = getHandle(Converters.defaultConverters, Date.class, getDate);
        assertEquals(new Date(current), (Date) c.invokeExact(current));

        getDate = new Object() {
            java.sql.Time getSqlTime(long time) {
                return new java.sql.Time(time);
            }
        };
        c = getHandle(Converters.defaultConverters, long.class, getDate);
        assertEquals(current, (long) c.invokeExact(current));
        c = getHandle(Converters.defaultConverters, Date.class, getDate);
        assertEquals(new Date(current), (Date) c.invokeExact(current));

        getDate = new Object() {
            java.sql.Timestamp getSqlTimestamp(long time) {
                return new java.sql.Timestamp(time);
            }
        };
        c = getHandle(Converters.defaultConverters, long.class, getDate);
        assertEquals(current, (long) c.invokeExact(current));
        c = getHandle(Converters.defaultConverters, Date.class, getDate);
        assertEquals(new Date(current), (Date) c.invokeExact(current));
    }

    @Test
    public void testBooleanAndChar() throws Throwable {
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
        MethodHandle c = getHandle(Converters.defaultConverters, char.class, getBool);
        assertEquals('t', (char) c.invokeExact(true));
        assertEquals('f', (char) c.invokeExact(false));
        c = getHandle(Converters.defaultConverters, String.class, getBool);
        assertEquals("true", (String) c.invokeExact(true));
        assertEquals("false", (String) c.invokeExact(false));
        c = getHandle(Converters.defaultConverters, boolean.class, getChar);
        assertTrue((boolean) c.invokeExact('t'));
        assertFalse((boolean) c.invokeExact('f'));
        assertTrue((boolean) c.invokeExact('1'));
        assertFalse((boolean) c.invokeExact('0'));
        c = getHandle(Converters.defaultConverters, String.class, getChar);
        for (char ch = '0'; ch <= 'Z'; ch++) {
            String s = (String) c.invokeExact(ch);
            assertEquals(1, s.length());
            assertEquals(ch, s.charAt(0));
        }
    }

    @Test
    public void testDirectConverter() throws Throwable {
        // Test unnecessary converter
        Converter c = Converters.defaultConverters.find(Integer.class, Number.class);
        assertNotNull(c);
        assertNull(c.getConverter());
        MethodHandle h = Converters.findConverter(Converters.defaultConverters, Integer.class, Number.class);
        assertNotNull(h);
        assertEquals(methodType(Number.class, Integer.class), h.type());
        Number n = (Number) h.invokeExact((Integer) 12);
        assertEquals(12, n);

        // Test non-perfect converter
        c = Converters.defaultConverters.find(TimeUnit.class, String.class);
        assertNotNull(c);
        assertNotNull(c.getConverter());
        assertEquals(methodType(String.class, Enum.class), c.getConverter().type());
        h = Converters.findConverter(Converters.defaultConverters, TimeUnit.class, String.class);
        assertEquals(methodType(String.class, TimeUnit.class), h.type());
        String s = (String) h.invokeExact(TimeUnit.SECONDS);
        assertEquals("SECONDS", s);
    }

    @Test
    public void testConvertParameter() throws Throwable {
        MethodHandle appendCharSeq = publicLookup().findVirtual(StringBuilder.class, "append",
                methodType(StringBuilder.class, CharSequence.class));
        // First try something that doesn't need to be converted
        MethodHandle appendString = Converters.convertArgumentTypesTo(appendCharSeq, Converters.defaultConverters,
                1, String.class);
        // then with a converter
        MethodHandle appendInt = Converters.convertArgumentTypesTo(appendString, Converters.defaultConverters,
                1, int.class);
        StringBuilder sb = new StringBuilder("The ");
        sb = (StringBuilder) appendCharSeq.invokeExact(sb, (CharSequence) "number ");
        sb = (StringBuilder) appendString.invokeExact(sb, "is ");
        sb = (StringBuilder) appendInt.invokeExact(sb, 42);
        assertEquals("The number is 42", sb.toString());
    }

    public void testCantConvert() throws NoMatchingConverterException {
        Converter converter = Converters.defaultConverters.find(Date.class, String.class);
        assertEquals(ConversionRank.NEEDS_CONVERSION, converter.getRank());
        assertNull(converter.getConverter());
    }

    // Can't convert from any primitive to another wrapper
    @Test
    public void testWrongPrimitive() throws NoMatchingConverterException {
        Converter converter = Converters.defaultConverters.find(long.class, Integer.class);
        assertFalse(converter.isConvertable());
    }

    // But can convert from any wrapper to some primitive as long as there's an xxxValue() method
    @Test
    public void testConvertablePrimitive() throws Throwable {
        Converter c = Converters.defaultConverters.find(Integer.class, long.class);
        long val = (long) c.getConverter().invokeExact((Integer) 12345);
        assertEquals(12345L, val);
    }

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
    public void testConvertType() throws Throwable {
        MethodHandle concat = lookup().findStatic(ConvertersTest.class, "concat", methodType(
                String.class, String.class, long.class, Date.class, boolean.class));
        MethodHandle different = Converters.convertTo(concat, Converters.defaultConverters,
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
    public void testLessArguments() throws Throwable {
        MethodHandle concat = lookup().findStatic(ConvertersTest.class, "concat", methodType(
                String.class, String.class, long.class, Date.class, boolean.class));
        Date now = new Date();
        MethodHandle lessArgs = Converters.convertTo(concat, Converters.defaultConverters,
                methodType(StringWrapper.class, TimeUnit.class), now, now, true);
        StringWrapper result = (StringWrapper) lessArgs.invokeExact(TimeUnit.HOURS);
        long ms = now.getTime();
        assertEquals("HOURS" + ms + " " + ms, result.val);

        lessArgs = Converters.convertTo(concat, Converters.defaultConverters,
                methodType(StringWrapper.class), ElementType.FIELD, null, null, null);
        result = (StringWrapper) lessArgs.invokeExact();
        assertEquals("FIELD0", result.val);
    }

    @Test
    public void testMoreArguments() throws Throwable {
        MethodHandle max = lookup().findStatic(Math.class, "max", methodType(int.class, int.class, int.class));
        MethodHandle more = Converters.convertTo(max, Converters.defaultConverters,
                methodType(String.class, Float.class, BigInteger.class, Date.class, TimeUnit.class));
        String result = (String) more.invokeExact((Float) 2.0f, new BigInteger("1000"), new Date(), TimeUnit.MICROSECONDS);
        assertEquals("1000", result);
    }
}
