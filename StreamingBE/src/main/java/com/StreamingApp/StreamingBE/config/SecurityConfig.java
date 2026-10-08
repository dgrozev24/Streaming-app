package com.StreamingApp.StreamingBE.config;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import static org.springframework.security.config.Customizer.withDefaults;
import com.StreamingApp.StreamingBE.accounts.AccountService;
import com.StreamingApp.StreamingBE.accounts.BearerFilter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, AccountService accounts) throws Exception {
        // Only explicit bearer tokens authenticate requests; no cookie or HTTP Basic authentication.
        return http.cors(withDefaults()).csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(errors -> errors.authenticationEntryPoint((req, res, ex) -> res.sendError(401)))
                .addFilterBefore(new BearerFilter(accounts), UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login").permitAll()
                .requestMatchers("/api/account", "/api/account/**", "/api/auth/logout").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/movies", "/api/movies/*", "/api/movies/*/playlist.m3u8").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/audiobooks", "/api/audiobooks/*", "/api/audiobooks/*/chapters/*/audio").permitAll()
                .requestMatchers(HttpMethod.HEAD, "/api/audiobooks/*/chapters/*/audio").permitAll()
                .requestMatchers("/error").permitAll()
                .anyRequest().denyAll())
                .build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origins:http://localhost:8090,http://localhost:8081}") List<String> origins) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(origins);
        config.setAllowedMethods(List.of("GET", "HEAD", "OPTIONS", "POST", "PUT", "DELETE"));
        config.setAllowedHeaders(List.of("Accept", "Content-Type", "Authorization", "Range", "If-Range"));
        config.setExposedHeaders(List.of("Accept-Ranges", "Content-Range", "Content-Length"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
