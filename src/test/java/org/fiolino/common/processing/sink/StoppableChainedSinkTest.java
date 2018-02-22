package org.fiolino.common.processing.sink;

import org.fiolino.common.container.Container;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Created by kuli on 06.04.16.
 */
class StoppableChainedSinkTest {
    @Test
    void testNormal() throws Throwable {
        MySink<String> sink = new MySink<>();
        Sink<String> stoppable = new StoppableChainedSink<>(sink);
        stoppable.accept("Hello world", Container.empty());
        assertEquals("Hello world", sink.result);
        stoppable.commit(Container.empty());
        assertEquals(1, sink.finishCount);
    }

    @Test
    void testStopAccepting() throws Throwable {
        MySink<String> sink = new MySink<>();
        StoppableChainedSink<String> stoppable = new StoppableChainedSink<>(sink);
        stoppable.stop();
        assertThrows(IllegalStateException.class, () -> stoppable.accept("Hello world", Container.empty()));
    }

    @Test
    void testStopFinishing() throws Throwable {
        MySink<String> sink = new MySink<>();
        StoppableChainedSink<String> stoppable = new StoppableChainedSink<>(sink);
        stoppable.stop();
        assertThrows(IllegalStateException.class, () -> stoppable.commit(Container.empty()));
    }
}
