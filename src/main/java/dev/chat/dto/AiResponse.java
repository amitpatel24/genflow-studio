package dev.chat.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AiResponse(
    boolean success,
    String message,
    String error,
    String model,
    String mode,
    String sessionId,
    Object data,
    Usage usage
) {
    public record Usage(
        int promptTokens,
        int completionTokens,
        int totalTokens
    ) {}
    
    public static AiResponse success(String message, String model) {
        return new AiResponse(true, message, null, model, null, null, null, null);
    }
    
    public static AiResponse success(String message, String model, String mode, String sessionId) {
        return new AiResponse(true, message, null, model, mode, sessionId, null, null);
    }
    
    public static AiResponse successWithData(String message, String model, Object data) {
        return new AiResponse(true, message, null, model, null, null, data, null);
    }
    
    public static AiResponse successWithUsage(String message, String model, int promptTokens, int completionTokens) {
        var usage = new Usage(promptTokens, completionTokens, promptTokens + completionTokens);
        return new AiResponse(true, message, null, model, null, null, null, usage);
    }
    
    public static AiResponse error(String error) {
        return new AiResponse(false, null, error, null, null, null, null, null);
    }
}
