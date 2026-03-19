package com.bank.authservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestTemplateConfig {

    /**
     * RestTemplate : client HTTP pour appeler l'API Admin Keycloak.
     * On fait des requêtes HTTP vers des services externes.
     *
     * Spring Boot 3+ ne fournit plus de RestTemplate automatiquement,
     * il faut le déclarer comme @Bean.
     */
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}