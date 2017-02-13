package org.fiolino.common.processing;

import org.fiolino.common.ioc.PostProcessor;

/**
 * These entities process an existing model bean.
 * <p>
 * Created by kuli on 18.11.15.
 */
public interface Processor<S, T> {

  void process(S source, T model) throws Exception;

  default <X extends Processor<?, ?>> X find(Class<X> type) {
    return type.isInstance(this) ? type.cast(this) : null;
  }

  default Processor<S, T> andThen(Processor<? super S, ? super T> next) {
    return new MultiProcessor<>(this, next);
  }

  default Processor<S, T> beforeDo(Processor<S, T> previous) {
    return previous.andThen(this);
  }

  @SuppressWarnings("unchecked")
  static <S, T> Processor<S, T> doNothing() {
    return (Processor<S, T>) VoidProcessor.INSTANCE;
  }

  static <T> T postProcess(T instance) {
    if (instance instanceof PostProcessor) {
      ((PostProcessor) instance).postConstruct();
    }
    return instance;
  }

  class VoidProcessor<S, T> implements Processor<S, T> {
    private static final VoidProcessor<?, ?> INSTANCE = new VoidProcessor<>();

    private VoidProcessor() {
    }

    @Override
    public void process(S source, T model) {
      // Do nothing
    }

    @Override
    @SuppressWarnings("unchecked")
    public Processor<S, T> andThen(Processor<? super S, ? super T> next) {
      return (Processor<S, T>) next;
    }

    @Override
    public Processor<S, T> beforeDo(Processor<S, T> previous) {
      return previous;
    }
  }
}
