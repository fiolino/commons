package org.fiolino.common.processing.sink;

import org.fiolino.common.container.Container;

/**
 * A sink that does nothing.
 *
 * Created by kuli on 06.04.16.
 */
public final class NullSink<T> implements ThreadsafeSink<T> {
  @Override
  public void accept(T value, Container metadata) {
    // Do nothing
  }

  @Override
  public void commit(Container metadata) {
    // Just ignore
  }
}
