package dev.chat.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for Hugging Face API integration.
 */
@ConfigurationProperties(prefix = "huggingface")
public record HuggingFaceConfig(
    Api api,
    Models models,
    Timeout timeout
) {
    public record Api(String key, String baseUrl) {}
    
    public record Models(Model chat, Model summarization) {}
    
    public record Model(String id, String name) {}
    
    public record Timeout(int connect, int read) {}
    
    /**
     * Builds the full URL for a specific model.
     */
    public String getModelUrl(String modelId) {
        return api.baseUrl() + "/" + modelId;
    }
}
