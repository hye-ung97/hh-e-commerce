package org.hhplus.hhecommerce;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;

@EnableRetry
@SpringBootApplication
public class HhECommerceApplication {

    public static void main(String[] args) {
        SpringApplication.run(HhECommerceApplication.class, args);
    }

}
