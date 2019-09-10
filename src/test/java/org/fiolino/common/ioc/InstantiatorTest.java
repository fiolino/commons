package org.fiolino.common.ioc;

import org.fiolino.annotations.PostCreate;
import org.fiolino.annotations.PostProcessor;
import org.fiolino.annotations.Provider;
import org.fiolino.annotations.Requested;
import org.fiolino.common.reflection.Methods;
import org.junit.jupiter.api.Test;

import javax.annotation.Nullable;
import java.awt.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandleProxies;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Date;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Supplier;

import static java.lang.invoke.MethodHandles.lookup;
import static java.lang.invoke.MethodHandles.publicLookup;
import static java.lang.invoke.MethodType.methodType;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Created by kuli on 08.12.15.
 */
class InstantiatorTest {

    public static class Normal {
        boolean flag;
    }

    private void checkIsLambda(Object func) {
        assertTrue(Methods.wasLambdafiedDirect(func), "Should be a lambda function");
    }

    private void checkIsNotLambda(Object func) {
        assertTrue(MethodHandleProxies.isWrapperInstance(func), "Was expected to not be a lambda");
    }

    @Test
    void testNormal() {
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
    void testPostProcessor() {
        Supplier<WithPostProcessor> instantiator = Instantiator.getDefault().createSupplierFor(WithPostProcessor.class);
        checkIsNotLambda(instantiator);
        Normal result = instantiator.get();

        assertNotNull(result);
        assertTrue(result.flag);
    }

    public static class WithVoidPostConstruct {
        String content = "Initial";

        @PostCreate
        public void changeContent() {
            content = "Changed";
        }
    }

    @Test
    void testPostConstructSupplier() {
        WithVoidPostConstruct sample = new WithVoidPostConstruct();
        assertEquals("Initial", sample.content);

        Instantiator i = Instantiator.forLookup(lookup());
        Supplier<WithVoidPostConstruct> supplier = i.createSupplierFor(WithVoidPostConstruct.class);
        checkIsNotLambda(supplier);
        sample = supplier.get();
        assertEquals("Changed", sample.content);
    }

    @Test
    void testWithArgument() {
        Supplier<WithPostProcessor> instantiator = Instantiator.getDefault().createSupplierFor(WithPostProcessor.class);
        checkIsNotLambda(instantiator);
        Normal result = instantiator.get();

        assertNotNull(result);
        assertTrue(result.flag);
    }

    @Test
    void testCorrectArgument() {
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

        @PostCreate
        void increase() {
            num++;
        }

        @PostCreate
        void increaseAgain() {
            num += 2;
        }
    }

    @Test
    void testPostConstructFunction() {
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

        @PostCreate
        ModifiedPostConstruct check() {
            if (value.equals("hit")) {
                return FALLBACK;
            }
            return this;
        }
    }

    @Test
    void testModifiedPostConstruct() {
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

        @PostCreate
        static ModifiedPostConstructWithStatic secondCheck(Object couldBeAnything) {
            if (couldBeAnything instanceof ModifiedPostConstructWithStatic) {
                if (((ModifiedPostConstruct) couldBeAnything).value.equals("second")) {
                    return new ModifiedPostConstructWithStatic("Gotcha!");
                }
                return (ModifiedPostConstructWithStatic) couldBeAnything;
            }
            return null;
        }
    }

    @Test
    void testModifiedPostConstructStatic() {
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
    void testPrimitiveInsteadOfObject() {
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
    void testNotMandatory() {
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
    void testPrivateAccess() {
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
    void testPrivateAccessWithArgument() {
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
        String sayHello() {
            return "Hello!";
        }

        @Provider
        String sayHelloTo(String name) {
            return "Hello " + name + "!";
        }
    }

    @Test
    void testFactory() {
        Instantiator i = Instantiator.withProviders(lookup(), StringFactory.class);
        Supplier<String> hello = i.createSupplierFor(String.class);
        checkIsLambda(hello);
        assertEquals("Hello!", hello.get());

        Function<String, String> helloWithName = i.createFunctionFor(String.class, String.class);
        checkIsLambda(helloWithName);
        assertEquals("Hello John!", helloWithName.apply("John"));

        i = i.addProviders(new Object() {
            @Provider
            Object newObject(@Requested Class<? extends Number> type, String value) throws Throwable {
                MethodHandle valueOf = publicLookup().findStatic(type, "valueOf", methodType(type, String.class));
                return type.cast(valueOf.invoke(value + value)); // Make sure it was our method being called
            }
        });
        // find a function that uses the given factory
        Function<String, Integer> numberFunction = i.createFunctionFor(Integer.class, String.class);
        checkIsLambda(numberFunction);
        int integer = numberFunction.apply("1234");
        assertEquals(12341234, integer);

        // find a function where the factory does not match, so use the constructor instead
        Function<Object, AtomicReference> referenceFunction = i.createFunctionFor(AtomicReference.class, Object.class);
        checkIsLambda(referenceFunction);
        @SuppressWarnings("unchecked")
        AtomicReference<Object> ref = referenceFunction.apply("Some text");
        assertEquals("Some text", ref.get());
    }

    static class Counter {
        private int c;

        @Provider
        AtomicInteger createNext() {
            return new AtomicInteger(++c);
        }
    }

    @Test
    void testFactoryInstance() {
        Instantiator i = Instantiator.withProviders(lookup(), Counter.class);
        Supplier<AtomicInteger> source = i.createSupplierFor(AtomicInteger.class);
        checkIsLambda(source);
        for (int x = 1; x < 100; x++) {
            assertEquals(x, source.get().get());
        }
    }

    static class NotOptionalFactory {
        @Provider
        AtomicInteger createOnlyEven(int num) {
            if ((num & 1) == 0) return new AtomicInteger(num);
            return null;
        }
    }

    @Test
    void testNotOptional() {
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
    void testOptional() {
        Instantiator i = Instantiator.withProviders(lookup(), new OptionalFactory());
        @SuppressWarnings("unchecked")
        IntFunction<AtomicInteger> func = i.createProviderFor(IntFunction.class, AtomicInteger.class);
        checkIsNotLambda(func); // Because it's nullable
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
    void testHandle() throws NoSuchMethodException, IllegalAccessException {
        MethodHandles.Lookup lookup = lookup();
        MethodHandle provider = lookup.findStatic(lookup.lookupClass(), "getConstant", MethodType.methodType(AtomicInteger.class, int.class));
        Instantiator i = Instantiator.withProviders(lookup, OptionalFactory.class, provider);
        @SuppressWarnings("unchecked")
        IntFunction<AtomicInteger> func = i.createProviderFor(IntFunction.class, AtomicInteger.class);
        checkIsLambda(func);

        AtomicInteger r0 = func.apply(-100);
        assertNotNull(r0);
        assertEquals(2046, r0.get());

        AtomicInteger r1 = func.apply(2);
        assertNull(r1);
    }

    static class GenericFactory {
        @Provider
        CharSequence reproduce(@Requested Class<?> type, String someValue, Integer factor) {
            StringBuilder sb = new StringBuilder();
            for (int i=0; i < factor; i++) {
                sb.append(someValue);
            }
            if (type.equals(String.class)) {
                return sb.toString();
            } else if (type.equals(StringBuilder.class)) {
                return sb;
            } else {
                return null;
            }
        }

        @Provider
        static Object staticAlternative(@Requested Class<? extends Number> type, String someValue, Integer factor) throws Throwable {
            MethodHandle valueOf = publicLookup().findStatic(type, "valueOf", methodType(type, String.class));
            return valueOf.invoke(someValue);
        }
    }

    static class HasConstructor {
        HasConstructor(String unused, Integer unused2) {}
    }

    @Test
    void testGeneric() {
        Instantiator i = Instantiator.withProviders(lookup(), new GenericFactory());
        @SuppressWarnings("unchecked")
        BiFunction<String, Integer, String> func = i.createProviderFor(BiFunction.class, String.class, String.class, int.class);
        checkIsLambda(func);
        String s = func.apply("test", 3);
        assertNotNull(s);
        assertEquals("testtesttest", s);

        @SuppressWarnings("unchecked")
        BiFunction<String, Integer, StringBuilder> func2 = i.createProviderFor(BiFunction.class, StringBuilder.class, String.class, int.class);
        checkIsLambda(func2);
        StringBuilder sb = func2.apply("bla", 4);
        assertNotNull(sb);
        assertEquals("blablablabla", sb.toString());

        @SuppressWarnings("unchecked")
        BiFunction<String, Integer, StringBuffer> func3 = i.createProviderFor(BiFunction.class, StringBuffer.class, String.class, int.class);
        checkIsLambda(func3);
        StringBuffer sb2 = func3.apply("blubb", 5);
        assertNull(sb2); // Because GenericFactory is not @Nullable

        @SuppressWarnings("unchecked")
        BiFunction<String, Integer, Integer> func4 = i.createProviderFor(BiFunction.class, Integer.class, String.class, Integer.class);
        checkIsLambda(func4);
        int integer = func4.apply("19", 6);
        assertEquals(19, integer);

        @SuppressWarnings("unchecked")
        BiFunction<String, Integer, HasConstructor> func5 = i.createProviderFor(BiFunction.class, HasConstructor.class, String.class, Integer.class);
        checkIsLambda(func5);
        HasConstructor x = func5.apply("x", 6);
        assertNotNull(x);
    }

    @Test
    void testFail() {
        Instantiator i = Instantiator.withProviders(lookup(), new GenericFactory(), new OptionalFactory());
        assertThrows(AssertionError.class, () -> i.createProviderFor(BiFunction.class, Rectangle.class, Date.class, TimeUnit.class));
    }

    @FunctionalInterface
    interface MyFunction {
        BigDecimal withScale(String value, int scale);
    }

    @Test
    void testOwnFunctionalInterface() {
        Instantiator i = Instantiator.withProviders(lookup(), new Object() {
            @Provider
            BigDecimal create(String value, int scale) {
                return new BigDecimal(value).setScale(scale, RoundingMode.HALF_UP);
            }
        });
        MyFunction func = i.createProviderFor(MyFunction.class);
        BigDecimal dec = func.withScale("145.66", 1);
        assertEquals("145.7", dec.toString());
    }

    @FunctionalInterface
    interface FromInt<T> {
        T fromInt(int value);
    }

    @Test
    void testDynamicProvider() {
        Instantiator i = Instantiator.withProviders(lookup(), (MethodHandleProvider)(l, t) -> l.findStatic(t.returnType(), "valueOf", t));
        Function<String, Integer> intFunction = i.createFunctionFor(Integer.class, String.class);
        checkIsLambda(intFunction);
        Integer integer = intFunction.apply("1234");
        assertEquals(1234, (int) integer);

        @SuppressWarnings("unchecked")
        FromInt<String> stringFunction = i.createProviderFor(FromInt.class, String.class);
        checkIsLambda(stringFunction);
        String string = stringFunction.fromInt(9876);
        assertEquals("9876", string);

        // Now without valueOf() call
        @SuppressWarnings("unchecked")
        FromInt<AtomicInteger> atomicFunction = i.createProviderFor(FromInt.class, AtomicInteger.class);
        checkIsLambda(atomicFunction);
        AtomicInteger atomic = atomicFunction.fromInt(8848);
        assertEquals(8848, atomic.get());
    }
}
