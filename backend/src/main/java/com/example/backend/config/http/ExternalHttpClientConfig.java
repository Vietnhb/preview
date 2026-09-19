package com.example.backend.config.http;

import java.net.http.HttpClient;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.example.backend.config.properties.VnpayProperties;

@Configuration
public class ExternalHttpClientConfig {
    @Bean
    HttpClient vnpayHttpClient(VnpayProperties properties) {
        return HttpClient.newBuilder().connectTimeout(properties.connectTimeout()).build();
    }
}
