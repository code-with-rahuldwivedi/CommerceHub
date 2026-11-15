package com.shopsphere.orderservice.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.shopsphere.orderservice.service.DefaultPricingPolicy;
import com.shopsphere.orderservice.service.PricingPolicy;

@Configuration
public class PricingConfig {

    // Replaced automatically by any other PricingPolicy bean. 
    @Bean
    @ConditionalOnMissingBean(PricingPolicy.class)
    public PricingPolicy defaultPricingPolicy() {
        return new DefaultPricingPolicy();
    }
}
