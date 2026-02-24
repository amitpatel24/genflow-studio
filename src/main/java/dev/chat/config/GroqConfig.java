package dev.chat.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for Groq API integration.
 */
@ConfigurationProperties(prefix = "groq")
public record GroqConfig(
    Api api,
    Models models,
    Timeout timeout
) {
    public record Api(String key, String baseUrl) {}
    
    public record Models(String versatile, String fast, String mixtral) {}
    
    public record Timeout(int connect, int read) {}
}
