package com.shopsmart;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Runnable app. Component scanning picks up the kit and the four slice modules (all under com.shopsmart). */
@SpringBootApplication
@EnableAsync
@EnableScheduling
public class ShopSmartApplication {
    public static void main(String[] args) {
        SpringApplication.run(ShopSmartApplication.class, args);
    }
}
