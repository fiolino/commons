package org.fiolino.common.analyzing;

import org.fiolino.common.processing.ModelDescription;

import java.lang.invoke.MethodHandles;

/**
 * Created by Michael Kuhlmann on 15.12.2015.
 */
public abstract class Analyzeable {

    protected MethodHandles.Lookup getLookup() {
        return MethodHandles.lookup();
    }

    protected void preAnalyze(ModelDescription modelDescription) {
        // Do nothing
    }

    protected void postAnalyze(ModelDescription modelDescription) {
        // Do nothing
    }
}
