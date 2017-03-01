package org.fiolino.common.processing.sink;

import org.fiolino.common.container.Container;

/**
 * Created by kuli on 20.01.16.
 */
public abstract class ModifyingSink<T> extends ChainedSink<T, T> {

    protected ModifyingSink(Sink<? super T> target) {
        super(target);
    }

    /**
     * Handles the individual element.
     */
    protected abstract void touch(T element, Container metadata) throws Exception;

    @Override
    public final void accept(T value, Container metadata) throws Exception {
        touch(value, metadata);

        getTarget().accept(value, metadata);
    }
}
