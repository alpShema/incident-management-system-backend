package com.amalitech.hilfe;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class HilfeApplication {

    public static void main(String[] args) {
        SpringApplication.run(HilfeApplication.class, args);
    }

}
