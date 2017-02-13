package org.fiolino.common.processing.sink;

import org.fiolino.common.container.Container;

import java.util.function.Predicate;

/**
 * Created by Kuli on 06/10/2016.
 */
public final class FilteringSink<T> extends AbstractFilteringSink<T> implements CloneableSink<T, FilteringSink<T>> {
  private final Predicate<? super T> testFunction;

  public FilteringSink(Sink<? super T> target, Predicate<? super T> testFunction) {
    super(target);
    this.testFunction = testFunction;
  }

  @Override
  protected boolean test(T element) throws Exception {
    return testFunction.test(element);
  }

  @Override
  public FilteringSink<T> createClone() {
    return new FilteringSink<>(targetForCloning(), testFunction);
  }

  @Override
  public void partialCommit(Container metadata) throws Exception {
    if (getTarget() instanceof CloneableSink) {
      ((CloneableSink<?, ?>) getTarget()).partialCommit(metadata);
    }
  }
}
