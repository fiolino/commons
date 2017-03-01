package org.fiolino.common.var;

import org.fiolino.data.annotation.Debugged;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Created by kuli on 29.11.16.
 */
public class ObjectToStringConverterTest {

    @Test
    public void testSimpleString() {
        String test = "Hello World!";
        String fullPrint = new ObjectToStringConverter(2, 2).append(new StringBuilder(), test).toString();
        assertEquals("\"" + test + "\"", fullPrint);
    }

    public static class A {
        String string;
        int primitive;
    }

    @Test
    public void testNormalFields() {
        A a = new A();
        a.string = "Hello World";
        a.primitive = 42;
        String fullPrint = new ObjectToStringConverter(2, 2).append(new StringBuilder(), a).toString();

        assertEquals(A.class.getName() + ": \n  string=\"Hello World\";\n  primitive=42;\n", fullPrint);
    }

    public static class B extends A {
        @Debugged(value = true, printNull = true)
        Object isNull;

        @Debugged(false)
        String ignored = "Ignored";
    }

    @Test
    public void testAnnotatedFields() {
        B b = new B();
        b.string = "Hello World";
        b.primitive = 42;
        String fullPrint = new ObjectToStringConverter(2, 2).append(new StringBuilder(), b).toString();

        assertEquals(B.class.getName() + ": \n  isNull=<null>;\n  string=\"Hello World\";\n  primitive=42;\n", fullPrint);
    }

}
