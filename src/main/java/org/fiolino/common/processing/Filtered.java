package org.fiolino.common.processing;

/**
 * Created by kuli on 24.03.15.
 */
public enum Filtered {
    NEVER(false),
    NO(false),
    YES(true);

    private final boolean filtered;

    Filtered(boolean filtered) {
        this.filtered = filtered;
    }

    public boolean isFiltered() {
        return filtered;
    }
}
