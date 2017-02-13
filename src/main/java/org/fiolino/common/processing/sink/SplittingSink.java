package org.fiolino.common.processing.sink;

import org.fiolino.common.container.Container;

/**
 * A sink that splits into two different targets, depending on the type of the incoming object.
 *
 * Created by kuli on 15.06.16.
 */
public final class SplittingSink<T, U extends T, V extends T> implements CloneableSink<T, SplittingSink<T, U, V>> {
  private final Class<U> firstCheck;
  private final Sink<? super U> firstSink;
  private final Class<V> secondCheck;
  private final Sink<? super V> secondSink;

  public SplittingSink(Class<U> firstCheck, Sink<? super U> firstSink, Class<V> secondCheck, Sink<? super V> secondSink) {
    if (firstCheck.isAssignableFrom(secondCheck)) {
      throw new IllegalArgumentException(secondCheck.getName() + " mustn't be related to " + firstCheck.getName());
    }
    this.firstCheck = firstCheck;
    this.firstSink = firstSink;
    this.secondCheck = secondCheck;
    this.secondSink = secondSink;
  }

  @Override
  public void accept(T value, Container metadata) throws Exception {
    if (firstCheck.isInstance(value)) {
      firstSink.accept(firstCheck.cast(value), metadata);
      return;
    }
    if (secondCheck.isInstance(value)) {
      secondSink.accept(secondCheck.cast(value), metadata);
      return;
    }
    if (value == null) {
      return;
    }

    throw new IllegalArgumentException(value + " must be of either " + firstCheck.getName() + " or "
        + secondCheck.getName());
  }

  @Override
  public void commit(Container metadata) throws Exception {
    firstSink.commit(metadata);
    secondSink.commit(metadata);
  }

  @Override
  public void partialCommit(Container metadata) throws Exception {
    if (firstSink instanceof CloneableSink) {
      ((CloneableSink<?, ?>) firstSink).partialCommit(metadata);
    }
    if (secondSink instanceof CloneableSink) {
      ((CloneableSink<?, ?>) secondSink).partialCommit(metadata);
    }
  }

  @Override
  public SplittingSink<T, U, V> createClone() {
    return new SplittingSink<>(firstCheck, ChainedSink.targetForCloning(firstSink),
            secondCheck, ChainedSink.targetForCloning(secondSink));
  }
}
