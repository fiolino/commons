package org.fiolino.common.processing;

import org.fiolino.common.container.Container;

/**
 * Created by kuli on 25.11.15.
 */
public abstract class AbstractModelDescriptionContainer implements ModelDescriptionContainer, ConfigurationContainer {

    private final ModelDescription modelDescription;

    protected AbstractModelDescriptionContainer(ModelDescription modelDescription) {
        this.modelDescription = modelDescription;
    }

    @Override
    public final ModelDescription getModelDescription() {
        return modelDescription;
    }

    @Override
    public final Container getConfiguration() {
        return getModelDescription().getConfiguration();
    }

    @Override
    public String toString() {
        return getClass().getName() + " for " + modelDescription;
    }

    @Override
    public boolean equals(Object obj) {
        return obj != null && obj.getClass().equals(getClass())
                && modelDescription.equals(((ModelDescriptionContainer) obj).getModelDescription());
    }

    @Override
    public int hashCode() {
        return modelDescription.hashCode() * 31 + getClass().hashCode();
    }
}
