package org.fiolino.common.processing.sink;

import org.fiolino.common.container.Container;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Created by kuli on 06.04.16.
 */
class DecomposingSinkTest {
    @Test
    void test() throws Throwable {
        final boolean[] finished = new boolean[] {
                false
        };

        Sink<String> sink = new Sink<String>() {
            int count;

            @Override
            public void accept(String value, Container metadata) {
                switch (count++) {
                    case 0:
                        assertEquals("One", value);
                        return;
                    case 1:
                        assertEquals("Two", value);
                        return;
                    case 2:
                        assertEquals("Three", value);
                        return;
                    default:
                        fail("Shouldn't reach here.");
                }
            }

            @Override
            public void commit(Container metadata) {
                finished[0] = true;
                assertEquals(3, count);
            }
        };

        Sink<Iterable<String>> listSink = new DecomposingSink<>(sink);
        listSink.accept(Arrays.asList("One", "Two", "Three"), Container.empty());
        listSink.commit(Container.empty());
        assertTrue(finished[0]);
    }

}
