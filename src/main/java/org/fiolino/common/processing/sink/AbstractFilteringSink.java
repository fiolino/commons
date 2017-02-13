package org.fiolino.common.processing.sink;

import org.fiolino.common.container.Container;

/**
 * Created by Michael Kuhlmann on 23.12.2015.
 */
abstract class AbstractFilteringSink<T> extends ChainedSink<T, T> {

  public AbstractFilteringSink(Sink<? super T> target) {
    super(target);
  }

  protected abstract boolean test(T element) throws Exception;

  @Override
  public void accept(T value, Container metadata) throws Exception {
    if (test(value)) {
      getTarget().accept(value, metadata);
    }
  }
}
