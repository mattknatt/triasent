package org.example.messageservice.client;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
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
import java.util.concurrent.TimeUnit;

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
                result.put(username, userStub.withDeadlineAfter(500, TimeUnit.MILLISECONDS).getUserByUsername(
                        GetUserByUsernameRequest.newBuilder().setUsername(username).build()));
            } catch (StatusRuntimeException e) {
                if (e.getStatus().getCode() == Status.Code.NOT_FOUND) {
                    log.debug("No userservice profile for: '{}'", username);
                } else {
                    log.warn("Failed to get user profile for: '{}', {}", username, e.getStatus(), e);
                }

            }
        }
        return result;
    }
}
