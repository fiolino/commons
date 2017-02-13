package org.fiolino.common.processing.sink;

import org.fiolino.common.container.Container;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Collects entries from multiple aggregations.
 *
 * Created by kuli on 27.03.16.
 */
public final class MultiAggregatingSink<T> extends ThreadsafeChainedSink<List<T>, List<T>> {

  private final int chunkSize;
  private List<T> list;
  private final Lock lock = new ReentrantLock();

  public MultiAggregatingSink(ThreadsafeSink<List<T>> target, int chunkSize) {
    super(target);
    this.chunkSize = chunkSize;
    initializeList();
  }

  private void initializeList() {
    list = new ArrayList<>(chunkSize);
  }

  @Override
  public void accept(List<T> values, Container metadata) throws Exception {
    if (values.isEmpty()) {
      return;
    }
    values = new ArrayList<>(values);
    lock.lock();
    List<T> toSend;
    try {
      list.addAll(values);
      if (list.size() < chunkSize) {
        return;
      }
      toSend = list;
      initializeList();
    } finally {
      lock.unlock();
    }

    getTarget().accept(toSend, metadata);
  }

  @Override
  public void commit(Container metadata) throws Exception {
    List<T> toSend;
    lock.lock();
    try {
      toSend = list;
      initializeList();
    } finally {
      lock.unlock();
    }
    if (!toSend.isEmpty()) {
      getTarget().accept(toSend, metadata);
    }
    super.commit(metadata);
  }
}
