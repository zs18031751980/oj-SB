package com.xauat.oj.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.xauat.oj")
@EntityScan("com.xauat.oj.core")
@EnableJpaRepositories("com.xauat.oj.core")
@EnableScheduling
public class OjApiApplication {
    public static void main(String[] args) {
        SpringApplication.run(OjApiApplication.class, args);
    }
}
