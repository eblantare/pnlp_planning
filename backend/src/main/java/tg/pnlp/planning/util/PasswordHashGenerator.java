package tg.pnlp.planning.util;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class PasswordHashGenerator {
    public static void main(String[] args) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(10);
        String hash = encoder.encode("admin123");

        System.out.println("#######################################");
        System.out.println("HASH A COPIER :");
        System.out.println(hash);
        System.out.println("#######################################");
        System.out.println("Longueur : " + hash.length());
        System.out.println("Verification : " + encoder.matches("admin123", hash));
        System.out.println("#######################################");
    }
}