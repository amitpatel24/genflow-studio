package dev.chat.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record GroqRequest(
    String model,
    List<ChatMessage> messages,
    
    @JsonProperty("max_tokens")
    Integer maxTokens,
    
    Double temperature,
    Boolean stream,
    
    @JsonProperty("response_format")
    Map<String, String> responseFormat,
    
    List<Tool> tools,
    
    @JsonProperty("tool_choice")
    String toolChoice
) {
    public record Tool(
        String type,
        Function function
    ) {
        public record Function(
            String name,
            String description,
            Map<String, Object> parameters
        ) {}
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private String model = "llama-3.3-70b-versatile";
        private List<ChatMessage> messages;
        private Integer maxTokens = 1024;
        private Double temperature = 0.7;
        private Boolean stream = false;
        private Map<String, String> responseFormat;
        private List<Tool> tools;
        private String toolChoice;
        
        public Builder model(String model) {
            this.model = model;
            return this;
        }
        
        public Builder messages(List<ChatMessage> messages) {
            this.messages = messages;
            return this;
        }
        
        public Builder maxTokens(Integer maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }
        
        public Builder temperature(Double temperature) {
            this.temperature = temperature;
            return this;
        }
        
        public Builder stream(Boolean stream) {
            this.stream = stream;
            return this;
        }
        
        public Builder jsonMode() {
            this.responseFormat = Map.of("type", "json_object");
            return this;
        }
        
        public Builder tools(List<Tool> tools) {
            this.tools = tools;
            return this;
        }
        
        public Builder toolChoice(String toolChoice) {
            this.toolChoice = toolChoice;
            return this;
        }
        
        public GroqRequest build() {
            return new GroqRequest(model, messages, maxTokens, temperature, stream, responseFormat, tools, toolChoice);
        }
    }
}
