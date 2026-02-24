package dev.chat.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GroqResponse(
    String id,
    String object,
    Long created,
    String model,
    List<Choice> choices,
    Usage usage
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Choice(
        Integer index,
        Message message,
        Delta delta,
        @JsonProperty("finish_reason")
        String finishReason
    ) {}
    
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Message(
        String role,
        String content,
        @JsonProperty("tool_calls")
        List<ToolCall> toolCalls
    ) {}
    
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Delta(
        String role,
        String content
    ) {}
    
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ToolCall(
        String id,
        String type,
        Function function
    ) {
        public record Function(
            String name,
            String arguments
        ) {}
    }
    
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Usage(
        @JsonProperty("prompt_tokens")
        Integer promptTokens,
        @JsonProperty("completion_tokens")
        Integer completionTokens,
        @JsonProperty("total_tokens")
        Integer totalTokens
    ) {}
    
    public String getContent() {
        if (choices != null && !choices.isEmpty()) {
            var choice = choices.get(0);
            if (choice.message() != null) {
                return choice.message().content();
            }
            if (choice.delta() != null) {
                return choice.delta().content();
            }
        }
        return null;
    }
}
