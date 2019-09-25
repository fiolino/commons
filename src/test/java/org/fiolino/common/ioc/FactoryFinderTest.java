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
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.Date;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.*;

import static java.lang.invoke.MethodHandles.lookup;
import static java.lang.invoke.MethodHandles.publicLookup;
import static java.lang.invoke.MethodType.methodType;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Created by kuli on 08.12.15.
 */
class FactoryFinderTest {

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
        Supplier<Normal> instantiator = FactoryFinder.instantiator().createSupplierFor(Normal.class);
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
        Supplier<WithPostProcessor> instantiator = FactoryFinder.instantiator().createSupplierFor(WithPostProcessor.class);
        checkIsNotLambda(instantiator);
        Normal result = instantiator.get();

        assertNotNull(result);
        assertTrue(result.flag);
    }

    public static class WithVoidPostConstruct {
        String content = "Initial";

        @PostCreate @SuppressWarnings("unused")
        public void changeContent() {
            content = "Changed";
        }
    }

    @Test
    void testPostConstructSupplier() {
        WithVoidPostConstruct sample = new WithVoidPostConstruct();
        assertEquals("Initial", sample.content);

        FactoryFinder i = FactoryFinder.instantiator();
        Supplier<WithVoidPostConstruct> supplier = i.createSupplierFor(WithVoidPostConstruct.class);
        checkIsNotLambda(supplier);
        sample = supplier.get();
        assertEquals("Changed", sample.content);
    }

    @Test
    void testWithArgument() {
        Supplier<WithPostProcessor> instantiator = FactoryFinder.instantiator().createSupplierFor(WithPostProcessor.class);
        checkIsNotLambda(instantiator);
        Normal result = instantiator.get();

        assertNotNull(result);
        assertTrue(result.flag);
    }

    @Test
    void testCorrectArgument() {
        Function<String, StringBuilder> instantiator = FactoryFinder.instantiator().createFunctionFor(StringBuilder.class, String.class);
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

        @PostCreate @SuppressWarnings("unused")
        void increase() {
            num++;
        }

        @PostCreate @SuppressWarnings("unused")
        void increaseAgain() {
            num += 2;
        }
    }

    @Test
    void testPostConstructFunction() {
        FactoryFinder i = FactoryFinder.instantiator();
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

        @PostCreate @SuppressWarnings("unused")
        ModifiedPostConstruct check() {
            if (value.equals("hit")) {
                return FALLBACK;
            }
            return this;
        }
    }

    @Test
    void testModifiedPostConstruct() {
        FactoryFinder i = FactoryFinder.instantiator();
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

        @PostCreate @SuppressWarnings("unused")
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
        FactoryFinder i = FactoryFinder.instantiator();
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
        Function<Number, ExpectNumber> instantiator = FactoryFinder.instantiator().createFunctionFor(ExpectNumber.class, Number.class);
        checkIsLambda(instantiator);
        ExpectNumber result = instantiator.apply(123);

        assertNotNull(result);
        assertEquals(123, result.num);
    }

    public static class WithOptionalArgument {
        public WithOptionalArgument(@SuppressWarnings("unused")String unused) {
        }
    }

    @Test
    void testNotMandatory() {
        Function<String, WithOptionalArgument> instantiator = FactoryFinder.instantiator().createFunctionFor(WithOptionalArgument.class, String.class);
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
        FactoryFinder i = FactoryFinder.instantiator().using(Hidden.lookup());
        Supplier<Hidden> supplier = i.createSupplierFor(Hidden.class);
        Hidden result = supplier.get();
        assertNotNull(result);
    }

    public static class HiddenWithArgument {
        HiddenWithArgument(@SuppressWarnings("unused") String unused) {

        }

        static MethodHandles.Lookup lookup() {
            return MethodHandles.lookup();
        }
    }

    @Test
    void testPrivateAccessWithArgument() {
        FactoryFinder i = FactoryFinder.instantiator().using(Hidden.lookup());
        Supplier<Hidden> function1 = i.createSupplierFor(Hidden.class);
        Hidden result1 = function1.get();
        assertNotNull(result1);

        FactoryFinder i2 = FactoryFinder.instantiator().using(HiddenWithArgument.lookup());
        Function<String, HiddenWithArgument> function2 = i2.createFunctionFor(HiddenWithArgument.class, String.class);
        HiddenWithArgument result2 = function2.apply("Hello!");
        assertNotNull(result2);
    }

    @SuppressWarnings("unused")
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
        FactoryFinder i = FactoryFinder.instantiator().withProvidersFrom(lookup(), StringFactory.class);
        Supplier<String> hello = i.createSupplierFor(String.class);
        checkIsLambda(hello);
        assertEquals("Hello!", hello.get());

        Function<String, String> helloWithName = i.createFunctionFor(String.class, String.class);
        checkIsLambda(helloWithName);
        assertEquals("Hello John!", helloWithName.apply("John"));

        i = i.withProvidersFrom(new Object() {
            @Provider @SuppressWarnings("unused")
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

        @Provider @SuppressWarnings("unused")
        AtomicInteger createNext() {
            return new AtomicInteger(++c);
        }
    }

    @Test
    void testFactoryInstance() {
        FactoryFinder i = FactoryFinder.instantiator().withProvidersFrom(lookup(), Counter.class);
        Supplier<AtomicInteger> source = i.createSupplierFor(AtomicInteger.class);
        checkIsLambda(source);
        for (int x = 1; x < 100; x++) {
            assertEquals(x, source.get().get());
        }
    }

    static class NotOptionalFactory {
        @Provider @SuppressWarnings("unused")
        AtomicInteger createOnlyEven(int num) {
            if ((num & 1) == 0) return new AtomicInteger(num);
            return null;
        }
    }

    @Test
    void testNotOptional() {
        FactoryFinder i = FactoryFinder.instantiator().withProvidersFrom(lookup(), new NotOptionalFactory());
        @SuppressWarnings("unchecked")
        IntFunction<AtomicInteger> func = i.createLambda(IntFunction.class, AtomicInteger.class);
        checkIsLambda(func);
        AtomicInteger r1 = func.apply(2);
        assertNotNull(r1);
        assertEquals(2, r1.get());

        AtomicInteger r2 = func.apply(3);
        assertNull(r2);
    }

    static class OptionalFactory {
        @Provider @Nullable @SuppressWarnings("unused")
        AtomicInteger createOnlyEven(int num) {
            if ((num & 1) == 0) return new AtomicInteger(99);
            return null;
        }
    }

    @Test
    void testOptional() {
        FactoryFinder i = FactoryFinder.instantiator().withProvidersFrom(lookup(), new OptionalFactory());
        @SuppressWarnings("unchecked")
        IntFunction<AtomicInteger> func = i.createLambda(IntFunction.class, AtomicInteger.class);
        checkIsNotLambda(func); // Because it's nullable
        AtomicInteger r1 = func.apply(2);
        assertNotNull(r1);
        assertEquals(99, r1.get());

        // Now the default constructor
        AtomicInteger r2 = func.apply(3);
        assertNotNull(r2);
        assertEquals(3, r2.get());
    }

    @SuppressWarnings("unused")
    private static AtomicInteger getConstant(int num) {
        return num < 0 ? new AtomicInteger(2046) : null;
    }

    @Test
    void testHandle() throws NoSuchMethodException, IllegalAccessException {
        MethodHandles.Lookup lookup = lookup();
        MethodHandle provider = lookup.findStatic(lookup.lookupClass(), "getConstant", MethodType.methodType(AtomicInteger.class, int.class));
        FactoryFinder i = FactoryFinder.instantiator().withProvidersFrom(lookup, OptionalFactory.class).withMethodHandle(provider);
        @SuppressWarnings("unchecked")
        IntFunction<AtomicInteger> func = i.createLambda(IntFunction.class, AtomicInteger.class);
        checkIsLambda(func);

        AtomicInteger r0 = func.apply(-100);
        assertNotNull(r0);
        assertEquals(2046, r0.get());

        AtomicInteger r1 = func.apply(2);
        assertNull(r1);
    }

    static class GenericFactory {
        @Provider @SuppressWarnings("unused")
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

        @Provider @SuppressWarnings("unused")
        static Object staticAlternative(@Requested Class<? extends Number> type, String someValue, Integer factor) throws Throwable {
            MethodHandle valueOf = publicLookup().findStatic(type, "valueOf", methodType(type, String.class));
            return valueOf.invoke(someValue);
        }
    }

    static class HasConstructor {
        HasConstructor(@SuppressWarnings("unused") String unused, @SuppressWarnings("unused") Integer unused2) {}
    }

    @Test
    void testGeneric() {
        FactoryFinder i = FactoryFinder.instantiator().withProvidersFrom(lookup(), new GenericFactory());
        @SuppressWarnings("unchecked")
        BiFunction<String, Integer, String> func = i.createLambda(BiFunction.class, String.class, String.class, int.class);
        checkIsLambda(func);
        String s = func.apply("test", 3);
        assertNotNull(s);
        assertEquals("testtesttest", s);

        @SuppressWarnings("unchecked")
        BiFunction<String, Integer, StringBuilder> func2 = i.createLambda(BiFunction.class, StringBuilder.class, String.class, int.class);
        checkIsLambda(func2);
        StringBuilder sb = func2.apply("bla", 4);
        assertNotNull(sb);
        assertEquals("blablablabla", sb.toString());

        @SuppressWarnings("unchecked")
        BiFunction<String, Integer, StringBuffer> func3 = i.createLambda(BiFunction.class, StringBuffer.class, String.class, int.class);
        checkIsLambda(func3);
        StringBuffer sb2 = func3.apply("blubb", 5);
        assertNull(sb2); // Because GenericFactory is not @Nullable

        @SuppressWarnings("unchecked")
        BiFunction<String, Integer, Integer> func4 = i.createLambda(BiFunction.class, Integer.class, String.class, Integer.class);
        checkIsLambda(func4);
        int integer = func4.apply("19", 6);
        assertEquals(19, integer);

        @SuppressWarnings("unchecked")
        BiFunction<String, Integer, HasConstructor> func5 = i.createLambda(BiFunction.class, HasConstructor.class, String.class, Integer.class);
        checkIsLambda(func5);
        HasConstructor x = func5.apply("x", 6);
        assertNotNull(x);
    }

    @Test
    void testFail() {
        FactoryFinder i = FactoryFinder.instantiator().withProvidersFrom(lookup(), new GenericFactory()).withProvidersFrom(new OptionalFactory());
        assertThrows(NoSuchProviderException.class, () -> i.createLambda(BiFunction.class, Rectangle.class, Date.class, TimeUnit.class));
    }

    @FunctionalInterface
    interface MyFunction {
        BigDecimal withScale(String value, int scale);
    }

    @Test
    void testOwnFunctionalInterface() {
        FactoryFinder i = FactoryFinder.instantiator().withProvidersFrom(lookup(), new Object() {
            @Provider @SuppressWarnings("unused")
            BigDecimal create(String value, int scale) {
                return new BigDecimal(value).setScale(scale, RoundingMode.HALF_UP);
            }
        });
        MyFunction func = i.createLambda(MyFunction.class);
        BigDecimal dec = func.withScale("145.66", 1);
        assertEquals("145.7", dec.toString());
    }

    @Test
    void testDynamicProvider() {
        FactoryFinder inst = FactoryFinder.instantiator().withMethodHandleProvider((l, ff, t) -> l.findStatic(t.returnType(), "valueOf", t));
        Function<String, Integer> intFunction = inst.createFunctionFor(Integer.class, String.class);
        checkIsLambda(intFunction);
        Integer integer = intFunction.apply("1234");
        assertEquals(1234, (int) integer);

        @SuppressWarnings("unchecked")
        IntFunction<String> stringFunction = inst.createLambda(IntFunction.class, String.class);
        checkIsLambda(stringFunction);
        String string = stringFunction.apply(9876);
        assertEquals("9876", string);

        // Now without valueOf() call
        @SuppressWarnings("unchecked")
        IntFunction<AtomicInteger> atomicFunction = inst.createLambda(IntFunction.class, AtomicInteger.class);
        checkIsLambda(atomicFunction);
        AtomicInteger atomic = atomicFunction.apply(8848);
        assertEquals(8848, atomic.get());
    }

    @Test
    void testToPrimitive() throws NoSuchMethodException, IllegalAccessException {
        MethodHandle h = publicLookup().findStatic(Integer.class, "parseInt", methodType(int.class, String.class));
        FactoryFinder i = FactoryFinder.instantiator().withMethodHandle(h);
        @SuppressWarnings("unchecked")
        ToIntFunction<String> func = i.createLambda(ToIntFunction.class, int.class, String.class);
        checkIsLambda(func);
        int value = func.applyAsInt("-550055");
        assertEquals(-550055, value);
    }

    static class DateFactory {
        @Provider @SuppressWarnings("unused")
        Date getDate(long time) {
            return new Date(0L);
        }
    }

    static class DateAndSubclassesFactory {
        @Provider @SuppressWarnings("unused")
        Date getDate(@Requested Class<? extends Date> type, long time) throws NoSuchMethodException, IllegalAccessException, InvocationTargetException, java.lang.InstantiationException {
            Constructor<? extends Date> c = type.getConstructor(long.class);
            return c.newInstance(0L);
        }
    }

    @Test @SuppressWarnings("unchecked")
    void testOnlyExactType() {
        FactoryFinder i = FactoryFinder.instantiator().withProvidersFrom(lookup(), new DateFactory());
        LongFunction<Date> dateFunction = (LongFunction<Date>) i.createLambda(LongFunction.class, Date.class);
        Date d = dateFunction.apply(999_999L);
        assertEquals(0L, d.getTime());

        LongFunction<Timestamp> timestampFunction = (LongFunction<Timestamp>) i.createLambda(LongFunction.class, Timestamp.class);
        Timestamp timestamp = timestampFunction.apply(999_999);
        assertEquals(999_999L, timestamp.getTime());

        i = FactoryFinder.instantiator().withProvidersFrom(lookup(), new DateAndSubclassesFactory());
        dateFunction = (LongFunction<Date>) i.createLambda(LongFunction.class, Date.class);
        d = dateFunction.apply(999_999);
        assertEquals(0L, d.getTime());

        timestampFunction = (LongFunction<Timestamp>) i.createLambda(LongFunction.class, Timestamp.class);
        timestamp = timestampFunction.apply(999_999);
        assertEquals(0L, timestamp.getTime());
    }

    @SuppressWarnings("unused")
    static class FactoryForCertainTypes {
        @Provider(CharSequence.class)
        Object sequenceOnly() {
            return "This is a string";
        }

        @Provider({java.sql.Date.class, Timestamp.class })
        Date sqlTypesOnly(@Requested Class<? extends Date> type, long time) throws NoSuchMethodException, IllegalAccessException, InvocationTargetException, java.lang.InstantiationException {
            Constructor<? extends Date> c = type.getConstructor(long.class);
            return c.newInstance(0L);
        }
    }

    @Test @SuppressWarnings("unchecked")
    void testOnlySpecifiedTypes() {
        FactoryFinder i = FactoryFinder.instantiator().withProvidersFrom(lookup(), new FactoryForCertainTypes());
        Supplier<Object> objectSupplier = i.createSupplierFor(Object.class);
        Object o = objectSupplier.get();
        assertEquals(Object.class, o.getClass());

        Supplier<String> stringSupplier = i.createSupplierFor(String.class);
        String s = stringSupplier.get();
        assertEquals("", s);

        Supplier<CharSequence> sequenceSupplier = i.createSupplierFor(CharSequence.class);
        CharSequence seq = sequenceSupplier.get();
        assertEquals("This is a string", seq);


        LongFunction<Date> dateFunction = (LongFunction<Date>) i.createLambda(LongFunction.class, Date.class);
        Date d = dateFunction.apply(999_999L);
        assertEquals(999_999L, d.getTime());

        LongFunction<java.sql.Date> sqlDateFunction = (LongFunction<java.sql.Date>) i.createLambda(LongFunction.class, java.sql.Date.class);
        d = sqlDateFunction.apply(999_999L);
        assertEquals(0L, d.getTime());

        LongFunction<Timestamp> timestampFunction = (LongFunction<Timestamp>) i.createLambda(LongFunction.class, Timestamp.class);
        d = timestampFunction.apply(999_999);
        assertEquals(0L, d.getTime());

        LongFunction<Time> timeFunction = (LongFunction<Time>) i.createLambda(LongFunction.class, Time.class);
        d = timeFunction.apply(999_999L);
        assertEquals(999_999L, d.getTime());
    }

    @SuppressWarnings("unused")
    private static String combine(long v1, String v2, TimeUnit v3) {
        return v2 + ": " + v3.toMillis(v1);
    }

    @Test
    void testHandleWithInitializers() throws Throwable {
        MethodHandles.Lookup l = lookup();
        MethodHandle h = l.findStatic(l.lookupClass(), "combine", methodType(String.class, long.class, String.class, TimeUnit.class));
        FactoryFinder i = FactoryFinder.instantiator().using(l);
        FactoryFinder i2 = i.withMethodHandle(h, 334L, "Test");

        MethodHandle exec = i2.find(String.class, TimeUnit.class).orElseThrow();
        String result = (String) exec.invokeExact(TimeUnit.SECONDS);
        assertEquals("Test: 334000", result);
        result = (String) exec.invokeExact(TimeUnit.MINUTES);
        assertEquals("Test: " + 60*334000, result);

        Function<TimeUnit, String> f = i2.createFunctionFor(String.class, TimeUnit.class);
        checkIsLambda(f);
        result = f.apply(TimeUnit.SECONDS);
        assertEquals("Test: 334000", result);
        result = f.apply(TimeUnit.MINUTES);
        assertEquals("Test: " + 60*334000, result);

        i2 = i.withMethodHandle(h, 77776644232L, "Bigger");

        exec = i2.find(String.class, TimeUnit.class).orElseThrow();
        result = (String) exec.invokeExact(TimeUnit.SECONDS);
        assertEquals("Bigger: 77776644232000", result);
        result = (String) exec.invokeExact(TimeUnit.MICROSECONDS);
        assertEquals("Bigger: 77776644", result);

        f = i2.createFunctionFor(String.class, TimeUnit.class);
        checkIsLambda(f);
        result = f.apply(TimeUnit.SECONDS);
        assertEquals("Bigger: 77776644232000", result);
        result = f.apply(TimeUnit.MICROSECONDS);
        assertEquals("Bigger: 77776644", result);
    }

    @Test
    void testTransformationOfValues() {
        FactoryFinder i = FactoryFinder.full();

        AtomicInteger instantiated = i.transform(AtomicInteger.class);
        assertEquals(0, instantiated.get());
        instantiated = i.transform(AtomicInteger.class, 99);
        assertEquals(99, instantiated.get());

        int converted = i.transform(int.class, "-813");
        assertEquals(-813, instantiated.get());

        i = i.withProvidersFrom(new Object() {
            @Provider String concatenate(CharSequence v1, long v2, TimeUnit v3) {
                return v1 + ": " + v3.toMillis(v2) + " ms";
            }
        });
        String concatenated = i.transform(String.class, "fff", 3636L, TimeUnit.SECONDS);
        assertEquals("fff: 3636000 ms", concatenated);
    }
}
