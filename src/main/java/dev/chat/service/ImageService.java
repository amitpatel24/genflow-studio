package dev.chat.service;

import dev.chat.exception.HuggingFaceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.Map;

/**
 * Service for generating images using Hugging Face Stable Diffusion API.
 */
@Service
public class ImageService {

    private static final Logger log = LoggerFactory.getLogger(ImageService.class);
    private static final String IMAGE_API_BASE_URL = "https://router.huggingface.co/hf-inference/models/";
    private static final int MAX_RETRIES = 2;
    private static final Duration RETRY_DELAY = Duration.ofSeconds(5);

    private final WebClient imageWebClient;
    private final String model;

    public ImageService(
            @Value("${huggingface.api.key}") String apiKey,
            @Value("${huggingface.image.model:stabilityai/stable-diffusion-2-1}") String model) {
        this.model = model;
        this.imageWebClient = WebClient.builder()
                .baseUrl(IMAGE_API_BASE_URL)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
        log.info("ImageService initialized with model: {}", model);
    }

    /**
     * Generates an image from a text prompt.
     *
     * @param prompt The text description of the image to generate
     * @return byte array containing the generated image (PNG format)
     */
    public byte[] generateImage(String prompt) {
        return generateImage(prompt, null, 512, 512);
    }

    /**
     * Generates an image from a text prompt with advanced options.
     *
     * @param prompt         The text description of the image to generate
     * @param negativePrompt Things to exclude from the image (optional)
     * @param width          Image width (default 512)
     * @param height         Image height (default 512)
     * @return byte array containing the generated image (PNG format)
     */
    public byte[] generateImage(String prompt, String negativePrompt, int width, int height) {
        log.info("Generating image with prompt: '{}', size: {}x{}", 
                truncateForLog(prompt), width, height);

        validateInput(prompt);

        Map<String, Object> requestBody = buildRequestBody(prompt, negativePrompt, width, height);

        try {
            byte[] imageBytes = imageWebClient.post()
                    .uri(model)
                    .bodyValue(requestBody)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, response ->
                            response.bodyToMono(String.class)
                                    .flatMap(body -> {
                                        log.error("Client error from Image API: {} - {}", 
                                                response.statusCode(), body);
                                        return Mono.error(mapClientError(response.statusCode().value(), body));
                                    }))
                    .onStatus(HttpStatusCode::is5xxServerError, response ->
                            response.bodyToMono(String.class)
                                    .flatMap(body -> {
                                        log.error("Server error from Image API: {} - {}", 
                                                response.statusCode(), body);
                                        return Mono.error(mapServerError(response.statusCode().value(), body));
                                    }))
                    .bodyToMono(byte[].class)
                    .retryWhen(Retry.backoff(MAX_RETRIES, RETRY_DELAY)
                            .filter(this::isRetryableError)
                            .doBeforeRetry(signal -> 
                                    log.info("Retrying image generation, attempt {}", 
                                            signal.totalRetries() + 1)))
                    .timeout(Duration.ofSeconds(120))
                    .block();

            if (imageBytes == null || imageBytes.length == 0) {
                throw new HuggingFaceException("Empty response from image API", 500, "EMPTY_RESPONSE");
            }

            log.info("Image generated successfully, size: {} bytes", imageBytes.length);
            return imageBytes;

        } catch (HuggingFaceException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error generating image", e);
            if (e.getMessage() != null && e.getMessage().contains("timeout")) {
                throw HuggingFaceException.timeout();
            }
            throw new HuggingFaceException("Failed to generate image: " + e.getMessage(), e);
        }
    }

    private Map<String, Object> buildRequestBody(String prompt, String negativePrompt, 
                                                  int width, int height) {
        if (negativePrompt != null && !negativePrompt.isBlank()) {
            return Map.of(
                    "inputs", prompt,
                    "parameters", Map.of(
                            "negative_prompt", negativePrompt,
                            "width", width,
                            "height", height
                    )
            );
        }
        return Map.of(
                "inputs", prompt,
                "parameters", Map.of(
                        "width", width,
                        "height", height
                )
        );
    }

    private void validateInput(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("Prompt cannot be empty");
        }
        if (prompt.length() > 1000) {
            throw new IllegalArgumentException("Prompt is too long (max 1000 characters)");
        }
    }

    private boolean isRetryableError(Throwable throwable) {
        if (throwable instanceof HuggingFaceException hfe) {
            // Retry on 503 (model loading) errors
            return hfe.getStatusCode() == 503;
        }
        return false;
    }

    private HuggingFaceException mapClientError(int statusCode, String body) {
        return switch (statusCode) {
            case 401 -> HuggingFaceException.unauthorized();
            case 429 -> HuggingFaceException.rateLimitExceeded();
            case 400 -> new HuggingFaceException("Invalid request: " + body, statusCode, "BAD_REQUEST");
            default -> new HuggingFaceException("API request failed: " + body, statusCode, "CLIENT_ERROR");
        };
    }

    private HuggingFaceException mapServerError(int statusCode, String body) {
        if (statusCode == 503 || (body != null && body.contains("loading"))) {
            return HuggingFaceException.modelLoading();
        }
        return switch (statusCode) {
            case 504 -> HuggingFaceException.timeout();
            default -> new HuggingFaceException("Server error: " + body, statusCode, "SERVER_ERROR");
        };
    }

    private String truncateForLog(String text) {
        return text.length() > 50 ? text.substring(0, 50) + "..." : text;
    }

    /**
     * Returns the model ID being used for image generation.
     */
    public String getModelId() {
        return model;
    }
}
