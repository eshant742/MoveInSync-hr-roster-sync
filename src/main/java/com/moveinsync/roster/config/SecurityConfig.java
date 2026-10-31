package com.moveinsync.roster.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security configuration for the HR Roster Sync API.
 *
 * <p>Implements role-based access control (RBAC) with three roles:</p>
 * <ul>
 *   <li><strong>ADMIN</strong> — Can ingest employee events, update transport fields,
 *       trigger reconciliation, and query employees.</li>
 *   <li><strong>OPERATOR</strong> — Can trigger and approve reconciliation runs,
 *       and access actuator endpoints for monitoring.</li>
 *   <li><strong>EMPLOYEE</strong> — Can query employee data (read-only).</li>
 * </ul>
 *
 * <p>Uses HTTP Basic authentication with stateless sessions for simplicity
 * in this case study. In production, this would be replaced with
 * OAuth2/JWT-based authentication.</p>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * Configures the HTTP security filter chain with RBAC rules.
     *
     * @param http the {@link HttpSecurity} builder
     * @return the configured {@link SecurityFilterChain}
     * @throws Exception if configuration fails
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            .authorizeHttpRequests(auth -> auth
                // Swagger / OpenAPI — permit all for development
                .requestMatchers(
                    "/swagger-ui/**",
                    "/swagger-ui.html",
                    "/v3/api-docs/**"
                ).permitAll()

                // Employee event ingestion — ADMIN only
                .requestMatchers(HttpMethod.POST, "/api/v1/employees/events")
                    .hasRole("ADMIN")

                // Transport field updates — ADMIN only
                .requestMatchers(HttpMethod.PUT, "/api/v1/employees/transport-fields")
                    .hasRole("ADMIN")

                // Reconciliation approval — OPERATOR only
                .requestMatchers(HttpMethod.POST, "/api/v1/reconciliation/approve")
                    .hasRole("OPERATOR")

                // Reconciliation operations — ADMIN or OPERATOR
                .requestMatchers(HttpMethod.POST, "/api/v1/reconciliation/**")
                    .hasAnyRole("ADMIN", "OPERATOR")

                // Employee queries — ADMIN or EMPLOYEE
                .requestMatchers(HttpMethod.GET, "/api/v1/employees/**")
                    .hasAnyRole("ADMIN", "EMPLOYEE")

                // Operations endpoints — ADMIN or OPERATOR
                .requestMatchers("/api/v1/operations/**")
                    .hasAnyRole("ADMIN", "OPERATOR")

                // Actuator endpoints — OPERATOR only
                .requestMatchers("/actuator/**")
                    .hasRole("OPERATOR")

                // All other requests require authentication
                .anyRequest().authenticated()
            )
            .httpBasic(Customizer.withDefaults());

        return http.build();
    }

    /**
     * Provides in-memory user details for the three RBAC roles.
     * Passwords can be overridden via environment variables or application properties.
     *
     * @param passwordEncoder the password encoder to hash passwords
     * @param adminPass       admin user password (default: admin123)
     * @param operatorPass    operator user password (default: operator123)
     * @param employeePass    employee user password (default: employee123)
     * @return the configured {@link UserDetailsService}
     */
    @Bean
    public UserDetailsService userDetailsService(
            PasswordEncoder passwordEncoder,
            @org.springframework.beans.factory.annotation.Value("${roster.auth.admin-key:admin_access_key}") String adminKey,
            @org.springframework.beans.factory.annotation.Value("${roster.auth.operator-key:operator_access_key}") String operatorKey,
            @org.springframework.beans.factory.annotation.Value("${roster.auth.employee-key:employee_access_key}") String employeeKey) {

        UserDetails admin = User.builder()
                .username("admin")
                .password(passwordEncoder.encode(adminKey))
                .roles("ADMIN")
                .build();

        UserDetails operator = User.builder()
                .username("operator")
                .password(passwordEncoder.encode(operatorKey))
                .roles("OPERATOR")
                .build();

        UserDetails employee = User.builder()
                .username("employee")
                .password(passwordEncoder.encode(employeeKey))
                .roles("EMPLOYEE")
                .build();

        return new InMemoryUserDetailsManager(admin, operator, employee);
    }

    /**
     * Provides a BCrypt password encoder for hashing user passwords.
     *
     * @return the {@link PasswordEncoder} instance
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
