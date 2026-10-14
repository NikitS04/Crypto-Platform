package com.nikit.config;

import com.nikit.ratelimit.RateLimitFilter;
import com.nikit.ratelimit.RateLimiter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import java.util.Arrays;
import java.util.Collections;

@Configuration
public class AppConfig {

    @Value("${app.frontend.url}")
    private String frontendUrl;

    @Value("${app.rate-limit.enabled:true}")
    private boolean rateLimitEnabled;

    @Value("${app.rate-limit.auth.capacity:10}")
    private int authCapacity;

    @Value("${app.rate-limit.auth.refill-per-second:0.2}")
    private double authRefill;

    @Value("${app.rate-limit.api.capacity:60}")
    private int apiCapacity;

    @Value("${app.rate-limit.api.refill-per-second:2}")
    private double apiRefill;

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtProvider jwtProvider, RateLimiter rateLimiter)throws Exception{
        http.sessionManagement(management->management.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(Authorize->Authorize
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers("/actuator/**").hasRole("ADMIN")
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll())

                .addFilterBefore(new JwtTokenValidator(jwtProvider), BasicAuthenticationFilter.class)
                .csrf(csrf->csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()));

        if (rateLimitEnabled) {
            // Runs after JWT validation so signed-in users are limited per account rather than per IP
            http.addFilterAfter(new RateLimitFilter(rateLimiter,
                    new RateLimitFilter.Policy(authCapacity, authRefill),
                    new RateLimitFilter.Policy(apiCapacity, apiRefill)), JwtTokenValidator.class);
        }
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder(){
        return new BCryptPasswordEncoder();
    }

    private CorsConfigurationSource corsConfigurationSource(){
        return request -> {
            CorsConfiguration cfg = new CorsConfiguration();
            cfg.setAllowedOrigins(
                    Arrays.asList(
                            frontendUrl,
                            "http://localhost:5173",
                            "http://localhost:3000")
            );

            cfg.setAllowedMethods(Collections.singletonList("*"));
            cfg.setAllowCredentials(true);
            cfg.setExposedHeaders(Arrays.asList("Authorization"));
            cfg.setAllowedHeaders(Collections.singletonList("*"));
            cfg.setMaxAge(3600L);
            return cfg;
        };
    }

}
