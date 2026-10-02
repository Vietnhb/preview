package com.example.backend.config;

import java.net.http.HttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ExternalHttpClientConfig {
    @Bean
    HttpClient vnpayHttpClient(VnpayProperties properties) {
        return HttpClient.newBuilder().connectTimeout(properties.connectTimeout()).build();
    }
}
