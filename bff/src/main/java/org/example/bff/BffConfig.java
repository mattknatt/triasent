package org.example.bff;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

import static org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.setPath;
import static org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.uri;
import static org.springframework.cloud.gateway.server.mvc.filter.TokenRelayFilterFunctions.tokenRelay;
import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.http;
import static org.springframework.web.servlet.function.RouterFunctions.route;

@Configuration
public class BffConfig {

    @Value("${messageservice.host:localhost}")
    private String messageserviceHost;

    @Value("${userservice.host:localhost}")
    private String userserviceHost;

    @Bean
    SecurityFilterChain security(HttpSecurity http, ClientRegistrationRepository clientRegistrationRepository) throws Exception {
        return http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/users").permitAll()
                        .anyRequest().authenticated()
                )
                .csrf(csrf -> csrf.disable())
                // OAuth2 login for browser users. Always return to the SPA root after login
                // (ignore the saved pre-login request, e.g. the SPA's /api/messages auth probe,
                // which would otherwise land the user on raw JSON).
                .oauth2Login(oauth2 -> oauth2.defaultSuccessUrl("/", true))
                // Enable OAuth2 client (needed for tokenRelay)
                .oauth2Client(Customizer.withDefaults())
                // RP-Initiated Logout: after clearing the local session, redirect the browser
                // to the authservice's end_session endpoint so its SSO cookie is cleared too.
                // Without this, the next login would silently reuse the existing auth-server
                // session and the user would never see the login form again.
                .logout(logout -> logout.logoutSuccessHandler(oidcLogoutSuccessHandler(clientRegistrationRepository)))
                .build();
    }

    private LogoutSuccessHandler oidcLogoutSuccessHandler(ClientRegistrationRepository clientRegistrationRepository) {
        OidcClientInitiatedLogoutSuccessHandler handler =
                new OidcClientInitiatedLogoutSuccessHandler(clientRegistrationRepository);
        handler.setPostLogoutRedirectUri("{baseUrl}/");
        return handler;
    }

    @Bean
    public RouterFunction<ServerResponse> usersRoute() {
        // POST /api/users -> http://userservice:8083/users (unauthenticated, for account creation)
        return route()
                .POST("/api/users", http())
                .before(uri("http://" + userserviceHost + ":8083/"))
                .before(setPath("/users"))
                .build();
    }

    @Bean
    public RouterFunction<ServerResponse> messagesRoute() {
        // /api/messages -> http://localhost:8081/messages (GET + POST)
        return route()
                .GET("/api/messages", http())
                .POST("/api/messages", http())
                .before(uri("http://" + messageserviceHost + ":8081/"))
                .before(setPath("/messages"))
                .filter(tokenRelay())
                .build();
    }
//    @Bean
//    public RouterFunction<ServerResponse> route1WithSetPathAndSegment() {
//        // /test -> http://localhost:8081/api/test
//        return route()
//                .GET("/{segment}", http())
//                .before(uri("http://localhost:8081/"))
//                .before(setPath("/api/{segment}"))
//                .filter(tokenRelay())
//                .build();
//    }

    /*
     Ett vanligt scenario när man vill förenkla för sina microservices så att de slipper packa upp JWT-tokenet själva
     är att istället för att använda tokenRelay(), som skickar vidare hela Authorization-headern, kan man använda
     en kombination av Springs säkerhetskontext och filtret addRequestHeader.
     */
    @Bean
    public RouterFunction<ServerResponse> routeWithUsername() {
        // /api/test -> http://localhost:8083/api/test
        return route()
                .GET("/api/test3", http())
                .before(uri("http://" + userserviceHost + ":8083/"))
                .before(setPath("/api/test"))
                .filter((request, next) -> {
                    // Hämta användarnamnet från Principal (Spring Security)
                    String username = request.servletRequest().getUserPrincipal() != null
                            ? request.servletRequest().getUserPrincipal().getName()
                            : "anonymous";
                    ServerRequest modifiedRequest = ServerRequest.from(request)
                            .headers(httpHeaders -> {
                                // .set ser till att eventuella headers från klienten raderas
                                // och ersätts helt av gatewayens verifierade användarnamn.
                                httpHeaders.set("X-User-Name", username);
                            })
                            .build();
                    return next.handle(modifiedRequest);
                })
                .build();
    }
}