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
 * Configuration for WebClients used to communicate with AI APIs.
 * - Groq: Text chat (fast inference)
 * - Google AI: Vision and Document analysis (multimodal)
 */
@Configuration
@EnableConfigurationProperties({GroqConfig.class, GoogleAiConfig.class})
public class WebClientConfig {

    private final GroqConfig groqConfig;
    private final GoogleAiConfig googleAiConfig;

    public WebClientConfig(GroqConfig groqConfig, GoogleAiConfig googleAiConfig) {
        this.groqConfig = groqConfig;
        this.googleAiConfig = googleAiConfig;
    }

    @Bean
    public WebClient groqWebClient() {
        HttpClient httpClient = HttpClient.create()
            .responseTimeout(Duration.ofMillis(groqConfig.timeout().read()));

        return WebClient.builder()
            .baseUrl(groqConfig.api().baseUrl())
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + groqConfig.api().key())
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .build();
    }

    @Bean
    public WebClient googleAiWebClient() {
        HttpClient httpClient = HttpClient.create()
            .responseTimeout(Duration.ofMillis(googleAiConfig.timeout()));

        return WebClient.builder()
            .baseUrl(googleAiConfig.baseUrl())
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(16 * 1024 * 1024)) // 16MB for large files
            .build();
    }

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}
