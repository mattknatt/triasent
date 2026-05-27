package org.example.messageservice.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.grpc.GetUserByUsernameRequest;
import org.example.grpc.UserProfile;
import org.example.grpc.UserServiceGrpc;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

/**
 * gRPC client to userservice, used to enrich message reads with author info.
 *
 * <p>Each distinct username is looked up once. A missing user or any gRPC error is
 * tolerated (that author is simply left un-enriched) so listing messages never fails
 * because of the user service.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserServiceClient {

    private final UserServiceGrpc.UserServiceBlockingStub userStub;

    public Map<String, UserProfile> profilesByUsername(Collection<String> usernames) {
        Map<String, UserProfile> result = new HashMap<>();
        for (String username : new HashSet<>(usernames)) {
            try {
                result.put(username, userStub.getUserByUsername(
                        GetUserByUsernameRequest.newBuilder().setUsername(username).build()));
            } catch (RuntimeException e) {
                log.debug("No userservice profile for '{}': {}", username, e.getMessage());
            }
        }
        return result;
    }
}
