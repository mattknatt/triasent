package org.example.userservice;

import io.grpc.stub.StreamObserver;
import org.example.grpc.GreetingServiceGrpc;
import org.example.grpc.HelloRequest;
import org.example.grpc.HelloResponse;
import org.springframework.grpc.server.service.GrpcService;

@GrpcService
public class GrpcServerService extends GreetingServiceGrpc.GreetingServiceImplBase {

    @Override
    public void sayHello(HelloRequest request, StreamObserver<HelloResponse> responseObserver) {
        String name = request.getName();
        String message = "Hello " + name + " from Service 3 via gRPC!";

        HelloResponse response = HelloResponse.newBuilder()
                .setMessage(message)
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
}