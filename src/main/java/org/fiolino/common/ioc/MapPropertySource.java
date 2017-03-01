package org.fiolino.common.ioc;

import java.util.HashMap;
import java.util.Map;

public class MapPropertySource implements PropertySource {

    private Map<String, String> properties = new HashMap<>();

    public MapPropertySource(Map<String, String> properties) {
        this.properties = properties;
    }

    @Override
    public String getProperty(String key) {
        return properties.get(key);
    }

}
