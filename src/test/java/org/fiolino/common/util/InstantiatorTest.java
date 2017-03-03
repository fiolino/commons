package org.fiolino.common.util;

import org.fiolino.common.ioc.PostProcessor;
import org.fiolino.data.annotation.Facet;
import org.junit.Test;

import javax.annotation.Nullable;
import javax.annotation.PostConstruct;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandleProxies;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Supplier;

import static java.lang.invoke.MethodHandles.lookup;
import static org.junit.Assert.*;

/**
 * Created by kuli on 08.12.15.
 */
public class InstantiatorTest {

    public static class Normal {
        boolean flag;
    }

    private void checkIsLambda(Object func) {
        assertFalse("Should be a lambda function", MethodHandleProxies.isWrapperInstance(func));
    }

    private void checkIsNotLambda(Object func) {
        assertTrue("Was expected to not be a lambda", MethodHandleProxies.isWrapperInstance(func));
    }

    @Test
    public void testNormal() {
        Supplier<Normal> instantiator = Instantiator.getDefault().createSupplierFor(Normal.class);
        checkIsLambda(instantiator);
        Normal result = instantiator.get();

        assertNotNull(result);
        assertFalse(result.flag);
    }

    public static class WithPostProcessor extends Normal implements PostProcessor {
        @Override
        public void postConstruct() {
            flag = true;
        }
    }

    @Test
    public void testPostProcessor() {
        Supplier<WithPostProcessor> instantiator = Instantiator.getDefault().createSupplierFor(WithPostProcessor.class);
        checkIsNotLambda(instantiator);
        Normal result = instantiator.get();

        assertNotNull(result);
        assertTrue(result.flag);
    }

    static class WithVoidPostConstruct {
        String content = "Initial";

        @PostConstruct
        void changeContent() {
            content = "Changed";
        }
    }

    @Test
    public void testPostConstructSupplier() {
        WithVoidPostConstruct sample = new WithVoidPostConstruct();
        assertEquals("Initial", sample.content);

        Instantiator i = Instantiator.forLookup(lookup());
        Supplier<WithVoidPostConstruct> supplier = i.createSupplierFor(WithVoidPostConstruct.class);
        checkIsNotLambda(supplier);
        sample = supplier.get();
        assertEquals("Changed", sample.content);
    }

    @Test
    public void testWithArgument() {
        Supplier<WithPostProcessor> instantiator = Instantiator.getDefault().createSupplierFor(WithPostProcessor.class);
        checkIsNotLambda(instantiator);
        Normal result = instantiator.get();

        assertNotNull(result);
        assertTrue(result.flag);
    }

    @Test
    public void testCorrectArgument() {
        Function<String, StringBuilder> instantiator = Instantiator.getDefault().createFunctionFor(StringBuilder.class, String.class);
        checkIsLambda(instantiator);
        StringBuilder result = instantiator.apply("Hello!");

        assertNotNull(result);
        assertEquals("Hello!", result.toString());
    }

    static class WithPostConstructAndConstructor extends WithVoidPostConstruct implements PostProcessor {
        int num;

        WithPostConstructAndConstructor(int num) {
            this.num = num;
        }

        @Override
        public void postConstruct() {
            num *= 2;
        }

        @PostConstruct
        void increase() {
            num++;
        }

        @PostConstruct
        void increaseAgain() {
            num += 2;
        }
    }

    @Test
    public void testPostConstructFunction() {
        Instantiator i = Instantiator.forLookup(lookup());
        Function<Integer, WithPostConstructAndConstructor> factory = i.createFunctionFor(WithPostConstructAndConstructor.class, int.class);
        checkIsNotLambda(factory);
        WithPostConstructAndConstructor sample = factory.apply(100);
        assertEquals("Changed", sample.content);
        assertEquals(203, sample.num);
    }

    static class ModifiedPostConstruct {
        static final ModifiedPostConstruct FALLBACK = new ModifiedPostConstruct("fallback");

        final String value;

        ModifiedPostConstruct(String value) {
            this.value = value;
        }

        @PostConstruct
        ModifiedPostConstruct check() {
            if (value.equals("hit")) {
                return FALLBACK;
            }
            return this;
        }
    }

    @Test
    public void testModifiedPostConstruct() {
        Instantiator i = Instantiator.forLookup(lookup());
        Function<String, ModifiedPostConstruct> factory = i.createFunctionFor(ModifiedPostConstruct.class, String.class);
        checkIsNotLambda(factory);
        ModifiedPostConstruct sample = factory.apply("innocent");
        assertEquals("innocent", sample.value);
        assertNotEquals(ModifiedPostConstruct.FALLBACK, sample);

        sample = factory.apply("hit");
        assertEquals("fallback", sample.value);
        assertEquals(ModifiedPostConstruct.FALLBACK, sample);
    }

    static class ModifiedPostConstructWithStatic extends ModifiedPostConstruct {
        ModifiedPostConstructWithStatic(String value) {
            super(value);
        }

        @PostConstruct
        static ModifiedPostConstructWithStatic secondCheck(Object couldBeAnything) {
            if (couldBeAnything instanceof ModifiedPostConstruct) {
                if (((ModifiedPostConstruct) couldBeAnything).value.equals("second")) {
                    return new ModifiedPostConstructWithStatic("Gotcha!");
                }
                return (ModifiedPostConstructWithStatic) couldBeAnything;
            }
            return null;
        }
    }

    @Test
    public void testModifiedPostConstructStatic() {
        Instantiator i = Instantiator.forLookup(lookup());
        Function<String, ModifiedPostConstructWithStatic> factory = i.createFunctionFor(ModifiedPostConstructWithStatic.class, String.class);
        checkIsNotLambda(factory);
        ModifiedPostConstruct sample = factory.apply("hit");
        assertNotEquals("fallback", sample.value); // original check is not called any more because the return type does not match

        sample = factory.apply("second");
        assertEquals("Gotcha!", sample.value);
    }

    public static class ExpectNumber {
        private final Number num;

        public ExpectNumber(Number num) {
            this.num = num;
        }
    }

    @Test
    public void testPrimitiveInsteadOfObject() {
        Function<Number, ExpectNumber> instantiator = Instantiator.getDefault().createFunctionFor(ExpectNumber.class, Number.class);
        checkIsLambda(instantiator);
        ExpectNumber result = instantiator.apply(123);

        assertNotNull(result);
        assertEquals(123, result.num);
    }

    public static class WithOptionalArgument {
        public WithOptionalArgument(String unused) {
        }
    }

    @Test
    public void testNotMandatory() {
        Function<String, WithOptionalArgument> instantiator = Instantiator.getDefault().createFunctionFor(WithOptionalArgument.class, String.class);
        checkIsLambda(instantiator);
        WithOptionalArgument result = instantiator.apply(null);

        assertNotNull(result);
    }

    public static class Hidden {

        private Hidden() {
        }

        static MethodHandles.Lookup lookup() {
            return MethodHandles.lookup();
        }
    }

    @Test
    public void testPrivateAccess() {
        Instantiator i = Instantiator.forLookup(Hidden.lookup());
        Supplier<Hidden> supplier = i.createSupplierFor(Hidden.class);
        Hidden result = supplier.get();
        assertNotNull(result);
    }

    public static class HiddenWithArgument {
        HiddenWithArgument(String unused) {

        }

        static MethodHandles.Lookup lookup() {
            return MethodHandles.lookup();
        }
    }

    @Test
    public void testPrivateAccessWithArgument() {
        Instantiator i = Instantiator.forLookup(Hidden.lookup());
        Supplier<Hidden> function1 = i.createSupplierFor(Hidden.class);
        Hidden result1 = function1.get();
        assertNotNull(result1);

        Instantiator i2 = Instantiator.forLookup(HiddenWithArgument.lookup());
        Function<String, HiddenWithArgument> function2 = i2.createFunctionFor(HiddenWithArgument.class, String.class);
        HiddenWithArgument result2 = function2.apply("Hello!");
        assertNotNull(result2);
    }

    static class StringFactory {
        @Provider
        static String sayHello() {
            return "Hello!";
        }

        @Provider
        static String sayHelloTo(String name) {
            return "Hello " + name + "!";
        }
    }

    @Test
    public void testFactory() {
        Instantiator i = Instantiator.withProviders(lookup(), StringFactory.class);
        Supplier<String> hello = i.createSupplierFor(String.class);
        assertEquals("Hello!", hello.get());

        Function<String, String> helloWithName = i.createFunctionFor(String.class, String.class);
        assertEquals("Hello John!", helloWithName.apply("John"));
    }

    static class Counter {
        private int c;

        @Provider
        AtomicInteger createNext() {
            return new AtomicInteger(++c);
        }
    }

    @Test
    public void testFactoryInstance() {
        Instantiator i = Instantiator.withProviders(lookup(), Counter.class);
        Supplier<AtomicInteger> source = i.createSupplierFor(AtomicInteger.class);
        for (int x = 1; x < 100; x++) {
            assertEquals(x, source.get().get());
        }
    }

    static class NotOptionalFactory {
        @Provider
        static AtomicInteger createOnlyEven(int num) {
            if ((num & 1) == 0) return new AtomicInteger(num);
            return null;
        }
    }

    @Test
    public void testNotOptional() {
        Instantiator i = Instantiator.withProviders(lookup(), new NotOptionalFactory());
        @SuppressWarnings("unchecked")
        IntFunction<AtomicInteger> func = i.createProviderFor(IntFunction.class, AtomicInteger.class);
        checkIsLambda(func);
        AtomicInteger r1 = func.apply(2);
        assertNotNull(r1);
        assertEquals(2, r1.get());

        AtomicInteger r2 = func.apply(3);
        assertNull(r2);
    }

    static class OptionalFactory {
        @Provider @Nullable
        AtomicInteger createOnlyEven(int num) {
            if ((num & 1) == 0) return new AtomicInteger(99);
            return null;
        }
    }

    @Test
    public void testOptional() {
        Instantiator i = Instantiator.withProviders(lookup(), new OptionalFactory());
        @SuppressWarnings("unchecked")
        IntFunction<AtomicInteger> func = i.createProviderFor(IntFunction.class, AtomicInteger.class);
        checkIsNotLambda(func);
        AtomicInteger r1 = func.apply(2);
        assertNotNull(r1);
        assertEquals(99, r1.get());

        // Now the default constructor
        AtomicInteger r2 = func.apply(3);
        assertNotNull(r2);
        assertEquals(3, r2.get());
    }

    private static AtomicInteger getConstant(int num) {
        return num < 0 ? new AtomicInteger(2046) : null;
    }

    @Test
    public void testHandle() throws NoSuchMethodException, IllegalAccessException {
        MethodHandles.Lookup lookup = lookup();
        MethodHandle provider = lookup.findStatic(lookup.lookupClass(), "getConstant", MethodType.methodType(AtomicInteger.class, int.class));
        Instantiator i = Instantiator.withProviders(lookup, OptionalFactory.class, provider);
        @SuppressWarnings("unchecked")
        IntFunction<AtomicInteger> func = i.createProviderFor(IntFunction.class, AtomicInteger.class);
        checkIsNotLambda(func);

        AtomicInteger r0 = func.apply(-100);
        assertNotNull(r0);
        assertEquals(2046, r0.get());

        AtomicInteger r1 = func.apply(2);
        assertNotNull(r1);
        assertEquals(99, r1.get());

        // Now the default constructor
        AtomicInteger r2 = func.apply(3);
        assertNotNull(r2);
        assertEquals(3, r2.get());
    }
}
