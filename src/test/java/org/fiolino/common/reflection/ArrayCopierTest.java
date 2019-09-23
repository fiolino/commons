package org.fiolino.common.reflection;

import org.fiolino.common.ioc.FactoryFinder;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Array;
import java.util.Date;
import java.util.function.IntFunction;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArrayCopierTest {

    @Test
    void testArrayCopierObject() {
        ArrayCopier<Object[]> objectCopier = ArrayCopier.createFor(Object[].class);
        Object[] arr = {"Hello", "Goodbye"};
        Object[] arr1 = objectCopier.copyOf(arr, 1);
        assertArrayEquals(new Object[] {"Hello"}, arr1);
        Object[] arr4 = objectCopier.copyOf(arr, 4);
        assertArrayEquals(new Object[] {"Hello", "Goodbye", null, null}, arr4);
    }

    @Test
    void testArrayCopierString() {
        ArrayCopier<String[]> objectCopier = ArrayCopier.createFor(String[].class);
        String[] arr = {"Hello", "Goodbye"};
        String[] arr1 = objectCopier.copyOf(arr, 1);
        assertArrayEquals(new String[] {"Hello"}, arr1);
        String[] arr4 = objectCopier.copyOf(arr, 4);
        assertArrayEquals(new String[] {"Hello", "Goodbye", null, null}, arr4);
    }

    @Test
    void testArrayCopierInt() {
        ArrayCopier<int[]> objectCopier = ArrayCopier.createFor(int[].class);
        int[] arr = {1, 2};
        int[] arr1 = objectCopier.copyOf(arr, 1);
        assertArrayEquals(new int[] {1}, arr1);
        int[] arr4 = objectCopier.copyOf(arr, 4);
        assertArrayEquals(new int[] {1, 2, 0, 0}, arr4);
    }

    @Test
    void testArrayCopierBooleanArray() {
        ArrayCopier<boolean[][]> objectCopier = ArrayCopier.createFor(boolean[][].class);
        boolean[][] arr = { {false}, {true, false} };
        boolean[][] arr1 = objectCopier.copyOf(arr, 1);
        assertArrayEquals(new boolean[][] { {false} }, arr1);
        boolean[][] arr4 = objectCopier.copyOf(arr, 4);
        assertArrayEquals(new boolean[][] { {false}, {true, false}, null, null }, arr4);
    }

    @Test
    void testObjectArrayFactory() {
        testArrayFactory(Object.class, Object[]::new);
        testArrayFactory(String.class, String[]::new);
        testArrayFactory(FactoryFinder.class, FactoryFinder[]::new);
    }

    @Test
    void testPrimitiveArrayFactory() {
        testArrayFactory(int.class, int[]::new);
        testArrayFactory(boolean.class, boolean[]::new);
        testArrayFactory(double.class, double[]::new);
        testArrayFactory(char.class, char[]::new);
        testArrayFactory(String.class, String[]::new);
        testArrayFactory(Date[][].class, Date[][][]::new);
    }

    @Test
    void testDirectFactory() throws Throwable {
        @SuppressWarnings("unchecked")
        IntFunction<float[]> f = (IntFunction<float[]>) Factory.lambdaFactory.invokeExact(float.class);
        float[] array = f.apply(5);
        assertArrayEquals(new float[5], array);
    }

    private <A> void testArrayFactory(Class<?> type, IntFunction<A> directFactory) {
        IntFunction<A> factory = ArrayCopier.factoryForArrayOfType(type);
        for (int i=0; i < 100; i++) {
            A result = factory.apply(i);
            A expected = directFactory.apply(i);
            assertTrue(result.getClass().isArray());
            assertEquals(i, Array.getLength(result));
            for (int j=0; j < i; j++) {
                assertEquals(Array.get(expected, j), Array.get(result, j));
            }
        }
    }
}
