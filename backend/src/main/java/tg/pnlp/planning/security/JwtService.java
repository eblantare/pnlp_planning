package tg.pnlp.planning.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tg.pnlp.planning.entity.Profil;
import tg.pnlp.planning.entity.Utilisateur;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

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
     * ✅ Génère un JWT avec la liste COMPLÈTE des profils.
     */
    public String genererToken(Utilisateur user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", user.getId().toString());
        claims.put("username", user.getUsername());

        // ✅ Liste des codes de profils (ex: ["ADMIN", "COORDINATEUR"])
        List<String> profilCodes = user.getProfils() != null
                ? user.getProfils().stream()
                .map(Profil::getCode)
                .sorted()
                .collect(Collectors.toList())
                : Collections.emptyList();
        claims.put("profils", profilCodes);

        // ✅ Liste des libellés (pour affichage éventuel)
        List<String> profilLibelles = user.getProfils() != null
                ? user.getProfils().stream()
                .map(Profil::getLibelle)
                .sorted()
                .collect(Collectors.toList())
                : Collections.emptyList();
        claims.put("profilLibelles", profilLibelles);

        // ✅ Compatibilité : garde le 1er profil (ancien comportement)
        if (!profilCodes.isEmpty()) {
            claims.put("profilCode", profilCodes.get(0));
        }

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

    public Claims validerToken(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String extraireUsername(String token) {
        return validerToken(token).getSubject();
    }

    public UUID extraireUserId(String token) {
        String userIdStr = validerToken(token).get("userId", String.class);
        return UUID.fromString(userIdStr);
    }

    /**
     * ✅ NOUVEAU : extraire la liste des rôles/profils.
     */
    @SuppressWarnings("unchecked")
    public List<String> extraireProfils(String token) {
        Object profils = validerToken(token).get("profils");
        if (profils instanceof List<?>) {
            return (List<String>) profils;
        }
        return Collections.emptyList();
    }

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