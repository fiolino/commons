package org.fiolino.common.processing.sink;

import org.fiolino.common.container.Container;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Created by kuli on 05.04.16.
 */
class ModifyingSinkTest {

    private static class MyModifyingSink extends ModifyingSink<AtomicInteger> {
        MyModifyingSink(Sink<AtomicInteger> target) {
            super(target);
        }

        @Override
        protected void touch(AtomicInteger element, Container metadata) {
            element.set(element.get() * 12);
        }
    }

    @Test
    void test() throws Throwable {
        MySink<AtomicInteger> sink = new MySink<>();
        MyModifyingSink modifyingSink = new MyModifyingSink(sink);
        AtomicInteger value = new AtomicInteger(150);
        modifyingSink.accept(value, Container.empty());
        assertEquals(value, sink.result);
        assertEquals(150 * 12, value.get());
    }
}
