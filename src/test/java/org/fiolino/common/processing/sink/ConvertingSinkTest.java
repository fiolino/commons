package org.fiolino.common.processing.sink;

import org.fiolino.common.container.Container;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Created by kuli on 05.04.16.
 */
public class ConvertingSinkTest {

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
    public void testValue() throws Throwable {
        MySink<String> sink = new MySink<>();
        MyConvertingSink chainedSink = new MyConvertingSink(sink);
        chainedSink.accept(199, Container.empty());
        assertEquals("test-199", sink.result);
        chainedSink.commit(Container.empty());
        chainedSink.commit(Container.empty());
        assertEquals(2, sink.finishCount);
    }

    @Test
    public void testNull() throws Throwable {
        MySink<String> sink = new MySink<>();
        sink.result = "Initial";
        MyConvertingSink chainedSink = new MyConvertingSink(sink);
        chainedSink.accept(-1, Container.empty());
        assertEquals("Initial", sink.result);
    }
}
