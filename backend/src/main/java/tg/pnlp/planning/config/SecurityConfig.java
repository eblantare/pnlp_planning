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
import static tg.pnlp.planning.security.ProfilCodeNormalizer.VALIDATEUR_NIVEAU_1;
import static tg.pnlp.planning.security.ProfilCodeNormalizer.VALIDATEUR_NIVEAU_2;
import static tg.pnlp.planning.security.ProfilCodeNormalizer.VALIDATEUR_NIVEAU_3;

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
                        // ENDPOINTS PUBLICS
                        // ============================================================
                        .requestMatchers("/auth/login").permitAll()
                        .requestMatchers("/auth/logout").permitAll()
                        .requestMatchers("/auth/register").permitAll()
                        .requestMatchers("/auth/password/**").permitAll()

                        .requestMatchers(
                                "/swagger-ui/**", "/swagger-ui.html",
                                "/v3/api-docs/**", "/swagger-resources/**", "/webjars/**"
                        ).permitAll()

                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        // ============================================================
                        // PERMISSIONS
                        // ============================================================
                        .requestMatchers("/permissions/**").authenticated()

                        // ============================================================
                        // ✅ VALIDATION — DOIT ÊTRE AVANT /planning/**
                        // URLs : /planning/validation/**
                        // ============================================================
                        .requestMatchers(HttpMethod.GET, "/planning/validation/**")
                        .hasAnyRole(SUPER_ADMIN, ADMIN, VALIDATEUR_NIVEAU_1, VALIDATEUR_NIVEAU_2, VALIDATEUR_NIVEAU_3)

                        .requestMatchers(HttpMethod.POST, "/planning/validation/**")
                        .hasAnyRole(SUPER_ADMIN, ADMIN, VALIDATEUR_NIVEAU_1, VALIDATEUR_NIVEAU_2, VALIDATEUR_NIVEAU_3)

                        // ============================================================
                        // ✅ LECTURE PLANNING : tous les authentifiés
                        // ============================================================
                        .requestMatchers(HttpMethod.GET, "/planning/**").authenticated()
                        .requestMatchers(HttpMethod.GET, "/activites/**").authenticated()
                        .requestMatchers(HttpMethod.GET, "/agents").authenticated()
                        .requestMatchers(HttpMethod.GET, "/agents/**").authenticated()
                        .requestMatchers(HttpMethod.GET, "/profils/actifs").authenticated()
                        .requestMatchers(HttpMethod.GET, "/profils").authenticated()
                        .requestMatchers(HttpMethod.GET, "/statistiques/**").authenticated()

                        // ============================================================
                        // 🟥 ÉCRITURE PLANNING
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
                        // 🟥 ÉCRITURE AGENTS
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
                        // 🟥 PROFILS
                        // ============================================================
                        .requestMatchers(HttpMethod.POST, "/profils/**").hasRole(SUPER_ADMIN)
                        .requestMatchers(HttpMethod.PUT, "/profils/**").hasRole(SUPER_ADMIN)
                        .requestMatchers(HttpMethod.PATCH, "/profils/**").hasRole(SUPER_ADMIN)
                        .requestMatchers(HttpMethod.DELETE, "/profils/**").hasRole(SUPER_ADMIN)

                        // ============================================================
                        // 🟥 UTILISATEURS
                        // ============================================================
                        .requestMatchers("/utilisateurs/**").hasRole(SUPER_ADMIN)

                        // ============================================================
                        // ✅ Tout le reste
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

    @org.springframework.beans.factory.annotation.Value("${app.frontend-url:http://localhost:4200}")
    private String frontendUrl;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        // Liste explicite — pas de wildcard avec allowCredentials
        config.setAllowedOrigins(java.util.Arrays.asList(
                "http://localhost:4200",
                "https://pnlp-planning-frontend.onrender.com"
        ));

        config.setAllowedMethods(java.util.Arrays.asList(
                "GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"
        ));
        config.setAllowedHeaders(java.util.Arrays.asList("*"));
        config.setExposedHeaders(java.util.Arrays.asList("Authorization"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}