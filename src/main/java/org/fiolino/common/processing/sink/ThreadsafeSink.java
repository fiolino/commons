package org.fiolino.common.processing.sink;

import javax.annotation.concurrent.ThreadSafe;

/**
 * A sink that is marked as being thread safe.
 *
 * Even for thread safe sinks, the caller must make sure that after finished() is being
 * called, no other thread is continuing to call accept().
 *
 * Created by kuli on 31.03.16.
 */
@ThreadSafe
public interface ThreadsafeSink<T> extends Sink<T> {
}
