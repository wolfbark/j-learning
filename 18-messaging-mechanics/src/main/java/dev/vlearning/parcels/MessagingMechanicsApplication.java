package dev.vlearning.parcels;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class MessagingMechanicsApplication {

    public static void main(String[] args) {
        SpringApplication.run(MessagingMechanicsApplication.class, args);
    }
}
