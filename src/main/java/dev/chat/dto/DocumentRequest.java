package dev.chat.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Request DTO for document/PDF analysis.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DocumentRequest(
    String fileBase64,
    String prompt
) {
    public DocumentRequest {
        if (prompt == null || prompt.isBlank()) {
            prompt = "Summarize this document and extract key insights.";
        }
    }
}
