package com.tripweaver.config;

import com.tripweaver.common.ApiError;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import tools.jackson.databind.json.JsonMapper;

/**
 * Trips need a signed-in user; everything else (catalogue, stateless planners, shared links) is public. Per-trip roles
 * are checked in {@code TripService} on every operation.
 *
 * <p>CSRF tokens are off: the API takes JSON only and the session cookie is SameSite=Lax, so browsers do not send it on
 * cross-site POST/PATCH/DELETE.
 */
@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, JsonMapper json) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(a -> a.requestMatchers("/api/v1/trips/**", "/api/v1/trips", "/api/v1/auth/me")
                        .authenticated()
                        .anyRequest()
                        .permitAll())
                .exceptionHandling(e -> e.authenticationEntryPoint((req, res, ex) -> {
                    res.setStatus(401);
                    res.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    json.writeValue(res.getOutputStream(), new ApiError("UNAUTHENTICATED", "Sign in first", Map.of()));
                }))
                .httpBasic(b -> b.disable())
                .formLogin(f -> f.disable())
                .logout(l -> l.disable());
        return http.build();
    }
}
