package utils;

import org.mindrot.jbcrypt.BCrypt;

public class TestBCrypt {
    public static void main(String[] args) {
        String password = "classta";
        String hashed = BCrypt.hashpw(password, BCrypt.gensalt());
        System.out.println("Original password: " + password);
        System.out.println("Hashed password: " + hashed);

        // 楠岃瘉瀵嗙爜
        boolean matches = BCrypt.checkpw(password, hashed);
        System.out.println("Password matches: " + matches);

        // 楠岃瘉涓€涓敊璇殑瀵嗙爜
        boolean wrongMatches = BCrypt.checkpw("wrong", hashed);
        System.out.println("Wrong password matches: " + wrongMatches);
    }
}
