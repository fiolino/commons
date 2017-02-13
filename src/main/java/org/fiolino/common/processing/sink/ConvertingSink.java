package org.fiolino.common.processing.sink;

import org.fiolino.common.container.Container;

/**
 * Created by kuli on 04.01.16.
 */
public abstract class ConvertingSink<S, T> extends ChainedSink<S, T> {
  public ConvertingSink(Sink<? super T> target) {
    super(new NullFilterSink<>(target));
  }

  /**
   * Converts the element to another type.
   *
   * @param element The element that should be added
   * @return The new element, or null if it shall be skipped
   */
  protected abstract T convert(S element, Container metadata) throws Exception;

  @Override
  public final void accept(S value, Container metadata) throws Exception {
    T newOne = convert(value, metadata);
    getTarget().accept(newOne, metadata);
  }
}
