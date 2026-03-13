package com.bank.authservice.service;

import com.bank.authservice.event.UserRegisteredEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class KafkaProducerService {

    private static final String TOPIC = "user.registered";
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public void publishUserRegistered(UserRegisteredEvent event) {
        try {
            String message = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(TOPIC, event.getEmail(), message);
            log.info("Event publié sur Kafka — topic: {}, email: {}", TOPIC, event.getEmail());
        } catch (Exception e) {
            log.error(" Erreur publication Kafka — email: {}", event.getEmail(), e);
        }
    }
}