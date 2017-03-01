package org.fiolino.common.processing.sink;

import java.util.ArrayList;
import java.util.List;

import org.fiolino.common.container.Container;

/**
 * This sink aggregates elements in a list and sends it then to the target.
 * <p>
 * The list is flushed when the given size is reached, or in case of a commit.
 * <p>
 * This class is not thread safe.
 * <p>
 * Created by kuli on 27.03.16.
 */
public final class AggregatingSink<T> extends ChainedSink<T, List<T>> implements CloneableSink<T, AggregatingSink<T>> {

    private List<T> list;
    private final int chunkSize;
    private Container metadata = Container.empty();

    public AggregatingSink(Sink<? super List<T>> target, int chunkSize) {
        super(target);
        this.chunkSize = chunkSize;
        initializeList();
    }

    private void initializeList() {
        list = new ArrayList<>(chunkSize);
    }

    @Override
    public void accept(T value, Container metadata) throws Exception {
        list.add(value);
        if (list.size() >= chunkSize) {
            flush();
        } else {
            this.metadata = metadata;
        }
    }

    @Override
    public void commit(Container metadata) throws Exception {
        flush();
        super.commit(metadata);
    }

    @Override
    public void partialCommit(Container metadata) throws Exception {
        flush();
        if (getTarget() instanceof CloneableSink) {
            ((CloneableSink<?, ?>) getTarget()).partialCommit(metadata);
        }
    }

    private void flush() throws Exception {
        if (!list.isEmpty()) {
            getTarget().accept(list, metadata);
            initializeList();
            this.metadata = Container.empty();
        }
    }

    @Override
    public AggregatingSink<T> createClone() {
        return new AggregatingSink<>(targetForCloning(), chunkSize);
    }
}
