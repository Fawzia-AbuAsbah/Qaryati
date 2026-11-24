package com.qaryati.integration.config;

import com.qaryati.integration.client.GeoapifyHttpTransport;
import com.qaryati.integration.client.JdkGeoapifyHttpTransport;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;

@Configuration
@EnableConfigurationProperties(GeoapifyProperties.class)
public class GeoapifyConfiguration {

    @Bean
    GeoapifyHttpTransport geoapifyHttpTransport(GeoapifyProperties properties) {
        return new JdkGeoapifyHttpTransport(HttpClient.newBuilder()
                .connectTimeout(properties.getConnectTimeout())
                .build());
    }
}
