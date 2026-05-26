package com.orderprocessing.order.service.infrastructure.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI orderServiceOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Order Service API")
                        .description("Serviço responsável por criação e cancelamento de pedidos. " +
                                "Publica eventos no RabbitMQ (order.created, order.cancelled) " +
                                "consumidos pelo inventory-service e notification-service.")
                        .version("1.0.0")
                        .contact(new Contact().name("Order Processing System")))
                .servers(List.of(new Server().url("http://localhost:8085").description("Local")));
    }
}
