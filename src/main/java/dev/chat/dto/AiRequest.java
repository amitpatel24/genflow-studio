package dev.chat.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AiRequest(
    String message,
    String sessionId,
    String mode,
    String model,
    String jsonSchema,
    List<ChatMessage> history
) {
    public static AiRequest simple(String message) {
        return new AiRequest(message, null, null, null, null, null);
    }
    
    public static AiRequest withSession(String message, String sessionId) {
        return new AiRequest(message, sessionId, null, null, null, null);
    }
}
