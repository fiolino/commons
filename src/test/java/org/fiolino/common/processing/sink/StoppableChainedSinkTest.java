package org.fiolino.common.processing.sink;

import org.fiolino.common.container.Container;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * Created by kuli on 06.04.16.
 */
public class StoppableChainedSinkTest {
  @Test
  public void testNormal() throws Throwable {
    MySink<String> sink = new MySink<>();
    Sink<String> stoppable = new StoppableChainedSink<>(sink);
    stoppable.accept("Hello world", Container.empty());
    assertEquals("Hello world", sink.result);
    stoppable.commit(Container.empty());
    assertEquals(1, sink.finishCount);
  }

  @Test(expected = IllegalStateException.class)
  public void testStopAccepting() throws Throwable {
    MySink<String> sink = new MySink<>();
    StoppableChainedSink<String> stoppable = new StoppableChainedSink<>(sink);
    stoppable.stop();
    stoppable.accept("Hello world", Container.empty());
    fail("Shouldn't reach here.");
  }

  @Test(expected = IllegalStateException.class)
  public void testStopFinishing() throws Throwable {
    MySink<String> sink = new MySink<>();
    StoppableChainedSink<String> stoppable = new StoppableChainedSink<>(sink);
    stoppable.stop();
    stoppable.commit(Container.empty());
    fail("Shouldn't reach here.");
  }
}
