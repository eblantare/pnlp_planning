package tg.pnlp.planning.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // Désactiver CSRF (API REST)
                .csrf(csrf -> csrf.disable())

                // Activer CORS avec la configuration personnalisée
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // Désactiver la session (API stateless)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // Autoriser TOUS les endpoints pour le moment
                .authorizeHttpRequests(auth -> auth
                        .anyRequest().permitAll()
                )

                // Désactiver l'authentification par défaut
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable());

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        // Autoriser le frontend Angular
        config.setAllowedOrigins(Arrays.asList(
                "http://localhost:4200",
                "http://localhost:4201",
                "http://127.0.0.1:4200"
        ));

        // Autoriser toutes les méthodes HTTP
        config.setAllowedMethods(Arrays.asList(
                "GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "HEAD"
        ));

        // Autoriser tous les headers
        config.setAllowedHeaders(Arrays.asList("*"));

        // Autoriser les credentials
        config.setAllowCredentials(true);

        // Exposer les headers utiles
        config.setExposedHeaders(Arrays.asList("Authorization", "Content-Type"));

        // Durée de cache des preflight requests (1 heure)
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}