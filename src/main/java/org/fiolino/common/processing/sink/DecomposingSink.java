package org.fiolino.common.processing.sink;

import org.fiolino.common.container.Container;

/**
 * Created by kuli on 31.03.16.
 */
public class DecomposingSink<O, I extends O, C extends Iterable<I>> extends ChainedSink<C, O>
        implements CloneableSink<C, DecomposingSink<O, I, C>> {
  public DecomposingSink(Sink<? super O> target) {
    super(target);
  }

  @Override
  public void accept(C values, Container metadata) throws Exception {
    for (O each : values) {
      getTarget().accept(each, metadata);
    }
  }

  @Override
  public DecomposingSink<O, I, C> createClone() {
    return new DecomposingSink<>(targetForCloning());
  }

  @Override
  public void partialCommit(Container metadata) throws Exception {
    if (getTarget() instanceof CloneableSink) {
      ((CloneableSink<?, ?>) getTarget()).partialCommit(metadata);
    }
  }
}
