package dev.chat.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Response DTO for vision and document analysis.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record VisionResponse(
    boolean success,
    String content,
    String error,
    String model,
    String mode
) {
    public static VisionResponse success(String content, String model, String mode) {
        return new VisionResponse(true, content, null, model, mode);
    }
    
    public static VisionResponse error(String error) {
        return new VisionResponse(false, null, error, null, null);
    }
}
