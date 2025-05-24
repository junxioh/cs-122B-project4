package main.java.com.fabflix.util;

public class GeneratePassword {
    public static void main(String[] args) {
        String password = "classta";
        String encryptedPassword = PasswordUtil.encryptPassword(password);
        System.out.println("Original password: " + password);
        System.out.println("Encrypted password: " + encryptedPassword);
        
        // 验证密码
        boolean matches = PasswordUtil.checkPassword(password, encryptedPassword);
        System.out.println("Password matches: " + matches);
    }
} 