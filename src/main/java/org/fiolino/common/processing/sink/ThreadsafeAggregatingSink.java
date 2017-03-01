package org.fiolino.common.processing.sink;

import org.fiolino.common.container.Container;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Created by kuli on 27.03.16.
 */
public final class ThreadsafeAggregatingSink<T> extends ThreadsafeChainedSink<T, List<T>> {

    private static final Logger logger = LoggerFactory.getLogger(ThreadsafeAggregatingSink.class);

    private final BlockingQueue<T> queue;
    private final Lock lock = new ReentrantLock();
    private volatile Container lastMetadata = Container.empty();

    public ThreadsafeAggregatingSink(ThreadsafeSink<List<T>> target, int chunkSize) {
        super(target);
        queue = new ArrayBlockingQueue<T>(chunkSize);
    }

    @Override
    public void accept(T value, Container metadata) throws Exception {
        do {
            if (queue.offer(value)) {
                lastMetadata = metadata;
                return;
            }
            if (!lock.tryLock()) {
                // Spin wait
                try {
                    TimeUnit.MILLISECONDS.sleep(20);
                } catch (InterruptedException ex) {
                    logger.warn("Interrupted while waiting for queue. Discarding " + value);
                    Thread.currentThread().interrupt();
                    return;
                }
                continue;
            }
            List<T> list;
            try {
                // Test again - it could be cleaned in between by another thread
                if (queue.offer(value)) {
                    lastMetadata = metadata;
                    return;
                }
                list = new ArrayList<>(queue);
                queue.clear();
            } finally {
                lock.unlock();
            }
            getTarget().accept(list, metadata);
            // Then add again

        } while (true);
    }

    @Override
    public void commit(Container metadata) throws Exception {
        List<T> list = new ArrayList<>(queue);
        queue.removeAll(list);
        if (!list.isEmpty()) {
            getTarget().accept(list, lastMetadata);
        }
        lastMetadata = Container.empty();
        super.commit(metadata);
    }
}
