package dev.chat.dto;

import java.util.Map;

/**
 * Record representing a request to the Hugging Face Inference API.
 * Supports both text generation and summarization tasks.
 */
public record HfRequest(
    String inputs,
    Map<String, Object> parameters
) {
    /**
     * Creates a simple request with just the input text.
     */
    public static HfRequest of(String inputs) {
        return new HfRequest(inputs, Map.of());
    }

    /**
     * Creates a request with input text and custom parameters.
     */
    public static HfRequest withParameters(String inputs, Map<String, Object> parameters) {
        return new HfRequest(inputs, parameters);
    }

    /**
     * Creates a chat request with default generation parameters.
     */
    public static HfRequest forChat(String prompt) {
        return new HfRequest(prompt, Map.of(
            "max_new_tokens", 512,
            "temperature", 0.7,
            "top_p", 0.95,
            "do_sample", true,
            "return_full_text", false
        ));
    }

    /**
     * Creates a summarization request with default parameters.
     */
    public static HfRequest forSummarization(String text) {
        return new HfRequest(text, Map.of(
            "max_length", 150,
            "min_length", 30,
            "do_sample", false
        ));
    }
}
