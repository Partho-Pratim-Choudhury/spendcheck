package com.parthopratim.spendcheck;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class SpendcheckApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpendcheckApplication.class, args);
    }
}
