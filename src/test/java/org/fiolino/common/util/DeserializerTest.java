package org.fiolino.common.util;

import org.fiolino.common.ioc.Instantiator;
import org.fiolino.common.reflection.Methods;
import org.junit.Test;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import static java.lang.invoke.MethodHandles.lookup;
import static java.lang.invoke.MethodType.methodType;
import static org.junit.Assert.*;

/**
 * Created by Michael Kuhlmann on 25.01.2016.
 */
public class DeserializerTest {

    private MethodHandle create(Class<?> type, String... fieldNames) throws NoSuchFieldException, IllegalAccessException,
            NoSuchMethodException {
        MethodHandle constructor = lookup().findConstructor(type, methodType(void.class));
        Deserializer d = new Deserializer(constructor);
        addFieldsTo(d, type, fieldNames);
        return d.createDeserializer();
    }

    private void addFieldsTo(Deserializer d, Class<?> type, String... fieldNames) throws NoSuchFieldException {
        int index = 0;
        for (String f : fieldNames) {
            if (f == null) {
                index++;
            } else {
                Field field;
                try {
                    field = type.getDeclaredField(f);
                } catch (NoSuchFieldException ex) {
                    field = type.getSuperclass().getDeclaredField(f);
                }
                MethodHandle setter = Methods.findSetter(lookup(), field);
                d.setField(setter, field.getGenericType(), index++, field::getName);
            }
        }
    }

    @Test
    public void testClassA() throws Throwable {
        MethodHandle factory = create(A.class, "integerValue", "string", "intValue", "enumValue");
        A a = (A) factory.invokeExact("500:\"Hello World!\":222:DAYS");
        assertEquals((Integer) 500, a.getIntegerValue());
        assertEquals("Hello World!", a.getString());
        assertEquals(222, a.getIntValue());
        assertEquals(TimeUnit.DAYS, a.getEnumValue());
    }

    @Test
    public void testClassAUnquoted() throws Throwable {
        MethodHandle factory = create(A.class, "integerValue", "string", "intValue", "enumValue");
        A a = (A) factory.invokeExact("500:Hello World!:222:DAYS");
        assertEquals((Integer) 500, a.getIntegerValue());
        assertEquals("Hello World!", a.getString());
        assertEquals(222, a.getIntValue());
        assertEquals(TimeUnit.DAYS, a.getEnumValue());
    }

    @Test
    public void testClassAWithEmptyValues() throws Throwable {
        MethodHandle factory = create(A.class, "integerValue", "string", "intValue", "enumValue");
        A a = (A) factory.invokeExact(":::");
        assertNull(a.getIntegerValue());
        assertNull(a.getString());
        assertEquals(0, a.getIntValue());
        assertNull(a.getEnumValue());
    }

    public static class AWithDefault extends A {
        public AWithDefault() {
            setIntegerValue(1);
            setIntValue(2);
            setEnumValue(TimeUnit.HOURS);
            setString("No string");
        }
    }

    @Test
    public void testClassAWithDefaultValues() throws Throwable {
        MethodHandle factory = create(AWithDefault.class, "integerValue", "string", "intValue", "enumValue");
        A a = (AWithDefault) factory.invokeExact(":::");
        assertEquals((Integer) 1, a.getIntegerValue());
        assertEquals("No string", a.getString());
        assertEquals(2, a.getIntValue());
        assertEquals(TimeUnit.HOURS, a.getEnumValue());
    }

    @Test
    public void testClassAWithIgnoredValues() throws Throwable {
        MethodHandle factory = create(A.class, null, null, "integerValue", null, "string", "intValue", null, "enumValue", null);
        A a = (A) factory.invokeExact("::500::\"Hello World!\":222::DAYS:");
        assertEquals((Integer) 500, a.getIntegerValue());
        assertEquals("Hello World!", a.getString());
        assertEquals(222, a.getIntValue());
        assertEquals(TimeUnit.DAYS, a.getEnumValue());
    }

    @Test
    public void testClassB() throws Throwable {
        MethodHandle factory = create(B.class, null, null, "integerValue", null, "string", "intValue", null, "enumValue", null, "intList", "enumList", "doubleValue", null, "longValue");
        B b = (B) factory.invokeExact("::500::\"Hello World!\":222::DAYS::5,,7,-1,-100,155:SECONDS,MILLISECONDS,,,,:::5131358468514531553");
        assertEquals((Integer) 500, b.getIntegerValue());
        assertEquals("Hello World!", b.getString());
        assertEquals(222, b.getIntValue());
        assertEquals(TimeUnit.DAYS, b.getEnumValue());
        assertEquals(Arrays.<Integer>asList(5, 7, -1, -100, 155), b.getIntList());
        assertEquals(Arrays.asList(TimeUnit.SECONDS, TimeUnit.MILLISECONDS), b.getEnumList());
        assertNull(b.getDoubleValue());
        assertEquals(5131358468514531553L, b.getLongValue());
    }

    @Test
    public void testClassBWithEmptyList() throws Throwable {
        MethodHandle factory = create(B.class, "intList");
        B b = (B) factory.invokeExact("::");
        assertNull(b.getIntList());
    }

    //@Test
    public void testBuilder() throws Throwable {
        DeserializerBuilder builder = new DeserializerBuilder(Instantiator.getDefault());
        MethodHandle factory = builder.getDeserializer(B.class);
        B b = (B) factory.invokeExact("Hello World!:222:500:DAYS:9999999999999999:50.9:1,2,3:MILLISECONDS,NANOSECONDS");
        assertEquals((Integer) 500, b.getIntegerValue());
        assertEquals("Hello World!", b.getString());
        assertEquals(222, b.getIntValue());
        assertEquals(TimeUnit.DAYS, b.getEnumValue());
        assertEquals(Arrays.<Integer>asList(1, 2, 3), b.getIntList());
        assertEquals(Arrays.asList(TimeUnit.MILLISECONDS, TimeUnit.NANOSECONDS), b.getEnumList());
        assertEquals((Double) 50.9, b.getDoubleValue());
        assertEquals(9999999999999999L, b.getLongValue());

        MethodHandle factory2 = builder.getDeserializer(B.class);
        assertTrue(factory == factory2);
    }

    //@Test
    public void testContainer() throws Throwable {
        DeserializerBuilder b = new DeserializerBuilder(Instantiator.getDefault());
        MethodHandle factory = b.getDeserializer(C.class);
        C c = (C) factory.invokeExact("My name:(\"Hello World!\":222:500:DAYS):Some text");
        assertEquals("My name", c.getName());
        A a = c.getA();
        assertEquals((Integer) 500, a.getIntegerValue());
        assertEquals("Hello World!", a.getString());
        assertEquals(222, a.getIntValue());
        assertEquals(TimeUnit.DAYS, a.getEnumValue());

        assertEquals("Some text", c.getText());
    }

    @Test
    public void testContainer2() throws Throwable {
        Deserializer forA = new Deserializer(Instantiator.getDefault().findProviderHandle(A.class));
        addFieldsTo(forA, A.class, "intValue", "string");
        Deserializer forC = new Deserializer(Instantiator.getDefault().findProviderHandle(C.class));
        addFieldsTo(forC, C.class, "name", "text");
        forC.setEmbeddedField(Methods.findSetter(lookup(), C.class, "a", A.class), forA.createDeserializer(), 2, () -> "a");
        MethodHandle factory = forC.createDeserializer();

        C c = (C) factory.invokeExact("My name:\"some text\":(425:\"Hello ::: World!\")");
        assertEquals("My name", c.getName());
        A a = c.getA();
        assertEquals("Hello ::: World!", a.getString());
        assertEquals(425, a.getIntValue());

        assertEquals("some text", c.getText());
    }

    @Test
    public void testContainerWithParenthesis() throws Throwable {
        Deserializer forA = new Deserializer(Instantiator.getDefault().findProviderHandle(A.class));
        addFieldsTo(forA, A.class, "intValue", "string");
        Deserializer forC = new Deserializer(Instantiator.getDefault().findProviderHandle(C.class));
        addFieldsTo(forC, C.class, "name", "text");
        forC.setEmbeddedField(Methods.findSetter(lookup(), C.class, "a", A.class), forA.createDeserializer(), 2, () -> "a");
        MethodHandle factory = forC.createDeserializer();

        C c = (C) factory.invokeExact("My (name):\"(some text)\":(425:\"With (some) \\\"parenthesises\\\"\")");
        assertEquals("My (name)", c.getName());
        A a = c.getA();
        assertEquals("With (some) \"parenthesises\"", a.getString());
        assertEquals(425, a.getIntValue());

        assertEquals("(some text)", c.getText());
    }
}
