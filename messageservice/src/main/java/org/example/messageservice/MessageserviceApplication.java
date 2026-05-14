package org.example.messageservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.grpc.client.ImportGrpcClients;

@SpringBootApplication
@ImportGrpcClients(basePackages="org.example.grpc")
public class MessageserviceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MessageserviceApplication.class, args);
    }

}
