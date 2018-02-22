package org.fiolino.common.reflection;

import org.fiolino.common.reflection.otherpackage.ClassFromOtherPackage;
import org.junit.jupiter.api.Test;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Created by Kuli on 6/16/2016.
 */
class MethodsVisitorTest {
    private void validate(MethodHandles.Lookup lookup, Class<?> type, String... expectedVisibleMethods) {
        final Set<String> exp = new HashSet<>(Arrays.asList(expectedVisibleMethods));
        Methods.visitAllMethods(lookup, type, null, (v, m, handleSupplier) -> {
            if (methodIsIgnored(m)) {
                return null;
            }
            String name = m.getName();
            assertTrue(exp.remove(name), () -> name + " should be expected to be visible");

            // Now check that MethodHandle can be created
            MethodHandle h = handleSupplier.get();
            assertNotNull(h);
            return null;
        });

        assertTrue(exp.isEmpty(), () -> "Methods expected to be visible but they aren't: " + exp);

        // Now check that other methods will not be visible
        Collections.addAll(exp, expectedVisibleMethods);
        Class<?> c = type;
        Set<String> visited = new HashSet<>();
        do {
            for (Method m : c.getDeclaredMethods()) {
                if (!visited.add(m.getName()) || methodIsIgnored(m)) {
                    continue;
                }
                try {
                    lookup.unreflect(m);
                } catch (IllegalAccessException ex) {
                    if (!exp.contains(m.getName())) {
                        // Then everything is fine
                        continue;
                    }
                    fail(m + " was expected to be visible.");
                }
                if (!exp.contains(m.getName())) {
                    fail(m + " is visible, this wasn't expected.");
                }
            }
            c = c.getSuperclass();
        } while (c != null && c != Object.class);
    }

    private boolean methodIsIgnored(Method m) {
        return m.getDeclaringClass() == Object.class || m.getReturnType() == MethodHandles.Lookup.class
                || m.getName().startsWith("$"); // Last one resorts to $jacocoInit()) and such
    }

    @Test
    void testWithPublicLookup() {
        MethodHandles.Lookup lookup = MethodHandles.publicLookup();
        validate(lookup, ClassFromSamePackage.class, "publicMethod");
        validate(lookup, ClassFromOtherPackage.class, "publicMethod");
        validate(lookup, OverwritingFromOtherPackage.class, "publicMethod", "publicMethod2");
    }

    @Test
    void testWithMyLookup() {
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        validate(lookup, ClassFromSamePackage.class, "publicMethod", "packageMethod", "protectedMethod");
        validate(lookup, ClassFromOtherPackage.class, "publicMethod");
        validate(lookup, OverwritingFromOtherPackage.class, "publicMethod", "publicMethod2", "packageMethod2",
                "protectedMethod", "protectedMethod2");
    }

    @Test
    void testWithLocalLookup() {
        MethodHandles.Lookup lookup = new LocalClass().lookup();
        validate(lookup, LocalClass.class, "publicMethod", "protectedMethod",
                "publicMethod2", "packageMethod2", "protectedMethod2", "privateMethod2");
        validate(lookup, ClassFromSamePackage.class, "publicMethod", "packageMethod", "protectedMethod");
        validate(lookup, ClassFromOtherPackage.class, "publicMethod", "protectedMethod");
    }

    @Test
    void testWithReducedLookup() {
        MethodHandles.Lookup lookup = new LocalClass().lookup().in(ClassFromSamePackage.class);
        validate(lookup, LocalClass.class, "publicMethod", "publicMethod2", "packageMethod2", "protectedMethod2");
        validate(lookup, ClassFromSamePackage.class, "publicMethod", "packageMethod", "protectedMethod");
        validate(lookup, ClassFromOtherPackage.class, "publicMethod");
    }

    @Test
    void testWithLookupOfSuperclass() {
        MethodHandles.Lookup lookup = new LocalClass().lookup().in(ClassFromOtherPackage.class);
        validate(lookup, LocalClass.class, "publicMethod", "publicMethod2");
        validate(lookup, ClassFromSamePackage.class, "publicMethod");
        validate(lookup, ClassFromOtherPackage.class, "publicMethod");
    }

    private static class PrivateClass {
        void publicMethod() {
        }

        void packageMethod() {
        }

        protected void protectedMethod() {
        }

        private void privateMethod() {
        }
    }

    @Test
    void testPrivateClass() {
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        validate(lookup, PrivateClass.class, "publicMethod", "protectedMethod", "packageMethod");
    }

    @Test
    void testPrivateClassWithInContext() {
        MethodHandles.Lookup lookup = MethodHandles.lookup().in(PrivateClass.class);
        validate(lookup, PrivateClass.class, "publicMethod", "protectedMethod", "packageMethod", "privateMethod");
    }

    @Test
    void testPrivateClassAndPublicLookup() {
        MethodHandles.Lookup lookup = MethodHandles.publicLookup();
        validate(lookup, PrivateClass.class);
    }

    @Test
    void testPrivateClassAndReducedLookup() {
        MethodHandles.Lookup lookup = MethodHandles.lookup().in(ClassFromSamePackage.class);
        validate(lookup, PrivateClass.class, "publicMethod", "protectedMethod", "packageMethod");
    }

    @Test
    void testPrivateClassAndEvenMoreReducedLookup() {
        MethodHandles.Lookup lookup = MethodHandles.lookup().in(ClassFromOtherPackage.class);
        validate(lookup, PrivateClass.class);
    }

    @Test
    void testPackagePrivateClass() {
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        validate(lookup, PackagePrivateClass.class, "publicMethod", "protectedMethod", "packageMethod");
    }

    @Test
    void testPackagePrivateClassAndPublicLookup() {
        MethodHandles.Lookup lookup = MethodHandles.publicLookup();
        validate(lookup, PackagePrivateClass.class);
    }

    @Test
    void testPackagePrivateClassAndReducedLookup() {
        MethodHandles.Lookup lookup = MethodHandles.lookup().in(ClassFromSamePackage.class);
        validate(lookup, PackagePrivateClass.class, "publicMethod", "protectedMethod", "packageMethod");
    }

    @Test
    void testPackagePrivateClassAndEvenMoreReducedLookup() {
        MethodHandles.Lookup lookup = MethodHandles.lookup().in(ClassFromOtherPackage.class);
        validate(lookup, PackagePrivateClass.class);
    }

    static class A {
        void method() {
        }

        void another() {
        }
    }

    static class B extends A {
        @Override
        void method() {
            super.method();
        }
    }

    @Test
    void testOverriddenMethodOnlyOnce() {
        final AtomicReference<Method> foundAnother = new AtomicReference<>();

        Method foundMethod = Methods.visitAllMethods(MethodHandles.lookup(), B.class, null, (v, m, handleSupplier) -> {
            if (m.getName().equals("method")) {
                assertNull(v);
                return m;
            }
            if (m.getName().equals("another")) {
                Method alreadyThere = foundAnother.getAndSet(m);
                assertNull(alreadyThere);
            }
            return v;
        });

        assertNotNull(foundMethod);
        assertEquals(B.class, foundMethod.getDeclaringClass());
        Method m = foundAnother.get();
        assertNotNull(m);
        assertEquals(A.class, m.getDeclaringClass());
    }

    static class Uninstantiable {
        Uninstantiable() {
            fail("Can't instantiate");
        }

        static String staticMethod() {
            return "I'm static, yeah!";
        }

        void instanceMethod() {
            fail("Don't call me.");
        }
    }

    @Test
    void testStaticMethods() throws Throwable {
        MethodHandle h = Methods.visitMethodsWithStaticContext(
                MethodHandles.lookup(), Uninstantiable.class, Uninstantiable::new, null, (v, m, handleSupplier) -> {
                    if (m.getName().equals("staticMethod")) {
                        return handleSupplier.get();
                    }
                    return v;
                });

        assertNotNull(h);
        String val = (String) h.invokeExact();
        assertEquals("I'm static, yeah!", val);
    }

    static final AtomicBoolean wasInstantiated = new AtomicBoolean();

    static class Instantiable {
        Instantiable() {
            wasInstantiated.set(true);
        }

        static String staticMethod() {
            return "Static";
        }

        String instanceMethod() {
            return "Instance";
        }
    }

    @Test
    void testStaticContextOnInstances() throws Throwable {
        final AtomicReference<MethodHandle> staticRef = new AtomicReference<>();
        final AtomicReference<MethodHandle> instanceRef = new AtomicReference<>();

        Instantiable i = new Instantiable();
        wasInstantiated.set(false);
        Methods.visitMethodsWithStaticContext(MethodHandles.lookup(), i, null, (v, m, handleSupplier) -> {
            if (m.getName().equals("staticMethod")) {
                staticRef.set(handleSupplier.get());
            }
            if (m.getName().equals("instanceMethod")) {
                instanceRef.set(handleSupplier.get());
            }
            return null;
        });

        assertFalse(wasInstantiated.get(), "Wasn't instantiated because instance was already given.");
        MethodHandle h = staticRef.get();
        assertNotNull(h);
        String val = (String) h.invokeExact();
        assertEquals("Static", val);
        h = instanceRef.get();
        assertNotNull(h);
        val = (String) h.invokeExact();
        assertEquals("Instance", val);

        // Now check with given class
        instanceRef.set(null);
        staticRef.set(null);
        Methods.visitMethodsWithStaticContext(MethodHandles.lookup(), Instantiable.class, Instantiable::new, null, (v, m, handleSupplier) -> {
            if (m.getName().equals("staticMethod")) {
                staticRef.set(handleSupplier.get());
            }
            if (m.getName().equals("instanceMethod")) {
                instanceRef.set(handleSupplier.get());
            }
            return null;
        });

        assertTrue(wasInstantiated.get(), "Was instantiated because there is an instance method.");
        h = staticRef.get();
        assertNotNull(h);
        val = (String) h.invokeExact();
        assertEquals("Static", val);
        h = instanceRef.get();
        assertNotNull(h);
        val = (String) h.invokeExact();
        assertEquals("Instance", val);
    }

    @Test
    void testInstanceContext() throws Throwable {
        final AtomicReference<MethodHandle> staticRef = new AtomicReference<>();
        final AtomicReference<MethodHandle> instanceRef = new AtomicReference<>();

        wasInstantiated.set(false);
        Methods.visitMethodsWithInstanceContext(MethodHandles.lookup(), Instantiable.class, null, (v, m, handleSupplier) -> {
            if (m.getName().equals("staticMethod")) {
                staticRef.set(handleSupplier.get());
            }
            if (m.getName().equals("instanceMethod")) {
                instanceRef.set(handleSupplier.get());
            }
            return null;
        });

        assertFalse(wasInstantiated.get(), "Wasn't instantiated because instance is injected on every call.");
        MethodHandle h = staticRef.get();
        assertNotNull(h);
        // Even static methods now expect an instance
        String val = (String) h.invokeExact(new Instantiable());
        assertEquals("Static", val);
        h = instanceRef.get();
        assertNotNull(h);
        val = (String) h.invokeExact(new Instantiable());
        assertEquals("Instance", val);
    }
}
