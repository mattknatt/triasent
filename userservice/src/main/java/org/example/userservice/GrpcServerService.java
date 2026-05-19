package org.example.userservice;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import org.example.grpc.GetUserRequest;
import org.example.grpc.UserProfile;
import org.example.grpc.UserServiceGrpc;
import org.example.userservice.model.UserEntity;
import org.example.userservice.repository.UserRepository;
import org.springframework.grpc.server.service.GrpcService;

import java.util.UUID;

@GrpcService
@RequiredArgsConstructor
public class GrpcServerService extends UserServiceGrpc.UserServiceImplBase {

    private final UserRepository userRepository;

    @Override
    public void getUserProfile(GetUserRequest request, StreamObserver<UserProfile> responseObserver) {
        UUID userId;
        try {
            userId = UUID.fromString(request.getUserId());
        } catch (IllegalArgumentException e) {
            responseObserver.onError(Status.INVALID_ARGUMENT.withDescription("Invalid user ID").asRuntimeException());
            return;
        }

        UserEntity user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            responseObserver.onError(Status.NOT_FOUND.withDescription("User not found").asRuntimeException());
            return;
        }

        UserProfile profile = UserProfile.newBuilder()
                .setId(user.getId().toString())
                .setUsername(user.getUsername())
                .setRole(user.getRole() != null ? user.getRole().name() : "")
                .build();

        responseObserver.onNext(profile);
        responseObserver.onCompleted();
    }
}
