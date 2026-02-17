package dev.chat.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Record representing a response from the Hugging Face Inference API.
 * Handles both text generation and summarization responses.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record HfResponse(
    @JsonAlias({"generated_text", "summary_text"})
    String generatedText
) {
    /**
     * Creates an empty response (used for error cases).
     */
    public static HfResponse empty() {
        return new HfResponse("");
    }

    /**
     * Creates a response with the given text.
     */
    public static HfResponse of(String text) {
        return new HfResponse(text);
    }

    /**
     * Checks if the response has valid content.
     */
    public boolean hasContent() {
        return generatedText != null && !generatedText.isBlank();
    }
}
