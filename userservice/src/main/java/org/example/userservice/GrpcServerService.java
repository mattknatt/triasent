package org.example.userservice;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import org.example.grpc.GetUserByUsernameRequest;
import org.example.grpc.GetUserRequest;
import org.example.grpc.UserProfile;
import org.example.grpc.UserServiceGrpc;
import org.example.grpc.VerifyCredentialsRequest;
import org.example.grpc.VerifyCredentialsResponse;
import org.example.userservice.model.UserEntity;
import org.example.userservice.repository.UserRepository;
import org.springframework.grpc.server.service.GrpcService;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.UUID;

@GrpcService
@RequiredArgsConstructor
public class GrpcServerService extends UserServiceGrpc.UserServiceImplBase {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

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

    @Override
    public void getUserByUsername(GetUserByUsernameRequest request, StreamObserver<UserProfile> responseObserver) {
        UserEntity user = userRepository.findByUsername(request.getUsername());
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

    @Override
    public void verifyCredentials(VerifyCredentialsRequest request, StreamObserver<VerifyCredentialsResponse> responseObserver) {
        UserEntity user = userRepository.findByUsername(request.getUsername());
        boolean valid = user != null && passwordEncoder.matches(request.getPassword(), user.getPassword());

        VerifyCredentialsResponse.Builder response = VerifyCredentialsResponse.newBuilder().setValid(valid);
        if (valid) {
            response.setId(user.getId().toString())
                    .setUsername(user.getUsername())
                    .setRole(user.getRole() != null ? user.getRole().name() : "");
        }

        responseObserver.onNext(response.build());
        responseObserver.onCompleted();
    }
}
