package org.fiolino.common.processing.sink;

import org.fiolino.common.container.Container;

/**
 * Created by kuli on 31.03.16.
 */
public final class NullFilterSink<T> extends AbstractFilteringSink<T>
        implements CloneableSink<T, NullFilterSink<T>> {

  public NullFilterSink(Sink<? super T> target) {
    super(target);
  }

  @Override
  protected boolean test(T element) throws Exception {
    return element != null;
  }

  @Override
  public NullFilterSink<T> createClone() {
    return new NullFilterSink<>(targetForCloning());
  }

  @Override
  public void partialCommit(Container metadata) throws Exception {
    if (getTarget() instanceof CloneableSink) {
      ((CloneableSink<?, ?>) getTarget()).partialCommit(metadata);
    }
  }
}
