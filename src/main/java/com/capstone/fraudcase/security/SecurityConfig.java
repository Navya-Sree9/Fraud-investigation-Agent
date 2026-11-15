package com.capstone.fraudcase.security;

import com.capstone.fraudcase.config.CapstoneProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Persona-based access (see {@link Roles}). UI uses form login with CSRF protection; the stateless JSON API uses
 * HTTP Basic and is exempt from CSRF.
 */
@Configuration
public class SecurityConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    UserDetailsService users(CapstoneProperties properties, PasswordEncoder encoder) {
        CapstoneProperties.Security s = properties.security();
        return new InMemoryUserDetailsManager(
                User.withUsername("agent").password(encoder.encode(s.agentPassword())).roles(Roles.SERVICE_AGENT).build(),
                User.withUsername("investigator").password(encoder.encode(s.investigatorPassword())).roles(Roles.INVESTIGATOR).build(),
                User.withUsername("reviewer").password(encoder.encode(s.reviewerPassword())).roles(Roles.REVIEWER).build(),
                User.withUsername("auditor").password(encoder.encode(s.auditorPassword())).roles(Roles.AUDITOR).build());
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(auth -> auth
                        .requestMatchers("/login", "/css/**", "/js/**", "/favicon.ico", "/error", "/actuator/health/**", "/actuator/health").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/cases", "/cases").hasAnyRole(Roles.SERVICE_AGENT, Roles.INVESTIGATOR)
                        .requestMatchers(HttpMethod.POST, "/api/cases/*/investigations", "/cases/*/investigate").hasRole(Roles.INVESTIGATOR)
                        .requestMatchers(HttpMethod.POST, "/api/cases/*/messages", "/cases/*/messages").hasAnyRole(Roles.INVESTIGATOR, Roles.REVIEWER)
                        .requestMatchers(HttpMethod.POST, "/api/cases/*/reviews", "/api/cases/*/reviews/start", "/cases/*/review", "/cases/*/review/start")
                                .hasRole(Roles.REVIEWER)
                        .requestMatchers("/api/cases/*/audit", "/cases/*/audit", "/audit").hasAnyRole(Roles.AUDITOR, Roles.REVIEWER)
                        .requestMatchers("/actuator/**").hasRole(Roles.AUDITOR)
                        .anyRequest().authenticated())
                .formLogin(form -> form.loginPage("/login").defaultSuccessUrl("/cases", true).permitAll())
                .logout(logout -> logout.logoutSuccessUrl("/login?logout"))
                .httpBasic(Customizer.withDefaults())
                .csrf(csrf -> csrf.ignoringRequestMatchers("/api/**"))
                .headers(h -> h
                        .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'self'; style-src 'self'; script-src 'self'; img-src 'self' data:; frame-ancestors 'none'"))
                        .frameOptions(f -> f.deny()));
        return http.build();
    }
}
