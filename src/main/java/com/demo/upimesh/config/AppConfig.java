package com.demo.upimesh.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling // powers IdempotencyService's scheduled cache eviction
public class AppConfig {

    @Bean
    public OpenAPI upiMeshOpenApi() {
        return new OpenAPI().info(new Info()
            .title("UPI Offline Mesh API")
            .description("Demo backend for offline UPI payments routed through a simulated Bluetooth mesh")
            .version("1.0.0"));
    }
}
