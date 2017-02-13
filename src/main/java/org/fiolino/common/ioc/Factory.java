package org.fiolino.common.ioc;

/**
 * Created by kuli on 26.03.15.
 */
public interface Factory<T> {

    T create() throws Exception;
}
