package org.example.messageservice;

import org.example.grpc.GetUserRequest;
import org.example.grpc.UserProfile;
import org.example.grpc.UserServiceGrpc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TestController {

    final UserServiceGrpc.UserServiceBlockingStub stub;

    public TestController(UserServiceGrpc.UserServiceBlockingStub stub) {
        this.stub = stub;
    }

    @GetMapping("/api/user-profile")
    public String getUserProfile(@RequestParam String userId) {
        GetUserRequest request = GetUserRequest.newBuilder()
                .setUserId(userId)
                .build();
        UserProfile profile = stub.getUserProfile(request);

        return "User: " + profile.getUsername() + " (role: " + profile.getRole() + ")";
    }
}
