package tg.pnlp.planning.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import tg.pnlp.planning.security.JwtAuthenticationFilter;

import java.util.List;

import static tg.pnlp.planning.security.ProfilCodeNormalizer.ADMIN;
import static tg.pnlp.planning.security.ProfilCodeNormalizer.SUPER_ADMIN;
import static tg.pnlp.planning.security.ProfilCodeNormalizer.PLANIFICATEUR;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
@Slf4j
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        log.info("🔐 Configuration de Spring Security");

        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                .authorizeHttpRequests(auth -> auth

                        // ============================================================
                        // ENDPOINTS PUBLICS (pas d'authentification)
                        // ============================================================
                        .requestMatchers("/auth/login").permitAll()
                        .requestMatchers("/auth/logout").permitAll()
                        .requestMatchers("/auth/register").permitAll()
                        .requestMatchers("/auth/password/**").permitAll()

                        // Swagger
                        .requestMatchers(
                                "/swagger-ui/**", "/swagger-ui.html",
                                "/v3/api-docs/**", "/swagger-resources/**", "/webjars/**"
                        ).permitAll()

                        // CORS preflight
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        // ============================================================
                        // PERMISSIONS DE L'UTILISATEUR → tout authentifié
                        // ============================================================
                        .requestMatchers("/permissions/**").authenticated()

                        // ============================================================
                        // ✅ LECTURE : accessible à TOUS les authentifiés
                        //    (nécessaire pour le Dashboard et la page Planning)
                        // ============================================================
                        .requestMatchers(HttpMethod.GET, "/planning/**").authenticated()
                        .requestMatchers(HttpMethod.GET, "/activites/**").authenticated()
                        .requestMatchers(HttpMethod.GET, "/agents").authenticated()
                        .requestMatchers(HttpMethod.GET, "/agents/**").authenticated()
                        .requestMatchers(HttpMethod.GET, "/profils/actifs").authenticated()
                        .requestMatchers(HttpMethod.GET, "/profils").authenticated()
                        .requestMatchers(HttpMethod.GET, "/statistiques/**").authenticated()

                        // ============================================================
                        // 🟥 ÉCRITURE PLANNING : SUPER_ADMIN, ADMIN, PLANIFICATEUR
                        // ============================================================
                        .requestMatchers(HttpMethod.POST, "/planning/**")
                        .hasAnyRole(SUPER_ADMIN, ADMIN, PLANIFICATEUR)

                        .requestMatchers(HttpMethod.PUT, "/planning/**")
                        .hasAnyRole(SUPER_ADMIN, ADMIN, PLANIFICATEUR)

                        .requestMatchers(HttpMethod.PATCH, "/planning/**")
                        .hasAnyRole(SUPER_ADMIN, ADMIN, PLANIFICATEUR)

                        .requestMatchers(HttpMethod.DELETE, "/planning/**")
                        .hasAnyRole(SUPER_ADMIN, ADMIN, PLANIFICATEUR)

                        // ============================================================
                        // 🟥 ÉCRITURE AGENTS : SUPER_ADMIN ou ADMIN
                        // ============================================================
                        .requestMatchers(HttpMethod.POST, "/agents/**")
                        .hasAnyRole(SUPER_ADMIN, ADMIN)

                        .requestMatchers(HttpMethod.PUT, "/agents/**")
                        .hasAnyRole(SUPER_ADMIN, ADMIN)

                        .requestMatchers(HttpMethod.PATCH, "/agents/**")
                        .hasAnyRole(SUPER_ADMIN, ADMIN)

                        .requestMatchers(HttpMethod.DELETE, "/agents/**")
                        .hasAnyRole(SUPER_ADMIN, ADMIN)

                        // ============================================================
                        // 🟥 PROFILS : écriture = SUPER_ADMIN uniquement
                        // ============================================================
                        .requestMatchers(HttpMethod.POST, "/profils/**").hasRole(SUPER_ADMIN)
                        .requestMatchers(HttpMethod.PUT, "/profils/**").hasRole(SUPER_ADMIN)
                        .requestMatchers(HttpMethod.PATCH, "/profils/**").hasRole(SUPER_ADMIN)
                        .requestMatchers(HttpMethod.DELETE, "/profils/**").hasRole(SUPER_ADMIN)

                        // ============================================================
                        // 🟥 UTILISATEURS : SUPER_ADMIN uniquement
                        // ============================================================
                        .requestMatchers("/utilisateurs/**").hasRole(SUPER_ADMIN)

                        // ============================================================
                        // ✅ Tout le reste : authentifié
                        // ============================================================
                        .anyRequest().authenticated()
                )

                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        log.info("✅ Spring Security configuré");
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of("http://localhost:4200"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("Authorization"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}