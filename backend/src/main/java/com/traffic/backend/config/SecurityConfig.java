package com.traffic.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        // ⚠️ DISABLE SECURITY FOR DEVELOPMENT ONLY ⚠️
        // We will add proper login/auth in Step 7!
        
        http
            .csrf(csrf -> csrf.disable())  // Disable CSRF for testing
            .authorizeHttpRequests(auth -> auth
                .anyRequest().permitAll()   // Allow ALL requests without login
            );
        
        return http.build();
    }
}