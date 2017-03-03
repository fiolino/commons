package org.fiolino.common.util;

import org.fiolino.common.ioc.PostProcessor;
import org.fiolino.data.annotation.Mandatory;
import org.junit.Test;

import javax.annotation.PostConstruct;
import java.lang.invoke.MethodHandleProxies;
import java.lang.invoke.MethodHandles;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
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
        Supplier<Normal> instantiator = Instantiator.getDefault().creatorFor(Normal.class);
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
        Supplier<WithPostProcessor> instantiator = Instantiator.getDefault().creatorFor(WithPostProcessor.class);
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

        Instantiator i = new Instantiator(lookup());
        Supplier<WithVoidPostConstruct> supplier = i.creatorFor(WithVoidPostConstruct.class);
        checkIsNotLambda(supplier);
        sample = supplier.get();
        assertEquals("Changed", sample.content);
    }

    @Test
    public void testWithArgument() {
        Function<String, WithPostProcessor> instantiator = Instantiator.getDefault().creatorWithArgument(WithPostProcessor.class, String.class);
        checkIsNotLambda(instantiator);
        Normal result = instantiator.apply("Hello!");

        assertNotNull(result);
        assertTrue(result.flag);
    }

    @Test
    public void testCorrectArgument() {
        Function<String, StringBuilder> instantiator = Instantiator.getDefault().creatorWithArgument(StringBuilder.class, String.class);
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
        Instantiator i = new Instantiator(lookup());
        Function<Integer, WithPostConstructAndConstructor> factory = i.creatorWithArgument(WithPostConstructAndConstructor.class, int.class);
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
        Instantiator i = new Instantiator(lookup());
        Function<String, ModifiedPostConstruct> factory = i.creatorWithArgument(ModifiedPostConstruct.class, String.class);
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
        Instantiator i = new Instantiator(lookup());
        Function<String, ModifiedPostConstructWithStatic> factory = i.creatorWithArgument(ModifiedPostConstructWithStatic.class, String.class);
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
        Function<Object, ExpectNumber> instantiator = Instantiator.getDefault().creatorWithArgument(ExpectNumber.class, Object.class);
        checkIsNotLambda(instantiator);
        ExpectNumber result = instantiator.apply(123);

        assertNotNull(result);
        assertEquals(123, result.num);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testWrongArgument() {
        Function<Object, ExpectNumber> instantiator = Instantiator.getDefault().creatorWithArgument(ExpectNumber.class, Object.class);
        checkIsNotLambda(instantiator);
        ExpectNumber result = instantiator.apply("Hello!");

        fail("Shouldn't reach this");
    }

    public static class WithOptionalArgument {
        public WithOptionalArgument(String unused) {
        }
    }

    @Test
    public void testNotMandatory() {
        Function<String, WithOptionalArgument> instantiator = Instantiator.getDefault().creatorWithArgument(WithOptionalArgument.class, String.class);
        checkIsLambda(instantiator);
        WithOptionalArgument result = instantiator.apply(null);

        assertNotNull(result);
    }

    public static class WithMandatoryArgument {
        public WithMandatoryArgument(@Mandatory String unused) {
        }
    }

    @Test
    public void testMandatory() {
        Function<String, WithMandatoryArgument> instantiator = Instantiator.getDefault().creatorWithArgument(WithMandatoryArgument.class, String.class);
        checkIsNotLambda(instantiator);
        WithMandatoryArgument result = instantiator.apply("Hello!");

        assertNotNull(result);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testFailedMandatory() {
        Function<String, WithMandatoryArgument> instantiator = Instantiator.getDefault().creatorWithArgument(WithMandatoryArgument.class, String.class);
        checkIsNotLambda(instantiator);
        instantiator.apply(null);

        fail("Shouldn't reach this");
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
        Instantiator i = new Instantiator(Hidden.lookup());
        Supplier<Hidden> supplier = i.creatorFor(Hidden.class);
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
        Instantiator i = new Instantiator(Hidden.lookup());
        Function<String, Hidden> function1 = i.creatorWithArgument(Hidden.class, String.class);
        Hidden result1 = function1.apply("Hello!");
        assertNotNull(result1);

        Instantiator i2 = new Instantiator(HiddenWithArgument.lookup());
        Function<String, HiddenWithArgument> function2 = i2.creatorWithArgument(HiddenWithArgument.class, String.class);
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
        Instantiator i = new Instantiator(lookup());
        i.registerFactory(StringFactory.class);
        Supplier<String> hello = i.creatorFor(String.class);
        assertEquals("Hello!", hello.get());

        Function<String, String> helloWithName = i.creatorWithArgument(String.class, String.class);
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
        Instantiator i = new Instantiator(lookup());
        i.registerFactory(Counter.class);
        Supplier<AtomicInteger> source = i.creatorFor(AtomicInteger.class);
        for (int x = 1; x < 100; x++) {
            assertEquals(x, source.get().get());
        }

        Function<String, AtomicInteger> ignoreParameter = i.creatorWithArgument(AtomicInteger.class, String.class);
        assertEquals(100, ignoreParameter.apply("unused").get());
    }


}
