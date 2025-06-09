package common;

public class ConfigManager {
    private static final String RECAPTCHA_SITE_KEY = "your_recaptcha_site_key";
    private static final String RECAPTCHA_SECRET_KEY = "your_recaptcha_secret_key";

    public static String getRecaptchaSiteKey() {
        return RECAPTCHA_SITE_KEY;
    }

    public static String getRecaptchaSecretKey() {
        return RECAPTCHA_SECRET_KEY;
    }
} 