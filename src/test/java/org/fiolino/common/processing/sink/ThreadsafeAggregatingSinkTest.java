package org.fiolino.common.processing.sink;

import org.fiolino.common.container.Container;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * Created by kuli on 06.04.16.
 */
public class ThreadsafeAggregatingSinkTest {
    @Test
    public void testNotFilled() throws Throwable {
        MySink<List<String>> sink = new MySink<>();
        Sink<String> aggregator = new ThreadsafeAggregatingSink<>(sink, 10);

        aggregator.accept("One", Container.empty());
        aggregator.accept("Two", Container.empty());
        aggregator.accept("Three", Container.empty());
        assertNull(sink.result);
        aggregator.commit(Container.empty());
        assertEquals("One", sink.result.get(0));
        assertEquals("Two", sink.result.get(1));
        assertEquals("Three", sink.result.get(2));
    }

    @Test
    public void testFull() throws Throwable {
        MySink<List<String>> sink = new MySink<>();
        Sink<String> aggregator = new ThreadsafeAggregatingSink<>(sink, 2);
        aggregator.accept("One", Container.empty());
        aggregator.accept("Two", Container.empty());
        aggregator.accept("Three", Container.empty());
        assertEquals(2, sink.result.size());
        assertEquals("One", sink.result.get(0));
        assertEquals("Two", sink.result.get(1));
        sink.hasAccepted = false;
        aggregator.commit(Container.empty());
        assertEquals(1, sink.result.size());
        assertEquals("Three", sink.result.get(0));
    }
}
