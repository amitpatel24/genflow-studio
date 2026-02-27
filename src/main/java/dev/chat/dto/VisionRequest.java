package dev.chat.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Request DTO for image vision analysis.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record VisionRequest(
    String imageBase64,
    String prompt
) {
    public VisionRequest {
        if (prompt == null || prompt.isBlank()) {
            prompt = "Describe this image in detail.";
        }
    }
}
