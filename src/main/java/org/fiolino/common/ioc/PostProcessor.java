package org.fiolino.common.ioc;

/**
 * If beans implement this, then this solr method will be invoked after instantiation.
 * <p>
 * Created by kuli on 08.12.15.
 */
public interface PostProcessor {

    void postConstruct();
}
