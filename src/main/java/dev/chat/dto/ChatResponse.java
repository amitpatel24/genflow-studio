package dev.chat.dto;

import java.time.LocalDateTime;

/**
 * Record representing a chat response to the frontend.
 */
public record ChatResponse(
    String message,
    String model,
    LocalDateTime timestamp,
    boolean success,
    String error
) {
    /**
     * Creates a successful response.
     */
    public static ChatResponse success(String message, String model) {
        return new ChatResponse(message, model, LocalDateTime.now(), true, null);
    }

    /**
     * Creates an error response.
     */
    public static ChatResponse error(String errorMessage) {
        return new ChatResponse(null, null, LocalDateTime.now(), false, errorMessage);
    }
}
