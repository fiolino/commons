package org.fiolino.common.processing.sink;

import org.fiolino.common.container.Container;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Created by kuli on 05.04.16.
 */
class MySink<T> implements ThreadsafeSink<T> {
    volatile T result;
    int finishCount;
    volatile boolean hasAccepted;

    @Override
    public void accept(T value, Container metadata) {
        if (hasAccepted) {
            fail("Sink was accepted twice!");
        }
        hasAccepted = true;
        result = value;
    }

    @Override
    public void commit(Container metadata) {
        finishCount++;
        hasAccepted = false;
    }
}
