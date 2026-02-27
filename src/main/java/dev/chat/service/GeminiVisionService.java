package dev.chat.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.chat.config.GoogleAiConfig;
import dev.chat.dto.VisionResponse;
import dev.chat.exception.AiApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Base64;

/**
 * Google Gemini service for Vision and Document analysis only.
 * Uses Gemini 1.5 Flash for multimodal capabilities.
 */
@Service
public class GeminiVisionService {

    private static final Logger log = LoggerFactory.getLogger(GeminiVisionService.class);
    
    private final WebClient webClient;
    private final GoogleAiConfig config;
    private final ObjectMapper objectMapper;

    public GeminiVisionService(WebClient googleAiWebClient, GoogleAiConfig config, ObjectMapper objectMapper) {
        this.webClient = googleAiWebClient;
        this.config = config;
        this.objectMapper = objectMapper;
        
        String keyStatus = (config.key() != null && !config.key().isBlank() && !config.key().equals("your-google-ai-key"))
            ? "configured (ends with ..." + config.key().substring(Math.max(0, config.key().length() - 4)) + ")"
            : "NOT CONFIGURED - Set GEMINI_API_KEY env variable!";
        log.info("GeminiVisionService initialized - model: {}, API key: {}", config.model(), keyStatus);
    }

    /**
     * Analyze an image using Gemini Vision.
     * 
     * @param base64Image Base64 encoded image data
     * @param prompt User prompt for analysis
     * @return VisionResponse with extracted content
     */
    public VisionResponse analyzeImage(String base64Image, String prompt) {
        log.debug("Analyzing image with prompt: {}", truncate(prompt));
        
        validateBase64Data(base64Image, "image");
        String mimeType = detectImageMimeType(base64Image);
        
        ObjectNode requestBody = buildMultimodalRequest(prompt, base64Image, mimeType);
        
        String response = callGeminiApi(requestBody);
        String content = extractTextFromResponse(response);
        
        return VisionResponse.success(content, config.model(), "vision");
    }

    /**
     * Analyze a PDF document using Gemini.
     * 
     * @param base64Pdf Base64 encoded PDF data
     * @return VisionResponse with document summary and insights
     */
    public VisionResponse analyzeDocument(String base64Pdf) {
        return analyzeDocument(base64Pdf, "Summarize this document and extract key insights.");
    }

    /**
     * Analyze a PDF document using Gemini with custom prompt.
     * 
     * @param base64Pdf Base64 encoded PDF data
     * @param prompt Custom analysis prompt
     * @return VisionResponse with document analysis
     */
    public VisionResponse analyzeDocument(String base64Pdf, String prompt) {
        log.debug("Analyzing document with prompt: {}", truncate(prompt));
        
        validateBase64Data(base64Pdf, "document");
        
        ObjectNode requestBody = buildMultimodalRequest(prompt, base64Pdf, "application/pdf");
        
        String response = callGeminiApi(requestBody);
        String content = extractTextFromResponse(response);
        
        return VisionResponse.success(content, config.model(), "document");
    }

    /**
     * Build the multimodal request body for Gemini API.
     */
    private ObjectNode buildMultimodalRequest(String prompt, String base64Data, String mimeType) {
        ObjectNode root = objectMapper.createObjectNode();
        ArrayNode contents = root.putArray("contents");
        ObjectNode content = contents.addObject();
        ArrayNode parts = content.putArray("parts");
        
        // Add inline data (image or PDF)
        ObjectNode inlineDataPart = parts.addObject();
        ObjectNode inlineData = inlineDataPart.putObject("inline_data");
        inlineData.put("mime_type", mimeType);
        inlineData.put("data", base64Data);
        
        // Add text prompt
        ObjectNode textPart = parts.addObject();
        textPart.put("text", prompt);
        
        return root;
    }

    /**
     * Call the Gemini API with the given request body.
     */
    private String callGeminiApi(ObjectNode requestBody) {
        String apiKey = config.key();
        if (apiKey == null || apiKey.isBlank() || apiKey.equals("your-google-ai-key")) {
            log.error("GEMINI_API_KEY is not configured! Please set the environment variable.");
            throw new AiApiException("Gemini API key not configured. Set GEMINI_API_KEY environment variable.", 401, "UNAUTHORIZED");
        }
        
        String endpoint = "/models/" + config.model() + ":generateContent?key=" + apiKey;
        log.debug("Calling Gemini API: {} (key length: {})", "/models/" + config.model() + ":generateContent", apiKey.length());
        
        try {
            String jsonBody = objectMapper.writeValueAsString(requestBody);
            log.debug("Request body: {}", jsonBody.substring(0, Math.min(200, jsonBody.length())) + "...");
            
            return webClient.post()
                .uri(endpoint)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(jsonBody)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, response ->
                    response.bodyToMono(String.class)
                        .flatMap(body -> {
                            log.error("Client error from Gemini API: {} - {}", response.statusCode(), body);
                            return Mono.error(mapClientError(response.statusCode().value(), body));
                        }))
                .onStatus(HttpStatusCode::is5xxServerError, response ->
                    response.bodyToMono(String.class)
                        .flatMap(body -> {
                            log.error("Server error from Gemini API: {} - {}", response.statusCode(), body);
                            return Mono.error(new AiApiException("Gemini server error: " + body, 
                                response.statusCode().value(), "SERVER_ERROR"));
                        }))
                .bodyToMono(String.class)
                .timeout(Duration.ofMillis(config.timeout()))
                .block();
                
        } catch (AiApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error calling Gemini API", e);
            if (e.getMessage() != null && e.getMessage().contains("timeout")) {
                throw AiApiException.timeout();
            }
            throw new AiApiException("Failed to analyze content: " + e.getMessage(), e);
        }
    }

    /**
     * Extract text content from Gemini API response.
     */
    private String extractTextFromResponse(String responseJson) {
        try {
            JsonNode root = objectMapper.readTree(responseJson);
            JsonNode candidates = root.path("candidates");
            
            if (candidates.isEmpty()) {
                JsonNode error = root.path("error");
                if (!error.isMissingNode()) {
                    String message = error.path("message").asText("Unknown error");
                    throw new AiApiException("Gemini API error: " + message, 400, "API_ERROR");
                }
                throw new AiApiException("No content generated", 400, "EMPTY_RESPONSE");
            }
            
            StringBuilder content = new StringBuilder();
            JsonNode parts = candidates.get(0).path("content").path("parts");
            
            for (JsonNode part : parts) {
                String text = part.path("text").asText(null);
                if (text != null) {
                    content.append(text);
                }
            }
            
            if (content.isEmpty()) {
                throw new AiApiException("No text content in response", 400, "EMPTY_CONTENT");
            }
            
            return content.toString();
            
        } catch (AiApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error parsing Gemini response", e);
            throw new AiApiException("Failed to parse response: " + e.getMessage(), e);
        }
    }

    /**
     * Map HTTP status codes to appropriate exceptions.
     */
    private AiApiException mapClientError(int statusCode, String body) {
        return switch (statusCode) {
            case 400 -> new AiApiException("Invalid request: " + extractErrorMessage(body), 400, "BAD_REQUEST");
            case 401, 403 -> AiApiException.unauthorized();
            case 404 -> new AiApiException("Model not found. Please check the model name.", 404, "MODEL_NOT_FOUND");
            case 429 -> AiApiException.rateLimitExceeded();
            default -> new AiApiException("API request failed: " + body, statusCode, "CLIENT_ERROR");
        };
    }

    /**
     * Extract error message from error response body.
     */
    private String extractErrorMessage(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            return root.path("error").path("message").asText(body);
        } catch (Exception e) {
            return body;
        }
    }

    /**
     * Validate base64 encoded data.
     */
    private void validateBase64Data(String base64Data, String type) {
        if (base64Data == null || base64Data.isBlank()) {
            throw new AiApiException(type + " data is required", 400, "MISSING_DATA");
        }
        
        // Remove data URL prefix if present
        String cleanBase64 = base64Data;
        if (base64Data.contains(",")) {
            cleanBase64 = base64Data.substring(base64Data.indexOf(",") + 1);
        }
        
        try {
            byte[] decoded = Base64.getDecoder().decode(cleanBase64);
            if (decoded.length > config.getMaxFileSize()) {
                throw new AiApiException(
                    String.format("%s size exceeds maximum allowed (%d MB)", 
                        type, config.getMaxFileSize() / (1024 * 1024)),
                    400, "FILE_TOO_LARGE"
                );
            }
        } catch (IllegalArgumentException e) {
            throw new AiApiException("Invalid base64 encoding for " + type, 400, "INVALID_BASE64");
        }
    }

    /**
     * Detect image MIME type from base64 data.
     */
    private String detectImageMimeType(String base64Data) {
        if (base64Data.startsWith("data:")) {
            int endIndex = base64Data.indexOf(";");
            if (endIndex > 5) {
                return base64Data.substring(5, endIndex);
            }
        }
        
        // Try to detect from magic bytes
        String cleanBase64 = base64Data.contains(",") 
            ? base64Data.substring(base64Data.indexOf(",") + 1) 
            : base64Data;
            
        try {
            byte[] bytes = Base64.getDecoder().decode(cleanBase64.substring(0, Math.min(cleanBase64.length(), 100)));
            
            if (bytes.length >= 3 && bytes[0] == (byte) 0xFF && bytes[1] == (byte) 0xD8 && bytes[2] == (byte) 0xFF) {
                return "image/jpeg";
            }
            if (bytes.length >= 8 && bytes[0] == (byte) 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4E && bytes[3] == 0x47) {
                return "image/png";
            }
            if (bytes.length >= 6 && bytes[0] == 0x47 && bytes[1] == 0x49 && bytes[2] == 0x46) {
                return "image/gif";
            }
            if (bytes.length >= 4 && bytes[0] == 0x52 && bytes[1] == 0x49 && bytes[2] == 0x46 && bytes[3] == 0x46) {
                return "image/webp";
            }
        } catch (Exception e) {
            log.debug("Could not detect MIME type from bytes", e);
        }
        
        // Default to JPEG
        return "image/jpeg";
    }

    private String truncate(String text) {
        if (text == null) return "null";
        return text.length() > 50 ? text.substring(0, 50) + "..." : text;
    }
}
