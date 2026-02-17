package dev.chat.dto;

/**
 * Record representing a chat request from the frontend.
 */
public record ChatRequest(
    String message,
    String model
) {
    /**
     * Validates the request has required fields.
     */
    public boolean isValid() {
        return message != null && !message.isBlank() 
            && model != null && !model.isBlank();
    }
}
