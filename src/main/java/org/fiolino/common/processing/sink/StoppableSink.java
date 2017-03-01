package org.fiolino.common.processing.sink;

/**
 * Created by micro on 5/30/2016.
 */
public interface StoppableSink<T> extends Sink<T> {
    /**
     * Stops the sink, so that all running threads (if any) are being stopped.
     * <p>
     * This sink won't accept any input thereafter.
     */
    void stop() throws InterruptedException;
}
