package com.novel2script.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Novel2Script — AI-driven novel-to-script conversion system.
 * <p>
 * Entry point for the Spring Boot application.
 * Component scanning covers all modules:
 * common, domain, infrastructure, application, and api.
 */
@SpringBootApplication(scanBasePackages = "com.novel2script")
public class Novel2ScriptApplication {

    public static void main(String[] args) {
        SpringApplication.run(Novel2ScriptApplication.class, args);
    }
}
