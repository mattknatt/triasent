package org.example.authservice;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;

/**
 * Two filter chains: one for the OAuth2/OIDC protocol endpoints, one for everything else
 * (form login + static assets). The first chain steers unauthenticated browsers to the
 * branded /login.html instead of Spring's default form.
 */
@Configuration
public class SecurityConfig {

    @Bean
    @Order(1)
    SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .oauth2AuthorizationServer(authorizationServer -> {
                    authorizationServer.oidc(Customizer.withDefaults());
                    // Scope this chain to the OAuth2/OIDC endpoints only — without this the
                    // chain matches `any request` and collides with the form-login chain.
                    http.securityMatcher(authorizationServer.getEndpointsMatcher());
                })
                .authorizeHttpRequests(a -> a.anyRequest().authenticated())
                .exceptionHandling(e -> e.defaultAuthenticationEntryPointFor(
                        new LoginUrlAuthenticationEntryPoint("/login.html"),
                        new MediaTypeRequestMatcher(MediaType.TEXT_HTML)));

        return http.build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/login.html", "/error", "/favicon.ico").permitAll()
                        .anyRequest().authenticated())
                // The login POST is a top-level form submit from a static page that can't embed
                // a CSRF token; disabling CSRF here mirrors the upstream Spring Authorization
                // Server samples for branded login.
                .csrf(csrf -> csrf.disable())
                // loginProcessingUrl defaults to loginPage in Spring Security, so without
                // this the auth filter would listen on POST /login.html instead of /login.
                .formLogin(f -> f.loginPage("/login.html").loginProcessingUrl("/login").permitAll())
                .build();
    }
}