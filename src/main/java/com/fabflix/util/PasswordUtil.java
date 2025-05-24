package main.java.com.fabflix.util;

import org.mindrot.jbcrypt.BCrypt;

public class PasswordUtil {
    /**
     * 加密密码
     * @param plainPassword 明文密码
     * @return 加密后的密码
     */
    public static String encryptPassword(String plainPassword) {
        return BCrypt.hashpw(plainPassword, BCrypt.gensalt());
    }

    /**
     * 验证密码
     * @param plainPassword 明文密码
     * @param encryptedPassword 加密后的密码
     * @return 如果密码匹配返回true，否则返回false
     */
    public static boolean checkPassword(String plainPassword, String encryptedPassword) {
        try {
            return BCrypt.checkpw(plainPassword, encryptedPassword);
        } catch (Exception e) {
            System.err.println("Error checking password: " + e.getMessage());
            return false;
        }
    }
} 