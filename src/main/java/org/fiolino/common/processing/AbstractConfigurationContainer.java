package org.fiolino.common.processing;

import org.fiolino.common.container.Container;

/**
 * Created by kuli on 25.11.15.
 */
abstract class AbstractConfigurationContainer implements ConfigurationContainer {

    private final Container configuration;

    AbstractConfigurationContainer(Container configuration) {
        this.configuration = configuration;
    }

    @Override
    public Container getConfiguration() {
        return configuration;
    }
}
