package dev.chat.exception;

import dev.chat.dto.AiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.net.SocketTimeoutException;
import java.util.concurrent.TimeoutException;

/**
 * Global exception handler for graceful error handling across the application.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(AiApiException.class)
    public ResponseEntity<AiResponse> handleAiApiException(AiApiException ex) {
        log.error("AI API error: {} (Type: {}, Status: {})", 
            ex.getMessage(), ex.getErrorType(), ex.getStatusCode());
        
        HttpStatus status = switch (ex.getStatusCode()) {
            case 401 -> HttpStatus.UNAUTHORIZED;
            case 429 -> HttpStatus.TOO_MANY_REQUESTS;
            case 503 -> HttpStatus.SERVICE_UNAVAILABLE;
            case 504 -> HttpStatus.GATEWAY_TIMEOUT;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };

        return ResponseEntity.status(status).body(AiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(WebClientResponseException.TooManyRequests.class)
    public ResponseEntity<AiResponse> handleRateLimitException(WebClientResponseException.TooManyRequests ex) {
        log.warn("Rate limit exceeded: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
            .body(AiResponse.error("Rate limit exceeded. Please wait a moment and try again."));
    }

    @ExceptionHandler({TimeoutException.class, SocketTimeoutException.class})
    public ResponseEntity<AiResponse> handleTimeoutException(Exception ex) {
        log.error("Request timeout: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT)
            .body(AiResponse.error("The request timed out. The model might be loading, please try again."));
    }

    @ExceptionHandler(WebClientRequestException.class)
    public ResponseEntity<AiResponse> handleWebClientRequestException(WebClientRequestException ex) {
        log.error("Network error communicating with AI service: {}", ex.getMessage());
        
        if (ex.getCause() instanceof SocketTimeoutException) {
            return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT)
                .body(AiResponse.error("Connection timed out. Please try again."));
        }
        
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(AiResponse.error("Unable to connect to the AI service. Please try again later."));
    }

    @ExceptionHandler(WebClientResponseException.class)
    public ResponseEntity<AiResponse> handleWebClientResponseException(WebClientResponseException ex) {
        log.error("AI API response error: {} - {}", ex.getStatusCode(), ex.getMessage());
        
        String message = switch (ex.getStatusCode().value()) {
            case 401 -> "Invalid API key. Please check your configuration.";
            case 403 -> "Access forbidden. Please check your API permissions.";
            case 404 -> "Model not found. Please check the model ID.";
            case 503 -> "Model is loading. Please wait a moment and try again.";
            default -> "An error occurred while processing your request.";
        };

        return ResponseEntity.status(ex.getStatusCode())
            .body(AiResponse.error(message));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<AiResponse> handleIllegalArgumentException(IllegalArgumentException ex) {
        log.warn("Invalid request: {}", ex.getMessage());
        return ResponseEntity.badRequest()
            .body(AiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Void> handleNoResourceFoundException(NoResourceFoundException ex) {
        // Silently ignore favicon.ico requests
        if (ex.getResourcePath().contains("favicon")) {
            return ResponseEntity.notFound().build();
        }
        log.warn("Resource not found: {}", ex.getResourcePath());
        return ResponseEntity.notFound().build();
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<AiResponse> handleGenericException(Exception ex) {
        log.error("Unexpected error: ", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(AiResponse.error("An unexpected error occurred. Please try again."));
    }
}
