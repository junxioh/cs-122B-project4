package main.java.com.fabflix.util;

import java.io.InputStream;
import java.util.Properties;

public class ConfigManager {
    private static final Properties properties = new Properties();
    
    static {
        try (InputStream input = ConfigManager.class.getClassLoader().getResourceAsStream("config.properties")) {
            if (input == null) {
                throw new RuntimeException("Unable to find config.properties");
            }
            properties.load(input);
        } catch (Exception e) {
            throw new RuntimeException("Error loading config.properties", e);
        }
    }
    
    public static String getProperty(String key) {
        return properties.getProperty(key);
    }
    
    public static String getRecaptchaSiteKey() {
        return getProperty("recaptcha.site.key");
    }
    
    public static String getRecaptchaSecretKey() {
        return getProperty("recaptcha.secret.key");
    }
} 