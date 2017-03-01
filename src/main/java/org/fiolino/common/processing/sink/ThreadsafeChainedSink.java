package org.fiolino.common.processing.sink;

import javax.annotation.concurrent.ThreadSafe;

/**
 * Created by kuli on 31.03.16.
 */
@ThreadSafe
public abstract class ThreadsafeChainedSink<S, T> extends ChainedSink<S, T> implements ThreadsafeSink<S> {
    ThreadsafeChainedSink(ThreadsafeSink<T> target) {
        super(target);
    }

    @Override
    @SuppressWarnings("unchecked")
    public ThreadsafeSink<T> getTarget() {
        return (ThreadsafeSink<T>) super.getTarget();
    }
}
