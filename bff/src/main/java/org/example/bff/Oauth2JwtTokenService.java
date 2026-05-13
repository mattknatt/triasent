package org.example.bff;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.stereotype.Service;

@Service
public class Oauth2JwtTokenService {

    private final OAuth2AuthorizedClientService authorizedClientService;

    public Oauth2JwtTokenService(OAuth2AuthorizedClientService authorizedClientService) {
        this.authorizedClientService = authorizedClientService;
    }

    // Denna metod hämtar token baserat på en skickad authentication
    public String getAccessToken(Authentication authentication) {
        if (authentication == null) return null;

        // Byt ut "my-client-id" mot det ID du har i application.yml under registration
        OAuth2AuthorizedClient client = authorizedClientService.loadAuthorizedClient(
                "authservice",
                authentication.getName()
        );

        return (client != null) ? client.getAccessToken().getTokenValue() : null;
    }
}