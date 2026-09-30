package net.storm.api.plugins.config;

/**
 * Minimal Storm-compat config manager (backed by RuneLite ConfigManager in client).
 */
public interface ConfigManager {
    void setConfiguration(String group, String key, Object value);

    String getConfiguration(String group, String key);

    default boolean getBoolean(String group, String key, boolean def) {
        String v = getConfiguration(group, key);
        if (v == null || v.isEmpty()) {
            return def;
        }
        return Boolean.parseBoolean(v);
    }
}
