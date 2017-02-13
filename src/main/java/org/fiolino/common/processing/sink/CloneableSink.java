package org.fiolino.common.processing.sink;

import org.fiolino.common.container.Container;

/**
 * Created by Michael Kuhlmann on 12.04.2016.
 */
public interface CloneableSink<T, S extends Sink<T>> extends Sink<T> {

  /**
   * This is called for each parallel thread before the real commit is triggered.
   */
  void partialCommit(Container metadata) throws Exception;

  /**
   * Creates a clone of myself.
   */
  S createClone();
}
