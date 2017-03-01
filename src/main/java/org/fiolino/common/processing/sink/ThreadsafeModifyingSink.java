package org.fiolino.common.processing.sink;

import org.fiolino.common.container.Container;

/**
 * Created by kuli on 20.01.16.
 */
public abstract class ThreadsafeModifyingSink<T> extends ThreadsafeChainedSink<T, T> {

    protected ThreadsafeModifyingSink(ThreadsafeSink<T> target) {
        super(target);
    }

    /**
     * Handles the individual element.
     */
    protected abstract void touch(T element, Container metadata) throws Exception;

    @Override
    public void accept(T value, Container metadata) throws Exception {
        touch(value, metadata);

        getTarget().accept(value, metadata);
    }
}
