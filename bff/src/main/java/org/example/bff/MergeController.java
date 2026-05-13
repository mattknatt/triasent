package org.example.bff;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.annotation.RegisteredOAuth2AuthorizedClient;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

@RestController
public class MergeController {

    @GetMapping("/api/merge")
    public String merge(@RegisteredOAuth2AuthorizedClient OAuth2AuthorizedClient client) {
        String jwtToken = client.getAccessToken().getTokenValue() ;

        var result1 = RestClient
                .create("http://localhost:8081/api/test")
                .get()
                .headers(h -> h.setBearerAuth(jwtToken))
                .retrieve().body(String.class);

        var result2 = RestClient
                .create("http://localhost:8082/api/test")
                .get()
                .headers(h -> h.setBearerAuth(jwtToken))
                .retrieve().body(String.class);

        return result1 + " " + result2;
    }

    @GetMapping("/api/merge2")
    public String merge2(@RegisteredOAuth2AuthorizedClient OAuth2AuthorizedClient client) {
        String jwtToken = client.getAccessToken().getTokenValue() ;

        var result1 = RestClient
                .create("http://localhost:8081/api/test")
                .get()
                .headers(h -> h.setBearerAuth(jwtToken))
                .retrieve().body(String.class);

        var result2 = RestClient
                .create("http://localhost:8083/api/test")
                .get()
                .headers(h -> h.set("X-User-Name", "demo"))
                .retrieve().body(String.class);

        return result1 + " " + result2;
    }
}