package main.java.com.fabflix.util;

import org.mindrot.jbcrypt.BCrypt;

public class TestBCrypt {
    public static void main(String[] args) {
        String password = "classta";
        String hashed = BCrypt.hashpw(password, BCrypt.gensalt());
        System.out.println("Original password: " + password);
        System.out.println("Hashed password: " + hashed);
        
        // 验证密码
        boolean matches = BCrypt.checkpw(password, hashed);
        System.out.println("Password matches: " + matches);
        
        // 验证一个错误的密码
        boolean wrongMatches = BCrypt.checkpw("wrong", hashed);
        System.out.println("Wrong password matches: " + wrongMatches);
    }
} 