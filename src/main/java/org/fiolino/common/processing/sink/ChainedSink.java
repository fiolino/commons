package org.fiolino.common.processing.sink;

import org.fiolino.common.container.Container;

/**
 * Created by Michael Kuhlmann on 23.12.2015.
 */
public abstract class ChainedSink<T, U> implements Sink<T> {

    private final Sink<? super U> target;

    protected ChainedSink(Sink<? super U> target) {
        this.target = target;
    }

    public Sink<? super U> getTarget() {
        return target;
    }

    @Override
    public void commit(Container metadata) throws Exception {
        getTarget().commit(metadata);
    }

    protected Sink<? super U> targetForCloning() {
        return targetForCloning(getTarget());
    }

    public static <V> Sink<V> targetForCloning(Sink<V> target) {
        if (target instanceof CloneableSink) {
            return ((CloneableSink<V, ?>) target).createClone();
        }
        if (target instanceof ThreadsafeSink) {
            return target;
        }
        throw new IllegalStateException("Target " + target + " is neither thread safe nor cloneable!");
    }
}
