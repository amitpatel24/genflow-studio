package dev.chat.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for Google AI (Gemini) Vision & Document analysis.
 */
@ConfigurationProperties(prefix = "google.ai")
public record GoogleAiConfig(
    String key,
    String baseUrl,
    String model,
    int timeout,
    long maxFileSize
) {
    public static final long DEFAULT_MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB
    
    public long getMaxFileSize() {
        return maxFileSize > 0 ? maxFileSize : DEFAULT_MAX_FILE_SIZE;
    }
}
