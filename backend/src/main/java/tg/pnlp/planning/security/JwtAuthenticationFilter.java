package tg.pnlp.planning.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        // ⚠️ Ici, le path CONTIENT le context-path /api
        //    Ex: /api/auth/login, /api/planning/activites, etc.
        String path = request.getRequestURI();

        // Si c'est un endpoint public, on laisse passer SANS vérifier le token
        if (isPublicEndpoint(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        // Sinon, on cherche le token
        String token = extraireToken(request);

        if (token != null && jwtService.estValide(token)) {
            try {
                Claims claims = jwtService.validerToken(token);
                String username = claims.getSubject();
                String profilCode = claims.get("profilCode", String.class);

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                username,
                                null,
                                List.of(new SimpleGrantedAuthority("ROLE_" + profilCode))
                        );
                authentication.setDetails(
                        new WebAuthenticationDetailsSource().buildDetails(request)
                );
                SecurityContextHolder.getContext().setAuthentication(authentication);

                log.debug("Utilisateur authentifié: {} ({})", username, profilCode);

            } catch (Exception e) {
                log.warn("Erreur lors de l'authentification: {}", e.getMessage());
            }
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Détermine si un endpoint est public (pas besoin d'authentification).
     * ⚠️ Le path contient le context-path /api.
     */
    private boolean isPublicEndpoint(String path) {
        return path.startsWith("/api/auth/login")
                || path.startsWith("/api/auth/logout")
                || path.startsWith("/api/auth/register")
                || path.startsWith("/api/auth/password")
                || path.startsWith("/api/swagger-ui")
                || path.startsWith("/api/v3/api-docs")
                || path.startsWith("/api/webjars");
    }

    private String extraireToken(HttpServletRequest request) {
        String bearer = request.getHeader("Authorization");
        if (bearer != null && bearer.startsWith("Bearer ")) {
            return bearer.substring(7);
        }
        return null;
    }
}