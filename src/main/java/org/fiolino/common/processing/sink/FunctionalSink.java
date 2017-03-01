package org.fiolino.common.processing.sink;

import org.fiolino.common.container.Container;

import java.util.function.Function;

/**
 * Created by Michael Kuhlmann on 23.12.2015.
 */
public final class FunctionalSink<T, U> extends ConvertingSink<T, U>
        implements ThreadsafeSink<T>, CloneableSink<T, FunctionalSink<T, U>> {

    private final Function<T, ? extends U> converter;

    public FunctionalSink(Sink<? super U> target, Function<T, ? extends U> converter) {
        super(target);
        this.converter = converter;
    }

    @Override
    protected U convert(T element, Container metadata) throws Exception {
        return converter.apply(element);
    }

    @Override
    public void partialCommit(Container metadata) throws Exception {
        if (getTarget() instanceof CloneableSink) {
            ((CloneableSink<?, ?>) getTarget()).partialCommit(metadata);
        }
    }

    @Override
    public FunctionalSink<T, U> createClone() {
        return new FunctionalSink<>(targetForCloning(getTarget()), converter);
    }
}
