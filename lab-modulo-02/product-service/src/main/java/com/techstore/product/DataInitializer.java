package com.techstore.product;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;

@Configuration
public class DataInitializer {

    @Bean
    CommandLineRunner init(ProductRepository repository) {
        return args -> {
            if (repository.count() == 0) {
                repository.save(new Product("Laptop Pro", new BigDecimal("3499.90"), 10));
                repository.save(new Product("Monitor 27", new BigDecimal("1299.90"), 15));
                repository.save(new Product("Mechanical Keyboard", new BigDecimal("349.90"), 30));
            }
        };
    }
}
