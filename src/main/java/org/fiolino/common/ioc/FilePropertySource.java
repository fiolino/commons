package org.fiolino.common.ioc;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class FilePropertySource implements PropertySource {

    private Properties properties = new Properties();

    public void load(InputStream is) throws IOException {
        properties.load(is);
    }

    @Override
    public String getProperty(String key) {
        String sysval = System.getProperty(key);
        if (sysval != null) {
            return sysval;
        }
        return properties.getProperty(key);
    }

    public void setPropery(String key, String value) {
        properties.setProperty(key, value);
    }
}
