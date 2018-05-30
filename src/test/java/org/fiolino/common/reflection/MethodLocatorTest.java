package org.fiolino.common.reflection;

import org.junit.jupiter.api.Test;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static java.lang.invoke.MethodHandles.lookup;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Created by Michael Kuhlmann on 15.12.2015.
 */
class MethodLocatorTest {

    private static final MethodHandles.Lookup LOOKUP = lookup();

    @SuppressWarnings("unused")
    private static class BeanWithBooleanValues {
        boolean isWithIsInGetterAndSetter;
        boolean isWithoutIsInGetterAndSetter;

        boolean getIsWithIsInGetterAndSetter() {
            return isWithIsInGetterAndSetter;
        }

        void setIsWithIsInGetterAndSetter(boolean withIsInGetterAndSetter) {
            isWithIsInGetterAndSetter = withIsInGetterAndSetter;
        }

        boolean isWithoutIsInGetterAndSetter() {
            return isWithoutIsInGetterAndSetter;
        }

        void setWithoutIsInGetterAndSetter(boolean withoutIsInGetterAndSetter) {
            isWithoutIsInGetterAndSetter = withoutIsInGetterAndSetter;
        }
    }

    @Test
    void testFindBooleanWithGetAndIs() throws Throwable {
        Field field = BeanWithBooleanValues.class.getDeclaredField("isWithIsInGetterAndSetter");
        MethodHandle getter = MethodLocator.findGetter(LOOKUP, field);
        assertNotNull(getter);
        MethodHandle setter = MethodLocator.findSetter(LOOKUP, field);
        assertNotNull(setter);

        BeanWithBooleanValues bean = new BeanWithBooleanValues();
        setter.invokeExact(bean, true);
        assertTrue((boolean) getter.invokeExact(bean));
        assertFalse(bean.isWithoutIsInGetterAndSetter());
    }

    @Test
    void testFindBooleanWithoutGetAndIs() throws Throwable {
        Field field = BeanWithBooleanValues.class.getDeclaredField("isWithoutIsInGetterAndSetter");
        MethodHandle getter = MethodLocator.findGetter(LOOKUP, field);
        assertNotNull(getter);
        MethodHandle setter = MethodLocator.findSetter(LOOKUP, field);
        assertNotNull(setter);

        BeanWithBooleanValues bean = new BeanWithBooleanValues();
        setter.invokeExact(bean, true);
        assertTrue((boolean) getter.invokeExact(bean));
        assertFalse(bean.getIsWithIsInGetterAndSetter());
    }

    @Test
    void testFindViaConsumer() throws Throwable {
        @SuppressWarnings("unchecked")
        MethodHandle handle = MethodLocator.findUsing(List.class, l -> l.add(null));
        assertNotNull(handle);
        List<String> testList = new ArrayList<>();
        boolean added = (boolean) handle.invokeExact(testList, (Object) "Hello World");
        assertTrue(added);
        assertEquals(1, testList.size());
        assertEquals("Hello World", testList.get(0));
    }

    @Test
    void testBindUsing() throws Throwable {
        List<String> testList = new ArrayList<>();
        MethodHandle handle = MethodLocator.bindUsing(testList, l -> l.add(null));
        boolean added = (boolean) handle.invokeExact((Object) "Hello World");
        assertTrue(added);
        assertEquals(1, testList.size());
        assertEquals("Hello World", testList.get(0));
    }

    private static class Prototype {
        @MethodFinder
        @SuppressWarnings("unused")
        int createAddHandle(List<Object> someList) {
            someList.add(new Object());
            fail("Should never reach here!");
            return 0;
        }

        @ExecuteDirect
        @SuppressWarnings("unused")
        int getDoubledSize(List<?> someList) {
            return someList.size() * 2;
        }
    }

    @Test
    void testFindUsingPrototype() throws Throwable {
        final MethodHandle[] handles = new MethodHandle[2];
        MethodLocator.findUsing(LOOKUP, new Prototype(), null, (v, l, m, handleSupplier) -> {
            handles[m.getName().equals("createAddHandle") ? 0 : 1] = handleSupplier.get();
            return null;
        });
        assertNotNull(handles[0]);
        assertNotNull(handles[1]);
        List<String> testList = new ArrayList<>();
        boolean added = (boolean) handles[0].invokeExact(testList, (Object) "Hello World");
        assertTrue(added);
        assertEquals(1, testList.size());
        assertEquals("Hello World", testList.get(0));
        int doubledSize = (int) handles[1].invokeExact(testList);
        assertEquals(2, doubledSize);
    }
}
