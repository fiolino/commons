package org.fiolino.common.reflection;

import org.fiolino.common.reflection.otherpackage.ClassFromOtherPackage;
import org.junit.jupiter.api.Test;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static java.lang.invoke.MethodHandles.lookup;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Created by Kuli on 6/16/2016.
 */
class MethodsVisitorTest {
    private void validate(MethodHandles.Lookup lookup, Object instance, String... expectedVisibleMethods) {
        Set<String> exp = new HashSet<>(Arrays.asList(expectedVisibleMethods));
        MethodLocator methodLocator = MethodLocator.forLocal(lookup, instance.getClass());

        methodLocator.methods().filter(info -> methodIsEligible(info.getMethod())).forEach(info -> {
            String name = info.getName();
            assertTrue(exp.remove(name), () -> name + " should be expected to be visible");

            // Now check that MethodHandle can be created
            MethodHandle h = info.getHandle();
            assertNotNull(h);

            if (!Modifier.isProtected(info.getModifiers()) || lookup.lookupClass().isInstance(instance)) {
                // Otherwise somehow the instance of a protected method must be of the lookup type
                Runnable r = info.lambdafy(Runnable.class, () -> instance);
                r.run();
            }
        });

        assertTrue(exp.isEmpty(), () -> "Methods expected to be visible but they aren't: " + exp);

        // Now check that other methods will not be visible
        Collections.addAll(exp, expectedVisibleMethods);
        Class<?> c = instance.getClass();
        Set<String> visited = new HashSet<>();
        do {
            for (Method m : c.getDeclaredMethods()) {
                if (!visited.add(m.getName()) || !methodIsEligible(m)) {
                    continue;
                }
                MethodHandle handle;
                try {
                    handle = lookup.unreflect(m);
                } catch (IllegalAccessException ex) {
                    if (!exp.contains(m.getName())) {
                        // Then everything is fine
                        continue;
                    }
                    fail(m + " was expected to be visible.");
                    throw new AssertionError();
                }
                if (!exp.contains(m.getName())) {
                    fail(m + " is visible, this wasn't expected.");
                }
                if (!Modifier.isProtected(m.getModifiers()) || lookup.lookupClass().isInstance(instance)) {
                    // Otherwise somehow the instance of a protected method must be of the lookup type
                    @SuppressWarnings("unchecked")
                    Consumer<Object> consumer = Methods.lambdafy(lookup, handle, Consumer.class);
                    consumer.accept(instance);

                    handle = handle.bindTo(instance);
                    try {
                        handle.invoke();
                    } catch (Throwable t) {
                        fail("Cannot call " + m, t);
                    }
                }
            }
            c = c.getSuperclass();
        } while (c != null && c != Object.class);
    }

    private boolean methodIsEligible(Method m) {
        return m.getDeclaringClass() != Object.class && m.getReturnType() == void.class
                && m.getParameterCount() == 0
                && !m.getName().startsWith("$"); // Last one resorts to $jacocoInit() and such
    }

    @Test
    void testWithPublicLookup() {
        MethodHandles.Lookup lookup = MethodHandles.publicLookup();
        validate(lookup, new ClassFromSamePackage(), "publicMethod");
        validate(lookup, new ClassFromOtherPackage(), "publicMethod");
        validate(lookup, new OverwritingFromOtherPackage(), "publicMethod", "publicMethod2");
    }

    @Test
    void testWithMyLookup() {
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        validate(lookup, new ClassFromSamePackage(), "publicMethod", "packageMethod", "protectedMethod");
        validate(lookup, new ClassFromOtherPackage(), "publicMethod");
        validate(lookup, new OverwritingFromOtherPackage(), "publicMethod", "publicMethod2", "packageMethod2",
                "protectedMethod", "protectedMethod2");
    }

    @Test
    void testWithLocalLookup() {
        MethodHandles.Lookup lookup = new LocalClass().lookup();
        validate(lookup, new LocalClass(), "publicMethod", "protectedMethod",
                "publicMethod2", "packageMethod2", "protectedMethod2", "privateMethod2");
        validate(lookup, new ClassFromSamePackage(), "publicMethod", "packageMethod", "protectedMethod");
        validate(lookup, new ClassFromOtherPackage(), "publicMethod", "protectedMethod");
    }

    @Test
    void testWithReducedLookup() {
        MethodHandles.Lookup lookup = new LocalClass().lookup().in(ClassFromSamePackage.class);
        validate(lookup, new LocalClass(), "publicMethod", "publicMethod2", "packageMethod2", "protectedMethod2");
        validate(lookup, new ClassFromSamePackage(), "publicMethod", "packageMethod", "protectedMethod");
        validate(lookup, new ClassFromOtherPackage(), "publicMethod");
    }

    @Test
    void testWithLookupOfSuperclass() {
        MethodHandles.Lookup lookup = new LocalClass().lookup().in(ClassFromOtherPackage.class);
        validate(lookup, new LocalClass(), "publicMethod", "publicMethod2");
        validate(lookup, new ClassFromSamePackage(), "publicMethod");
        validate(lookup, new ClassFromOtherPackage(), "publicMethod");
    }

    private static class PrivateClass {
        public void publicMethod() {
        }

        void packageMethod() {
        }

        protected void protectedMethod() {
        }

        private void privateMethod() {
        }

        private static MethodHandles.Lookup lookup() {
            return MethodHandles.lookup();
        }
    }

    @Test
    void testPrivateClass() {
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        validate(lookup, new PrivateClass(), "publicMethod", "protectedMethod", "packageMethod", "privateMethod");
    }

    @Test
    void testPrivateClassWithInContext() {
        MethodHandles.Lookup lookup = MethodHandles.lookup().in(PrivateClass.class);
        validate(lookup, new PrivateClass(), "publicMethod", "protectedMethod", "packageMethod", "privateMethod");
    }

    @Test
    void testPrivateClassWithOwnLookup() {
        MethodHandles.Lookup lookup = PrivateClass.lookup();
        validate(lookup, new PrivateClass(), "publicMethod", "protectedMethod", "packageMethod", "privateMethod");
    }

    @Test
    void testPrivateClassAndPublicLookup() {
        MethodHandles.Lookup lookup = MethodHandles.publicLookup();
        validate(lookup, new PrivateClass());
    }

    @Test
    void testPrivateClassAndReducedLookup() {
        MethodHandles.Lookup lookup = MethodHandles.lookup().in(ClassFromSamePackage.class);
        validate(lookup, new PrivateClass(), "publicMethod", "protectedMethod", "packageMethod");
    }

    @Test
    void testPrivateClassAndEvenMoreReducedLookup() {
        MethodHandles.Lookup lookup = MethodHandles.lookup().in(ClassFromOtherPackage.class);
        validate(lookup, new PrivateClass());
    }

    @Test
    void testPackagePrivateClass() {
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        validate(lookup, new PackagePrivateClass(), "publicMethod", "protectedMethod", "packageMethod");
    }

    @Test
    void testPackagePrivateClassAndPublicLookup() {
        MethodHandles.Lookup lookup = MethodHandles.publicLookup();
        validate(lookup, new PackagePrivateClass());
    }

    @Test
    void testPackagePrivateClassAndReducedLookup() {
        MethodHandles.Lookup lookup = MethodHandles.lookup().in(ClassFromSamePackage.class);
        validate(lookup, new PackagePrivateClass(), "publicMethod", "protectedMethod", "packageMethod");
    }

    @Test
    void testPackagePrivateClassAndEvenMoreReducedLookup() {
        MethodHandles.Lookup lookup = MethodHandles.lookup().in(ClassFromOtherPackage.class);
        validate(lookup, new PackagePrivateClass());
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

        Method foundMethod = MethodLocator.forLocal(MethodHandles.lookup(), B.class).methods().map(MethodInfo::getMethod).reduce(null, (v, m) -> {
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

        static String staticMethod(String name) {
            return "Hello " + name + ", I'm static, yeah!";
        }

        void instanceMethod() {
            fail("Don't call me.");
        }
    }

    @Test
    void testStaticMethods() throws Throwable {
        MethodLocator locator = MethodLocator.forLocal(lookup(), Uninstantiable.class);
        MethodHandle h = locator.methods()
                .filter(info -> info.getName().equals("staticMethod"))
                .map(info -> info.getStaticHandle(Uninstantiable::new))
                .findFirst().orElseThrow();

        assertNotNull(h);
        String val = (String) h.invokeExact("Lisa");
        assertEquals("Hello Lisa, I'm static, yeah!", val);

        @SuppressWarnings("unchecked")
        Supplier<String> supplier = locator.methods()
                .filter(info -> info.getName().equals("staticMethod"))
                .map(info -> info.lambdafy(Supplier.class, Uninstantiable::new, "Monica"))
                .findFirst().orElseThrow();
        assertTrue(Methods.wasLambdafiedDirect(supplier));
        val = supplier.get();
        assertEquals("Hello Monica, I'm static, yeah!", val);
    }
}
