package com.example.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.List;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Automatically routes messages to specific factory topics
        config.enableSimpleBroker("/topic");
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // The secure endpoint React will connect to
        registry.addEndpoint("/ws-enviroguard")

                .setAllowedOrigins(String.valueOf(List.of(
                "http://localhost:5173",
                "https://enviro-guard-guq3okqq7-ayesha-mujeeb.vercel.app/" // Add your Vercel domain here
        )))
                .withSockJS();
    }
}