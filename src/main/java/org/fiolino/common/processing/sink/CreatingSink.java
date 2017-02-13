package org.fiolino.common.processing.sink;

import java.util.concurrent.TimeUnit;

import org.fiolino.common.container.Container;
import org.fiolino.common.processing.BeanCreator;
import org.fiolino.common.util.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by kuli on 29.12.15.
 */
public final class CreatingSink<T, U> extends ConvertingSink<T, U> implements CloneableSink<T, CreatingSink<T, U>> {

  private static final Logger logger = LoggerFactory.getLogger(CreatingSink.class);

  //@ThreadSafe
  private final BeanCreator<? super T, ? extends U> beanCreator;
  private final long maximumNanoSeconds;

  public CreatingSink(Sink<? super U> target, BeanCreator<? super T, ? extends U> beanCreator,
                      long maximumDuration, TimeUnit durationUnit) {
    this(target, beanCreator, durationUnit.toNanos(maximumDuration));
  }

  private CreatingSink(Sink<? super U> target, BeanCreator<? super T, ? extends U> beanCreator,
                      long maximumNanoSeconds) {
    super(target);
    this.beanCreator = beanCreator;
    this.maximumNanoSeconds = maximumNanoSeconds;
  }

  @Override
  protected U convert(T element, Container metadata) throws Exception {
    long start = System.nanoTime();
    U result = beanCreator.getFor(element);
    long duration = System.nanoTime() - start;
    if (duration > maximumNanoSeconds) {
      logger.warn("Converting value took unusually long: " + Strings.printDuration(duration, TimeUnit.MILLISECONDS));
      logger.warn("Input value was " + element);
    }

    return result;
  }

  @Override
  public CreatingSink<T, U> createClone() {
    return new CreatingSink<>(targetForCloning(), beanCreator, maximumNanoSeconds);
  }

  @Override
  public void partialCommit(Container metadata) throws Exception {
    if (getTarget() instanceof CloneableSink) {
      ((CloneableSink<?, ?>) getTarget()).partialCommit(metadata);
    }
  }
}
