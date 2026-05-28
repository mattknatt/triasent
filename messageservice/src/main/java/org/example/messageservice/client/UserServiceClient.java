package org.example.messageservice.client;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.grpc.GetUserRequest;
import org.example.grpc.UserProfile;
import org.example.grpc.UserServiceGrpc;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * gRPC client to userservice, used to enrich message reads with author display info.
 *
 * <p>Each distinct user id is looked up once. A missing user or any gRPC error is
 * tolerated (that author is simply left un-enriched) so listing messages never fails
 * because of the user service.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserServiceClient {

    private final UserServiceGrpc.UserServiceBlockingStub userStub;

    public Map<UUID, UserProfile> profilesById(Collection<UUID> userIds) {
        Map<UUID, UserProfile> result = new HashMap<>();
        for (UUID userId : new HashSet<>(userIds)) {
            try {
                UserProfile profile = userStub.withDeadlineAfter(500, TimeUnit.MILLISECONDS)
                        .getUserProfile(GetUserRequest.newBuilder().setUserId(userId.toString()).build());
                result.put(userId, profile);
            } catch (StatusRuntimeException e) {
                if (e.getStatus().getCode() == Status.Code.NOT_FOUND) {
                    // Expected for the reserved bot user-id (no row in userservice) and for
                    // any users that were deleted after their messages were posted.
                    log.debug("No userservice profile for: '{}'", userId);
                } else {
                    log.warn("Failed to get user profile for: '{}', {}", userId, e.getStatus(), e);
                }
            }
        }
        return result;
    }
}
