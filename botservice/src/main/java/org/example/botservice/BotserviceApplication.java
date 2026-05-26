package org.example.botservice;

import org.example.botservice.config.LlmProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.resilience.annotation.EnableResilientMethods;

@SpringBootApplication
@EnableConfigurationProperties(LlmProperties.class)
@EnableResilientMethods
public class BotserviceApplication {

    public static void main(String[] args) {
        SpringApplication.run(BotserviceApplication.class, args);
    }

}
