package com.collectormarket.backend.ebay;

import java.time.Duration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;

import io.netty.channel.ChannelOption;
import reactor.netty.http.client.HttpClient;

@Configuration
@EnableConfigurationProperties(EbayProperties.class)
public class EbayWebClientConfig {

    @Bean
    public WebClient ebayWebClient(WebClient.Builder builder, EbayProperties properties) {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) properties.connectTimeoutMs())
                .responseTimeout(Duration.ofMillis(properties.responseTimeoutMs()));

        return builder
                .baseUrl(properties.apiBaseUrl())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }
}
