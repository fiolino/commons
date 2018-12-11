package org.fiolino.common.reflection;

import org.junit.jupiter.api.Test;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.function.UnaryOperator;

import static java.lang.invoke.MethodHandles.lookup;
import static java.lang.invoke.MethodHandles.publicLookup;
import static java.lang.invoke.MethodType.methodType;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LambdaFactoryTest {

    @Test
    void testFunction() throws NoSuchMethodException, IllegalAccessException {
        MethodHandle h = publicLookup().findVirtual(String.class, "concat", methodType(String.class, String.class));
        LambdaFactory<UnaryOperator<String>> factory = LambdaFactory.createFor(lookup(), h, UnaryOperator.class);
        UnaryOperator<String> op = factory.create("Hello ");

        String result = op.apply("Melissa");
        assertEquals("Hello Melissa", result);
    }
}
