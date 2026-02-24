package dev.chat.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.chat.config.GroqConfig;
import dev.chat.dto.*;
import dev.chat.exception.AiApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Comprehensive Groq API service with multiple AI modes.
 */
@Service
public class GroqService {

    private static final Logger log = LoggerFactory.getLogger(GroqService.class);
    private static final int MAX_RETRIES = 2;
    private static final Duration RETRY_DELAY = Duration.ofSeconds(1);

    private final WebClient webClient;
    private final GroqConfig config;
    private final ObjectMapper objectMapper;
    private final ConversationMemoryService memoryService;
    
    private final String modelFast;
    private final String modelVersatile;
    private final String modelMixtral;

    public GroqService(WebClient groqWebClient, GroqConfig config, ObjectMapper objectMapper,
                       ConversationMemoryService memoryService) {
        this.webClient = groqWebClient;
        this.config = config;
        this.objectMapper = objectMapper;
        this.memoryService = memoryService;
        
        this.modelFast = config.models().fast();
        this.modelVersatile = config.models().versatile();
        this.modelMixtral = config.models().mixtral();
        
        log.info("GroqService initialized with models: fast={}, versatile={}, mixtral={}", 
            modelFast, modelVersatile, modelMixtral);
    }

    // ==================== CHAT COMPLETION ====================
    
    /**
     * Multi-turn chat completion with conversation history.
     */
    public AiResponse chatCompletion(String prompt, String sessionId) {
        log.debug("Chat completion for session: {}", sessionId);
        
        if (sessionId == null || sessionId.isBlank()) {
            sessionId = memoryService.createSession();
        }
        
        memoryService.addUserMessage(sessionId, prompt);
        
        String systemPrompt = "You are a helpful, friendly AI assistant. Be concise but thorough.";
        List<ChatMessage> messages = memoryService.getHistoryWithSystemPrompt(sessionId, systemPrompt);
        
        GroqRequest request = GroqRequest.builder()
            .model(modelVersatile)
            .messages(messages)
            .temperature(0.7)
            .maxTokens(1024)
            .build();
        
        GroqResponse response = callApi(request);
        String content = response.getContent();
        
        memoryService.addAssistantMessage(sessionId, content);
        
        return AiResponse.success(content, modelVersatile, "chat", sessionId);
    }

    // ==================== STREAMING CHAT ====================
    
    /**
     * Streaming chat with Server-Sent Events.
     */
    public Flux<String> streamingChat(String prompt, String sessionId) {
        log.debug("Streaming chat for session: {}", sessionId);
        
        if (sessionId == null || sessionId.isBlank()) {
            sessionId = memoryService.createSession();
        }
        
        memoryService.addUserMessage(sessionId, prompt);
        
        String systemPrompt = "You are a helpful AI assistant. Be concise but thorough.";
        List<ChatMessage> messages = memoryService.getHistoryWithSystemPrompt(sessionId, systemPrompt);
        
        GroqRequest request = GroqRequest.builder()
            .model(modelFast)
            .messages(messages)
            .temperature(0.7)
            .maxTokens(1024)
            .stream(true)
            .build();
        
        final String finalSessionId = sessionId;
        StringBuilder fullResponse = new StringBuilder();
        
        return webClient.post()
            .uri("/chat/completions")
            .bodyValue(request)
            .accept(MediaType.TEXT_EVENT_STREAM)
            .retrieve()
            .onStatus(HttpStatusCode::isError, response ->
                response.bodyToMono(String.class)
                    .flatMap(body -> Mono.error(new AiApiException("Stream error: " + body))))
            .bodyToFlux(String.class)
            .filter(line -> line.startsWith("data: ") && !line.equals("data: [DONE]"))
            .map(line -> line.substring(6))
            .filter(json -> !json.isBlank())
            .mapNotNull(json -> {
                try {
                    JsonNode node = objectMapper.readTree(json);
                    JsonNode delta = node.path("choices").path(0).path("delta").path("content");
                    if (!delta.isMissingNode() && !delta.isNull()) {
                        String content = delta.asText();
                        fullResponse.append(content);
                        return content;
                    }
                } catch (Exception e) {
                    log.trace("Error parsing stream chunk: {}", e.getMessage());
                }
                return null;
            })
            .doOnComplete(() -> {
                if (!fullResponse.isEmpty()) {
                    memoryService.addAssistantMessage(finalSessionId, fullResponse.toString());
                }
            })
            .doOnError(e -> log.error("Streaming error", e));
    }

    // ==================== SUMMARIZATION ====================
    
    /**
     * Summarize text concisely.
     */
    public AiResponse summarize(String text) {
        log.debug("Summarizing text of length: {}", text.length());
        
        List<ChatMessage> messages = List.of(
            ChatMessage.system("You are an expert summarizer. Summarize the following text clearly and concisely. " +
                "Extract key points and present them in a well-organized manner. " +
                "Keep the summary under 200 words unless the text is very long."),
            ChatMessage.user(text)
        );
        
        GroqRequest request = GroqRequest.builder()
            .model(modelMixtral)
            .messages(messages)
            .temperature(0.3)
            .maxTokens(512)
            .build();
        
        GroqResponse response = callApi(request);
        return AiResponse.success(response.getContent(), modelMixtral, "summarize", null);
    }

    // ==================== CODE ASSISTANT ====================
    
    /**
     * Code assistant for programming help.
     */
    public CodeResponse codeAssistant(String prompt) {
        log.debug("Code assistant request");
        
        List<ChatMessage> messages = List.of(
            ChatMessage.system("""
                You are an expert senior software engineer with deep knowledge of multiple programming languages,
                design patterns, and best practices. When providing code:
                1. Write clean, production-ready code
                2. Include helpful comments for complex logic
                3. Follow language-specific conventions
                4. Suggest improvements when relevant
                5. Wrap code blocks with ```language syntax
                6. Explain your approach briefly before the code
                """),
            ChatMessage.user(prompt)
        );
        
        GroqRequest request = GroqRequest.builder()
            .model(modelVersatile)
            .messages(messages)
            .temperature(0.2)
            .maxTokens(2048)
            .build();
        
        GroqResponse response = callApi(request);
        String content = response.getContent();
        
        // Parse code blocks from response
        List<CodeResponse.CodeBlock> codeBlocks = extractCodeBlocks(content);
        String explanation = extractExplanation(content);
        
        return CodeResponse.success(explanation, codeBlocks);
    }

    // ==================== IMAGE PROMPT ENHANCER ====================
    
    /**
     * Enhance prompts for image generation (Stable Diffusion, DALL-E, etc.)
     */
    public AiResponse enhanceImagePrompt(String prompt) {
        log.debug("Enhancing image prompt");
        
        List<ChatMessage> messages = List.of(
            ChatMessage.system("""
                You are an expert at crafting prompts for AI image generation models like Stable Diffusion, DALL-E, and Midjourney.
                
                When given a basic prompt, enhance it by adding:
                - Artistic style (e.g., digital art, oil painting, photography, cinematic)
                - Lighting details (e.g., golden hour, dramatic lighting, soft ambient light)
                - Camera/perspective (e.g., wide angle, close-up, bird's eye view)
                - Quality modifiers (e.g., highly detailed, 8k, photorealistic, masterpiece)
                - Mood/atmosphere (e.g., ethereal, moody, vibrant, serene)
                - Color palette suggestions
                
                Return ONLY the enhanced prompt, nothing else. Make it detailed but under 200 words.
                """),
            ChatMessage.user("Enhance this prompt for image generation: " + prompt)
        );
        
        GroqRequest request = GroqRequest.builder()
            .model(modelFast)
            .messages(messages)
            .temperature(0.8)
            .maxTokens(300)
            .build();
        
        GroqResponse response = callApi(request);
        return AiResponse.success(response.getContent(), modelFast, "enhance-image", null);
    }

    // ==================== STRUCTURED JSON OUTPUT ====================
    
    /**
     * Generate structured JSON output based on a schema.
     */
    public AiResponse structuredOutput(String prompt, String jsonSchema) {
        log.debug("Generating structured JSON output");
        
        String schemaInstruction = jsonSchema != null && !jsonSchema.isBlank()
            ? "Follow this JSON schema strictly:\n" + jsonSchema
            : "Generate a well-structured JSON object appropriate for the request.";
        
        List<ChatMessage> messages = List.of(
            ChatMessage.system("""
                You are a JSON generator. You MUST respond with valid JSON only.
                Do not include any text before or after the JSON.
                Do not wrap the JSON in markdown code blocks.
                """ + schemaInstruction),
            ChatMessage.user(prompt)
        );
        
        GroqRequest request = GroqRequest.builder()
            .model(modelVersatile)
            .messages(messages)
            .temperature(0.1)
            .maxTokens(1024)
            .jsonMode()
            .build();
        
        GroqResponse response = callApi(request);
        String content = response.getContent();
        
        // Validate JSON
        try {
            JsonNode jsonNode = objectMapper.readTree(content);
            Object parsedJson = objectMapper.treeToValue(jsonNode, Object.class);
            return AiResponse.successWithData(content, modelVersatile, parsedJson);
        } catch (JsonProcessingException e) {
            log.error("Invalid JSON response: {}", content);
            return AiResponse.error("Generated response was not valid JSON: " + e.getMessage());
        }
    }

    // ==================== FUNCTION CALLING ====================
    
    /**
     * Demonstrate function/tool calling capability.
     */
    public AiResponse functionCallingExample(String prompt) {
        log.debug("Function calling example");
        
        // Define available tools
        var weatherTool = new GroqRequest.Tool(
            "function",
            new GroqRequest.Tool.Function(
                "get_weather",
                "Get the current weather for a location",
                Map.of(
                    "type", "object",
                    "properties", Map.of(
                        "location", Map.of("type", "string", "description", "City name"),
                        "unit", Map.of("type", "string", "enum", List.of("celsius", "fahrenheit"))
                    ),
                    "required", List.of("location")
                )
            )
        );
        
        var calculatorTool = new GroqRequest.Tool(
            "function",
            new GroqRequest.Tool.Function(
                "calculate",
                "Perform mathematical calculations",
                Map.of(
                    "type", "object",
                    "properties", Map.of(
                        "expression", Map.of("type", "string", "description", "Math expression to evaluate")
                    ),
                    "required", List.of("expression")
                )
            )
        );
        
        List<ChatMessage> messages = List.of(
            ChatMessage.system("You are a helpful assistant with access to tools. Use them when appropriate."),
            ChatMessage.user(prompt)
        );
        
        GroqRequest request = GroqRequest.builder()
            .model(modelVersatile)
            .messages(messages)
            .tools(List.of(weatherTool, calculatorTool))
            .toolChoice("auto")
            .temperature(0.1)
            .maxTokens(512)
            .build();
        
        GroqResponse response = callApi(request);
        
        // Check for tool calls
        if (response.choices() != null && !response.choices().isEmpty()) {
            var choice = response.choices().get(0);
            if (choice.message() != null && choice.message().toolCalls() != null) {
                var toolCalls = choice.message().toolCalls();
                return AiResponse.successWithData(
                    "Model requested function calls",
                    modelVersatile,
                    toolCalls
                );
            }
        }
        
        return AiResponse.success(response.getContent(), modelVersatile, "function-call", null);
    }

    // ==================== AVAILABLE MODELS ====================
    
    public List<ModelInfo> getAvailableModels() {
        return List.of(
            new ModelInfo(modelVersatile, "General Chat (Llama 3.3 70B)", "chat"),
            new ModelInfo(modelFast, "Fast Chat (Llama 3.1 8B)", "chat"),
            new ModelInfo(modelMixtral, "Summarization (Mixtral 8x7B)", "summarize")
        );
    }

    // ==================== HELPER METHODS ====================
    
    private GroqResponse callApi(GroqRequest request) {
        log.debug("Calling Groq API with model: {}", request.model());
        
        try {
            String responseBody = webClient.post()
                .uri("/chat/completions")
                .bodyValue(request)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, response ->
                    response.bodyToMono(String.class)
                        .flatMap(body -> {
                            log.error("Client error from Groq API: {} - {}", response.statusCode(), body);
                            return Mono.error(mapClientError(response.statusCode().value(), body));
                        }))
                .onStatus(HttpStatusCode::is5xxServerError, response ->
                    response.bodyToMono(String.class)
                        .flatMap(body -> {
                            log.error("Server error from Groq API: {} - {}", response.statusCode(), body);
                            return Mono.error(mapServerError(response.statusCode().value(), body));
                        }))
                .bodyToMono(String.class)
                .retryWhen(Retry.backoff(MAX_RETRIES, RETRY_DELAY)
                    .filter(this::isRetryable)
                    .doBeforeRetry(signal -> log.info("Retrying Groq API call, attempt {}", signal.totalRetries() + 1)))
                .timeout(Duration.ofMillis(config.timeout().read()))
                .block();
            
            return objectMapper.readValue(responseBody, GroqResponse.class);
            
        } catch (AiApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error calling Groq API", e);
            if (e.getMessage() != null && e.getMessage().contains("timeout")) {
                throw AiApiException.timeout();
            }
            throw new AiApiException("Failed to get response from AI model: " + e.getMessage(), e);
        }
    }

    private boolean isRetryable(Throwable throwable) {
        if (throwable instanceof AiApiException aae) {
            return aae.getStatusCode() == 429 || aae.getStatusCode() == 503;
        }
        return false;
    }

    private AiApiException mapClientError(int statusCode, String body) {
        return switch (statusCode) {
            case 401 -> AiApiException.unauthorized();
            case 429 -> AiApiException.rateLimitExceeded();
            default -> new AiApiException("API request failed: " + body, statusCode, "CLIENT_ERROR");
        };
    }

    private AiApiException mapServerError(int statusCode, String body) {
        return switch (statusCode) {
            case 503 -> AiApiException.serviceUnavailable();
            case 504 -> AiApiException.timeout();
            default -> new AiApiException("Server error: " + body, statusCode, "SERVER_ERROR");
        };
    }

    private List<CodeResponse.CodeBlock> extractCodeBlocks(String content) {
        List<CodeResponse.CodeBlock> blocks = new ArrayList<>();
        Pattern pattern = Pattern.compile("```(\\w*)\\n([\\s\\S]*?)```");
        Matcher matcher = pattern.matcher(content);
        
        while (matcher.find()) {
            String language = matcher.group(1).isEmpty() ? "plaintext" : matcher.group(1);
            String code = matcher.group(2).trim();
            blocks.add(new CodeResponse.CodeBlock(language, code, null));
        }
        
        return blocks;
    }

    private String extractExplanation(String content) {
        // Remove code blocks and return the explanation text
        String explanation = content.replaceAll("```\\w*\\n[\\s\\S]*?```", "").trim();
        // Clean up multiple newlines
        explanation = explanation.replaceAll("\\n{3,}", "\n\n");
        return explanation;
    }
}
