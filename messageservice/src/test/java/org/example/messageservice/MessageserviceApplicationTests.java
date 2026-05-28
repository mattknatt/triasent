package org.example.messageservice;

import org.junit.jupiter.api.Test;

class MessageserviceApplicationTests extends AbstractIntegrationTest {

    @Test
    void contextLoads() {
        // The base class wires Postgres + RabbitMQ via Testcontainers, so a successful
        // context load also proves the connection details are flowing through correctly.
    }
}
