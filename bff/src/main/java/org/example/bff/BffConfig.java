package org.example.bff;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
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

    @Bean
    SecurityFilterChain security(HttpSecurity http) {
        return http
                .authorizeHttpRequests(auth -> auth
                        .anyRequest().authenticated()
                )
                .csrf(csrf -> csrf.disable())
                // Enable OAuth2 login (for browser users)
                .oauth2Login(Customizer.withDefaults())
                // Enable OAuth2 client (needed for tokenRelay)
                .oauth2Client(Customizer.withDefaults())
                .build();
    }

    @Bean
    public RouterFunction<ServerResponse> route1() {
        // /api/test -> http://localhost:8081/api/test
        return route()
                .GET("/api/test", http())
                .before(uri("http://localhost:8081/"))
                .filter(tokenRelay())
                .build();
    }

    @Bean
    public RouterFunction<ServerResponse> route2() {
        // /api/test2 -> http://localhost:8082/api/test
        return route()
                .GET("/api/test2", http())
                .before(uri("http://localhost:8082/"))
                .before(setPath("/api/test"))
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
                .before(uri("http://localhost:8083/"))
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