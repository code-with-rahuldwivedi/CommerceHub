package com.shopsphere.userservice.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {

    /** All "now" calls go through this clock (UTC) so time-based logic is testable. */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
