package common;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import javax.crypto.spec.SecretKeySpec;
import javax.xml.bind.DatatypeConverter;
import java.security.Key;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

public class JwtUtil {
    private static final Logger logger = Logger.getLogger(JwtUtil.class.getName());
    private static final String SECRET_KEY = "fabflix_secret_key_2024";
    private static final long EXPIRATION_TIME = 86400000; // 24 hours

    static {
        try {
            // Test the JAXB functionality during class loading
            DatatypeConverter.parseBase64Binary(SECRET_KEY);
            logger.info("JAXB functionality verified during JwtUtil initialization");
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error initializing JAXB in JwtUtil", e);
        }
    }

    public static String generateJWT(String id, String email, String role) {
        try {
            SignatureAlgorithm signatureAlgorithm = SignatureAlgorithm.HS256;

            long nowMillis = System.currentTimeMillis();
            Date now = new Date(nowMillis);

            byte[] apiKeySecretBytes = DatatypeConverter.parseBase64Binary(SECRET_KEY);
            Key signingKey = new SecretKeySpec(apiKeySecretBytes, signatureAlgorithm.getJcaName());

            JwtBuilder builder = Jwts.builder()
                    .setId(id)
                    .setIssuedAt(now)
                    .setSubject(email)
                    .claim("role", role)
                    .signWith(signatureAlgorithm, signingKey);

            builder.setExpiration(new Date(nowMillis + EXPIRATION_TIME));
            
            String token = builder.compact();
            logger.info("Generated JWT token for user: " + email);
            return token;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error generating JWT token", e);
            throw new RuntimeException("Failed to generate JWT token", e);
        }
    }

    public static Claims decodeJWT(String jwt) {
        try {
            Claims claims = Jwts.parser()
                    .setSigningKey(DatatypeConverter.parseBase64Binary(SECRET_KEY))
                    .parseClaimsJws(jwt)
                    .getBody();
            logger.info("Decoded JWT token for user: " + claims.getSubject());
            return claims;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error decoding JWT token", e);
            throw e; // Re-throw to be handled by the caller
        }
    }
} 