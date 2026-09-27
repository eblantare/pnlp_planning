package tg.pnlp.planning.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tg.pnlp.planning.entity.Utilisateur;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
public class JwtService {

    private final SecretKey secretKey;
    private final long expirationMs;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiration-ms}") long expirationMs) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    /**
     * Génère un token JWT pour un utilisateur
     */
    public String genererToken(Utilisateur user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", user.getId().toString());
        claims.put("username", user.getUsername());
        claims.put("profilCode", user.getProfil().getCode());
        claims.put("profilLibelle", user.getProfil().getLibelle());

        Date now = new Date();
        Date expiration = new Date(now.getTime() + expirationMs);

        return Jwts.builder()
                .claims(claims)
                .subject(user.getUsername())
                .issuedAt(now)
                .expiration(expiration)
                .signWith(secretKey, SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * Valide un token et retourne les claims
     */
    public Claims validerToken(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Extrait le username du token
     */
    public String extraireUsername(String token) {
        return validerToken(token).getSubject();
    }

    /**
     * Extrait l'userId du token
     */
    public UUID extraireUserId(String token) {
        String userIdStr = validerToken(token).get("userId", String.class);
        return UUID.fromString(userIdStr);
    }

    /**
     * Vérifie si le token est valide
     */
    public boolean estValide(String token) {
        try {
            validerToken(token);
            return true;
        } catch (Exception e) {
            log.warn("Token JWT invalide: {}", e.getMessage());
            return false;
        }
    }

    public long getExpirationMs() {
        return expirationMs;
    }
}