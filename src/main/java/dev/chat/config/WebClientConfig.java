package dev.chat.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

/**
 * Configuration for WebClient used to communicate with Hugging Face API.
 */
@Configuration
@EnableConfigurationProperties(HuggingFaceConfig.class)
public class WebClientConfig {

    private final HuggingFaceConfig config;

    public WebClientConfig(HuggingFaceConfig config) {
        this.config = config;
    }

    @Bean
    public WebClient huggingFaceWebClient() {
        HttpClient httpClient = HttpClient.create()
            .responseTimeout(Duration.ofMillis(config.timeout().read()));

        return WebClient.builder()
            .baseUrl(config.api().baseUrl())
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + config.api().key())
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .build();
    }

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}
