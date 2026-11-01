package com.anantghadi.pricing;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class DynamicPricingEngineApplication {

    public static void main(String[] args) {
        SpringApplication.run(DynamicPricingEngineApplication.class, args);
    }
}
