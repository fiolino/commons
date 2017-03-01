package org.fiolino.common.util;

import org.fiolino.common.ioc.PostProcessor;
import org.fiolino.data.annotation.Mandatory;
import org.junit.Test;

import javax.annotation.PostConstruct;
import java.lang.invoke.MethodHandles;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.junit.Assert.*;

/**
 * Created by kuli on 08.12.15.
 */
public class InstantiatorTest {

    public static class Normal {
        boolean flag;
    }

    @Test
    public void testNormal() {
        Supplier<Normal> instantiator = Instantiator.creatorFor(Normal.class);
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
        Supplier<WithPostProcessor> instantiator = Instantiator.creatorFor(WithPostProcessor.class);
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

        Supplier<WithVoidPostConstruct> supplier = Instantiator.creatorFor(MethodHandles.lookup(), WithVoidPostConstruct.class);
        sample = supplier.get();
        assertEquals("Changed", sample.content);
    }

    @Test
    public void testWithArgument() {
        Function<String, WithPostProcessor> instantiator = Instantiator.creatorWithArgument(WithPostProcessor.class, String.class);
        Normal result = instantiator.apply("Hello!");

        assertNotNull(result);
        assertTrue(result.flag);
    }

    @Test
    public void testCorrectArgument() {
        Function<String, StringBuilder> instantiator = Instantiator.creatorWithArgument(StringBuilder.class, String.class);
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
        Function<Integer, WithPostConstructAndConstructor> factory = Instantiator.creatorWithArgument(MethodHandles.lookup(), WithPostConstructAndConstructor.class, int.class);
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
        Function<String, ModifiedPostConstruct> factory = Instantiator.creatorWithArgument(MethodHandles.lookup(), ModifiedPostConstruct.class, String.class);
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
        Function<String, ModifiedPostConstructWithStatic> factory = Instantiator.creatorWithArgument(MethodHandles.lookup(), ModifiedPostConstructWithStatic.class, String.class);
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
        Function<Object, ExpectNumber> instantiator = Instantiator.creatorWithArgument(ExpectNumber.class, Object.class);
        ExpectNumber result = instantiator.apply(123);

        assertNotNull(result);
        assertEquals(123, result.num);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testWrongArgument() {
        Function<Object, ExpectNumber> instantiator = Instantiator.creatorWithArgument(ExpectNumber.class, Object.class);
        ExpectNumber result = instantiator.apply("Hello!");

        fail("Shouldn't reach this");
    }

    public static class WithOptionalArgument {
        public WithOptionalArgument(String unused) {
        }
    }

    @Test
    public void testNotMandatory() {
        Function<String, WithOptionalArgument> instantiator = Instantiator.creatorWithArgument(WithOptionalArgument.class, String.class);
        WithOptionalArgument result = instantiator.apply(null);

        assertNotNull(result);
    }

    public static class WithMandatoryArgument {
        public WithMandatoryArgument(@Mandatory String unused) {
        }
    }

    @Test
    public void testMandatory() {
        Function<String, WithMandatoryArgument> instantiator = Instantiator.creatorWithArgument(WithMandatoryArgument.class, String.class);
        WithMandatoryArgument result = instantiator.apply("Hello!");

        assertNotNull(result);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testFailedMandatory() {
        Function<String, WithMandatoryArgument> instantiator = Instantiator.creatorWithArgument(WithMandatoryArgument.class, String.class);
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
        Supplier<Hidden> supplier = Instantiator.creatorFor(Hidden.lookup(), Hidden.class);
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
        Function<String, Hidden> function1 = Instantiator.creatorWithArgument(Hidden.lookup(), Hidden.class, String.class);
        Hidden result1 = function1.apply("Hello!");
        assertNotNull(result1);

        Function<String, HiddenWithArgument> function2 = Instantiator.creatorWithArgument(HiddenWithArgument.lookup(), HiddenWithArgument.class, String.class);
        HiddenWithArgument result2 = function2.apply("Hello!");
        assertNotNull(result2);
    }

}
