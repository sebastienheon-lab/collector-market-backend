package com.collectormarket.backend.configs;

import com.collectormarket.backend.properties.RetentionProperties;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(RetentionProperties.class)
public class IngestionConfig {
}
