package org.example.authservice;

import org.example.grpc.UserServiceGrpc;
import org.example.grpc.VerifyCredentialsRequest;
import org.example.grpc.VerifyCredentialsResponse;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
public class UserServiceAuthenticationProvider implements AuthenticationProvider {

    private final UserServiceGrpc.UserServiceBlockingStub stub;

    public UserServiceAuthenticationProvider(UserServiceGrpc.UserServiceBlockingStub stub) {
        this.stub = stub;
    }

    @Override
    public Authentication authenticate(Authentication authentication) {
        String username = authentication.getName();
        String password = authentication.getCredentials().toString();

        VerifyCredentialsResponse response = stub.verifyCredentials(
                VerifyCredentialsRequest.newBuilder()
                        .setUsername(username)
                        .setPassword(password)
                        .build());

        if (!response.getValid()) {
            throw new BadCredentialsException("Invalid credentials");
        }

        String role = response.getRole().isEmpty() ? "USER" : response.getRole();
        var authorities = List.of(
                new SimpleGrantedAuthority("ROLE_" + role),
                FactorGrantedAuthority.withAuthority(FactorGrantedAuthority.PASSWORD_AUTHORITY)
                        .issuedAt(Instant.now())
                        .build());
        // The principal's "username" becomes the JWT subject (sub) downstream. We pin it
        // to the stable user UUID so renames don't orphan their own messages, and so the
        // owner_user_id foreign-keyish reference in messageservice stays sound. The actual
        // display name is fetched on demand via userservice gRPC.
        var principal = User.withUsername(response.getId())
                .password("")
                .authorities(authorities)
                .build();

        return new UsernamePasswordAuthenticationToken(principal, password, authorities);
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
