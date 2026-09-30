package com.example.backend.config;

import com.example.backend.security.AccountCapabilityInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class AccountCapabilityConfig implements WebMvcConfigurer {
    private final AccountCapabilityInterceptor interceptor;
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor).addPathPatterns("/api/reviewer/**", "/api/evaluations/**", "/api/schemas/**", "/api/simulations/*/approve", "/api/simulations/*/reject");
    }
}
