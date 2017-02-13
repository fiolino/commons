package org.fiolino.common.processing.sink;

import org.fiolino.common.container.Container;

/**
 * A sink is the target for data operations.
 *
 * It's meant to be a sink for unrelated data items. Each item is sent into the sink by calling accept.
 * Additional finished calls can be used to commit the sent items.
 *
 * Sinks can be chained together. Intermediate sinks are allowed to change the order of the calls, as long as
 * the following rules apply:
 * 1. accept() calls may be delayed and reordered, but they mustn't be discarded (unless it is intended, because
 * the particular items shall be suppressed).
 * 2. finished() calls may be delayed and even be combined, as long as they are eventually executed. Following
 * accept() calls may be executed before, but after previous accept() the finished() call must eventually run.
 *
 * Created by Michael Kuhlmann on 22.12.2015.
 */
public interface Sink<T> {

  /**
   * Accepts some data items.
   */
  void accept(T value, Container metadata) throws Exception;

  /**
   * Commits the until now sent items to whatever target.
   */
  void commit(Container metadata) throws Exception;
}
