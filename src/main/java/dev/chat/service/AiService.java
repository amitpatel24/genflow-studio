package dev.chat.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.chat.config.HuggingFaceConfig;
import dev.chat.dto.ChatResponse;
import dev.chat.dto.ModelInfo;
import dev.chat.exception.HuggingFaceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Service layer for interacting with Hugging Face Inference API.
 * Uses the OpenAI-compatible chat completions endpoint.
 */
@Service
public class AiService {

    private static final Logger log = LoggerFactory.getLogger(AiService.class);
    
    private final WebClient webClient;
    private final HuggingFaceConfig config;
    private final ObjectMapper objectMapper;

    public AiService(WebClient huggingFaceWebClient, HuggingFaceConfig config, ObjectMapper objectMapper) {
        this.webClient = huggingFaceWebClient;
        this.config = config;
        this.objectMapper = objectMapper;
    }

    /**
     * Sends a chat message to the specified model and returns the AI response.
     */
    public ChatResponse chat(String message, String modelId) {
        log.debug("Processing chat request with model: {}", modelId);
        
        validateInput(message, modelId);
        
        Map<String, Object> request = createChatRequest(message, modelId);
        String response = callChatCompletionsApi(request);
        
        return ChatResponse.success(response, modelId);
    }

    /**
     * Returns list of available models for the frontend dropdown.
     */
    public List<ModelInfo> getAvailableModels() {
        return List.of(
            ModelInfo.chat(config.models().chat().id(), config.models().chat().name()),
            ModelInfo.summarization(config.models().summarization().id(), config.models().summarization().name())
        );
    }

    /**
     * Checks if the given model ID is for summarization.
     */
    public boolean isSummarizationModel(String modelId) {
        return modelId.equals(config.models().summarization().id());
    }

    private void validateInput(String message, String modelId) {
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("Message cannot be empty");
        }
        if (modelId == null || modelId.isBlank()) {
            throw new IllegalArgumentException("Model ID cannot be empty");
        }
    }

    /**
     * Creates an OpenAI-compatible chat completion request.
     */
    private Map<String, Object> createChatRequest(String message, String modelId) {
        String systemPrompt = isSummarizationModel(modelId)
            ? "You are a helpful assistant that summarizes text concisely."
            : "You are a helpful AI assistant.";

        return Map.of(
            "model", modelId + ":fastest",
            "messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", message)
            ),
            "max_tokens", 512,
            "temperature", 0.7
        );
    }

    /**
     * Calls the OpenAI-compatible chat completions endpoint.
     */
    private String callChatCompletionsApi(Map<String, Object> request) {
        log.debug("Calling HF chat completions API with model: {}", request.get("model"));
        
        try {
            String responseBody = webClient.post()
                .uri("/chat/completions")
                .bodyValue(request)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, response -> 
                    response.bodyToMono(String.class)
                        .flatMap(body -> {
                            log.error("Client error from HF API: {} - {}", response.statusCode(), body);
                            return Mono.error(mapClientError(response.statusCode().value(), body));
                        }))
                .onStatus(HttpStatusCode::is5xxServerError, response ->
                    response.bodyToMono(String.class)
                        .flatMap(body -> {
                            log.error("Server error from HF API: {} - {}", response.statusCode(), body);
                            return Mono.error(mapServerError(response.statusCode().value(), body));
                        }))
                .bodyToMono(String.class)
                .timeout(Duration.ofMillis(config.timeout().read()))
                .block();

            return parseChatResponse(responseBody);
            
        } catch (HuggingFaceException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error calling Hugging Face API", e);
            if (e.getMessage() != null && e.getMessage().contains("timeout")) {
                throw HuggingFaceException.timeout();
            }
            throw new HuggingFaceException("Failed to get response from AI model", e);
        }
    }

    /**
     * Parses the OpenAI-compatible chat completion response.
     */
    private String parseChatResponse(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            
            // OpenAI format: { "choices": [{ "message": { "content": "..." } }] }
            if (root.has("choices") && root.get("choices").isArray()) {
                JsonNode choices = root.get("choices");
                if (!choices.isEmpty()) {
                    JsonNode firstChoice = choices.get(0);
                    if (firstChoice.has("message") && firstChoice.get("message").has("content")) {
                        return firstChoice.get("message").get("content").asText().trim();
                    }
                }
            }
            
            // Check for error in response
            if (root.has("error")) {
                JsonNode error = root.get("error");
                String errorMessage = error.isObject() && error.has("message") 
                    ? error.get("message").asText()
                    : error.asText();
                log.error("HF API returned error: {}", errorMessage);
                if (errorMessage.contains("loading")) {
                    throw HuggingFaceException.modelLoading();
                }
                throw new HuggingFaceException("Model error: " + errorMessage, 500, "MODEL_ERROR");
            }
            
            log.warn("Unexpected response format: {}", responseBody);
            return "Unable to parse AI response.";
            
        } catch (HuggingFaceException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error parsing HF response: {}", responseBody, e);
            return "Error parsing AI response.";
        }
    }

    private HuggingFaceException mapClientError(int statusCode, String body) {
        return switch (statusCode) {
            case 401 -> HuggingFaceException.unauthorized();
            case 429 -> HuggingFaceException.rateLimitExceeded();
            default -> new HuggingFaceException("API request failed: " + body, statusCode, "CLIENT_ERROR");
        };
    }

    private HuggingFaceException mapServerError(int statusCode, String body) {
        if (body != null && body.contains("loading")) {
            return HuggingFaceException.modelLoading();
        }
        return switch (statusCode) {
            case 503 -> HuggingFaceException.modelLoading();
            case 504 -> HuggingFaceException.timeout();
            default -> new HuggingFaceException("Server error: " + body, statusCode, "SERVER_ERROR");
        };
    }
}
