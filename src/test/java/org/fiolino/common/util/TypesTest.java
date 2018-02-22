package org.fiolino.common.util;

import org.junit.jupiter.api.Test;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Michael Kuhlmann <michael@kuhlmann.org>
 */
@SuppressWarnings("unused")
class TypesTest {

    private static class NoException extends ThreadDeath {
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface TestWithParameter {
        Class<? extends Throwable> expected() default NoException.class;
    }

    @Test
    void testAsPrimitive() {
        assertEquals(int.class, Types.asPrimitive(Integer.class));
        assertEquals(int.class, Types.asPrimitive(int.class));
        assertEquals(void.class, Types.asPrimitive(Void.class));
        assertNull(Types.asPrimitive(Object.class));
    }

    @Test
    void testToWrapper() {
        assertEquals(Integer.class, Types.toWrapper(int.class));
        assertEquals(Integer.class, Types.toWrapper(Integer.class));
        assertEquals(Void.class, Types.toWrapper(void.class));
        assertEquals(Object.class, Types.toWrapper(Object.class));
    }

    @Test
    void testAssignable() {
        assertTrue(Types.isAssignableFrom(Map.class, HashMap.class));
        assertFalse(Types.isAssignableFrom(HashMap.class, Map.class));
        assertTrue(Types.isAssignableFrom(Number.class, int.class));
        assertTrue(Types.isAssignableFrom(Number.class, double.class));
        assertTrue(Types.isAssignableFrom(Object.class, boolean.class));
        assertFalse(Types.isAssignableFrom(Number.class, boolean.class));
        assertTrue(Types.isAssignableFrom(int.class, Integer.class));
        assertFalse(Types.isAssignableFrom(int.class, Number.class));
    }

    @Test
    void testWithGenerics() throws Throwable {
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        for (Method m : getClass().getDeclaredMethods()) {
            TestWithParameter testAnno = m.getAnnotation(TestWithParameter.class);
            if (testAnno == null) {
                continue;
            }
            Type[] params = m.getGenericParameterTypes();
            MethodHandle mh = lookup.unreflect(m);
            try {
                switch (params.length) {
                    case 0:
                        mh.invokeExact(this);
                        break;
                    case 1:
                        mh.invoke(this, null);
                        break;
                    case 2:
                        if (params[0].equals(Type.class)) {
                            mh.invoke(this, params[1], null);
                        } else if (params[1].equals(Type.class)) {
                            mh.invoke(this, null, params[0]);
                        } else {
                            throw new IllegalStateException("Expected to be one type parameter in " + m);
                        }
                        break;
                    default:
                        throw new IllegalStateException("More than two parameters in " + m);
                }
            } catch (Throwable ex) {
                if (testAnno.expected().isInstance(ex)) {
                    // Then everything is fine!
                    continue;
                } else {
                    throw ex;
                }
            }
            if (testAnno.expected() != NoException.class) {
                fail("Expected " + testAnno.expected() + " in " + m);
            }
        }
    }

    @TestWithParameter
    @SuppressWarnings("rawtypes")
    void testRawType1(Map unused, Type t) {
        assertEquals(Map.class, Types.erasureOf(t));
    }

    @TestWithParameter
    void testRawType2(Map<? extends List<?>, ?> unused, Type t) {
        assertEquals(Map.class, Types.erasureOf(t));
    }

    @TestWithParameter
    <T> void testRawType3(ThreadLocal<T> unused, Type t) {
        assertEquals(ThreadLocal.class, Types.erasureOf(t));
    }

    @TestWithParameter(expected = IllegalArgumentException.class)
    <T> void testRawType4(T unused, Type t) {
        Types.erasureOf(t);
    }

    @TestWithParameter
    void testRawType5(List<? extends Map<?, ?>> unused, Type t) {
        Type argument = ((ParameterizedType) t).getActualTypeArguments()[0];
        assertEquals(Map.class, Types.erasureOf(argument, Types.Bounded.UPPER));
    }

    @TestWithParameter
    void testRawType6(List<? super Map<?, ?>> unused, Type t) {
        Type argument = ((ParameterizedType) t).getActualTypeArguments()[0];
        assertEquals(Map.class, Types.erasureOf(argument, Types.Bounded.LOWER));
    }

    @TestWithParameter(expected = IllegalArgumentException.class)
    void testRawType7(List<? extends Map<?, ?>> unused, Type t) {
        Type argument = ((ParameterizedType) t).getActualTypeArguments()[0];
        Types.erasureOf(argument, Types.Bounded.LOWER);
    }

    @TestWithParameter
    void testSimpleArguments(Map<?, String> unused, Type t) {
        assertEquals(String.class, Types.erasedArgument(t, Map.class, 1, Types.Bounded.EXACT));
    }

    @TestWithParameter(expected = IllegalArgumentException.class)
    void testOutOfBounds(Map<?, String> unused, Type t) {
        Types.erasedArgument(t, Map.class, 2, Types.Bounded.EXACT);
    }

    @TestWithParameter(expected = IllegalArgumentException.class)
    void testRawType(Map unused, Type t) {
        Types.erasedArgument(t, Map.class, 0, Types.Bounded.EXACT);
    }

    private interface SwapParameters<A, B> extends Map<B, A> {
    }

    @TestWithParameter
    void testSwappedArguments(SwapParameters<Integer, String> unused, Type t) {
        assertEquals(Integer.class, Types.erasedArgument(t, Map.class, 1, Types.Bounded.EXACT));
    }

    private interface FixedParameters extends SwapParameters<Integer, String> {
    }

    @TestWithParameter
    void testFixedArguments(FixedParameters unused, Type t) {
        assertEquals(Integer.class, Types.erasedArgument(t, Map.class, 1, Types.Bounded.EXACT));
        assertEquals(String.class, Types.erasedArgument(t, SwapParameters.class, 1, Types.Bounded.EXACT));
    }

    private interface OneParameter<A extends List<?>> extends Map<Date, A> {
    }

    @TestWithParameter
    void testOneArgument(OneParameter<?> unused, Type t) {
        assertEquals(Date.class, Types.erasedArgument(t, Map.class, 0, Types.Bounded.EXACT));
        assertEquals(List.class, Types.erasedArgument(t, Map.class, 1, Types.Bounded.UPPER));
    }
}
