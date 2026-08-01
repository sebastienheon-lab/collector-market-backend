package com.collectormarket.backend.configs;

import com.collectormarket.backend.properties.EbayProperties;

import java.time.Duration;

import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;

import io.netty.channel.ChannelOption;
import reactor.netty.http.client.HttpClient;

@Configuration
@EnableConfigurationProperties(EbayProperties.class)
public class EbayWebClientConfig {

    /**
     * This app mixes spring-boot-starter-webmvc and -webflux, and Spring Boot does not
     * auto-configure a WebClient.Builder bean in that combination (it defers to the servlet
     * stack). Declared explicitly so EbayOAuthTokenProvider's injection point resolves.
     * Prototype-scoped (matching Spring Boot's own convention for this bean) since the builder
     * is mutable - a shared singleton would leak baseUrl/connector state between consumers.
     */
    @Bean
    @ConditionalOnMissingBean
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder();
    }

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
