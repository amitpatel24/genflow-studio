package dev.chat.dto;

/**
 * Record representing model information for the frontend dropdown.
 */
public record ModelInfo(
    String id,
    String name,
    String type
) {
    public static ModelInfo chat(String id, String name) {
        return new ModelInfo(id, name, "chat");
    }

    public static ModelInfo summarization(String id, String name) {
        return new ModelInfo(id, name, "summarization");
    }
}
