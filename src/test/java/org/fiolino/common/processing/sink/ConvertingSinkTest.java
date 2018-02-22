package org.fiolino.common.processing.sink;

import org.fiolino.common.container.Container;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Created by kuli on 05.04.16.
 */
class ConvertingSinkTest {

    private static class MyConvertingSink extends ConvertingSink<Integer, String> {
        MyConvertingSink(Sink<String> target) {
            super(target);
        }

        @Override
        protected String convert(Integer element, Container metadata) {
            if (element >= 0) {
                return "test-" + element;
            }
            return null;
        }
    }

    @Test
    void testValue() throws Throwable {
        MySink<String> sink = new MySink<>();
        MyConvertingSink chainedSink = new MyConvertingSink(sink);
        chainedSink.accept(199, Container.empty());
        assertEquals("test-199", sink.result);
        chainedSink.commit(Container.empty());
        chainedSink.commit(Container.empty());
        assertEquals(2, sink.finishCount);
    }

    @Test
    void testNull() throws Throwable {
        MySink<String> sink = new MySink<>();
        sink.result = "Initial";
        MyConvertingSink chainedSink = new MyConvertingSink(sink);
        chainedSink.accept(-1, Container.empty());
        assertEquals("Initial", sink.result);
    }
}
