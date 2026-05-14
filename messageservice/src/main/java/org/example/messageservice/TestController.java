package org.example.messageservice;

import org.example.grpc.GreetingServiceGrpc;
import org.example.grpc.HelloRequest;
import org.example.grpc.HelloResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TestController {

    final GreetingServiceGrpc.GreetingServiceBlockingStub stub;

    public TestController(GreetingServiceGrpc.GreetingServiceBlockingStub stub) {
        this.stub = stub;
    }

    @GetMapping("/api/test")
    public String test(@RequestParam(defaultValue = "Guest") String name) {
        HelloRequest request = HelloRequest.newBuilder()
                .setName(name)
                .build();
        HelloResponse response = stub.sayHello(request);

        return "Service 1 received gRPC response: " + response.getMessage();
    }
}