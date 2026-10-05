package com.fazbear.orden.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Provee el bean WebClient.Builder para que OrdenService pueda
 * hacer llamadas HTTP reactivas a ms-pedidos y ms-carrito.
 */
@Configuration
public class WebClientConfig {

    @Bean
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder();
    }
}
