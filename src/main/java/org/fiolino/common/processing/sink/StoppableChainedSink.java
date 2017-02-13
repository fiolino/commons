package org.fiolino.common.processing.sink;

import org.fiolino.common.container.Container;

/**
 * Created by kuli on 31.03.16.
 */
public class StoppableChainedSink<T> extends ChainedSink<T, T> implements StoppableSink<T> {
  private volatile boolean stopped;

  public StoppableChainedSink(Sink<T> target) {
    super(target);
  }

  @Override
  public final void accept(T value, Container metadata) throws Exception {
    checkStoppedState();
    doAccept(value, metadata);
  }

  protected void doAccept(T value, Container metadata) throws Exception {
    getTarget().accept(value, metadata);
  }

  protected void checkStoppedState() {
    if (stopped) {
      throw new IllegalStateException("Sink is already stopped!");
    }
  }

  @Override
  public final void commit(Container metadata) throws Exception {
    checkStoppedState();
    doCommit(metadata);
  }

  protected void doCommit(Container metadata) throws Exception {
    super.commit(metadata);
  }

  @Override
  public final void stop() throws InterruptedException {
    stopped = true;
    doStop();
  }

  protected void doStop() throws InterruptedException {
    // By default do nothing
  }

  public boolean isStopped() {
    return stopped;
  }
}
