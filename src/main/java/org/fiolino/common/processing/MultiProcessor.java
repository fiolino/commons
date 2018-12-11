package org.fiolino.common.processing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Created by kuli on 25.11.15.
 */
public class MultiProcessor<S, T> implements Processor<S, T> {
    private final List<Processor<? super S, ? super T>> processors = new ArrayList<>();

    @SafeVarargs
    public MultiProcessor(Processor<? super S, ? super T>... initial) {
        Collections.addAll(processors, initial);
    }

    public void addProcessor(Processor<? super S, ? super T> processor) {
        processors.add(processor);
    }

    @Override
    public <X extends Processor<?, ?>> X find(Class<X> type) {
        for (Processor<? super S, ? super T> p : processors) {
            if (type.isInstance(p)) {
                return type.cast(p);
            }
        }
        return null;
    }

    @Override
    public Processor<S, T> andThen(Processor<? super S, ? super T> next) {
        addProcessor(next);
        return this;
    }

    @Override
    public Processor<S, T> beforeDo(Processor<S, T> previous) {
        processors.add(0, previous);
        return this;
    }

    @Override
    public void process(S source, T model) throws Exception {
        for (Processor<? super S, ? super T> p : processors) {
            p.process(source, model);
        }
    }
}
