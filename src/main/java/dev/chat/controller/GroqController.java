package dev.chat.controller;

import dev.chat.dto.AiRequest;
import dev.chat.dto.AiResponse;
import dev.chat.dto.CodeResponse;
import dev.chat.service.ConversationMemoryService;
import dev.chat.service.GroqService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

/**
 * REST Controller for all Groq AI endpoints.
 */
@RestController
@RequestMapping("/api")
@CrossOrigin(origins = {"http://localhost:8080", "http://127.0.0.1:8080"}, allowCredentials = "true")
public class GroqController {

    private static final Logger log = LoggerFactory.getLogger(GroqController.class);
    
    private final GroqService groqService;
    private final ConversationMemoryService memoryService;

    public GroqController(GroqService groqService, ConversationMemoryService memoryService) {
        this.groqService = groqService;
        this.memoryService = memoryService;
    }

    // ==================== CHAT ====================

    /**
     * Standard chat completion with conversation history.
     * POST /api/chat
     * 
     * curl -X POST http://localhost:8080/api/chat \
     *   -H "Content-Type: application/json" \
     *   -d '{"message": "Hello, how are you?", "sessionId": "optional-session-id"}'
     */
    @PostMapping("/chat")
    public ResponseEntity<AiResponse> chat(@RequestBody AiRequest request) {
        log.info("Chat request: {}", truncate(request.message()));
        
        if (request.message() == null || request.message().isBlank()) {
            return ResponseEntity.badRequest().body(AiResponse.error("Message cannot be empty"));
        }
        
        AiResponse response = groqService.chatCompletion(request.message(), request.sessionId());
        return ResponseEntity.ok(response);
    }

    /**
     * Streaming chat with Server-Sent Events.
     * GET /api/chat/stream?message=Hello&sessionId=xxx
     * 
     * curl -N "http://localhost:8080/api/chat/stream?message=Tell%20me%20a%20joke"
     */
    @GetMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamChat(
            @RequestParam String message,
            @RequestParam(required = false) String sessionId) {
        log.info("Stream chat request: {}", truncate(message));
        
        if (message == null || message.isBlank()) {
            return Flux.just("data: {\"error\": \"Message cannot be empty\"}\n\n");
        }
        
        return groqService.streamingChat(message, sessionId)
            .map(token -> "data: " + escapeForSSE(token) + "\n\n")
            .concatWith(Flux.just("data: [DONE]\n\n"))
            .onErrorResume(e -> {
                log.error("Stream error", e);
                return Flux.just("data: {\"error\": \"" + e.getMessage() + "\"}\n\n");
            });
    }

    // ==================== SUMMARIZE ====================

    /**
     * Summarize text.
     * POST /api/summarize
     * 
     * curl -X POST http://localhost:8080/api/summarize \
     *   -H "Content-Type: application/json" \
     *   -d '{"message": "Long text to summarize..."}'
     */
    @PostMapping("/summarize")
    public ResponseEntity<AiResponse> summarize(@RequestBody AiRequest request) {
        log.info("Summarize request, text length: {}", 
            request.message() != null ? request.message().length() : 0);
        
        if (request.message() == null || request.message().isBlank()) {
            return ResponseEntity.badRequest().body(AiResponse.error("Text to summarize cannot be empty"));
        }
        
        if (request.message().length() < 50) {
            return ResponseEntity.badRequest().body(AiResponse.error("Text is too short to summarize"));
        }
        
        AiResponse response = groqService.summarize(request.message());
        return ResponseEntity.ok(response);
    }

    // ==================== CODE ASSISTANT ====================

    /**
     * Code assistant for programming help.
     * POST /api/code
     * 
     * curl -X POST http://localhost:8080/api/code \
     *   -H "Content-Type: application/json" \
     *   -d '{"message": "Write a Python function to sort a list"}'
     */
    @PostMapping("/code")
    public ResponseEntity<CodeResponse> codeAssistant(@RequestBody AiRequest request) {
        log.info("Code assistant request: {}", truncate(request.message()));
        
        if (request.message() == null || request.message().isBlank()) {
            return ResponseEntity.badRequest().body(CodeResponse.error("Code request cannot be empty"));
        }
        
        CodeResponse response = groqService.codeAssistant(request.message());
        return ResponseEntity.ok(response);
    }

    // ==================== IMAGE PROMPT ENHANCER ====================

    /**
     * Enhance prompts for image generation.
     * POST /api/enhance-image
     * 
     * curl -X POST http://localhost:8080/api/enhance-image \
     *   -H "Content-Type: application/json" \
     *   -d '{"message": "a cat sitting on a windowsill"}'
     */
    @PostMapping("/enhance-image")
    public ResponseEntity<AiResponse> enhanceImagePrompt(@RequestBody AiRequest request) {
        log.info("Enhance image prompt request: {}", truncate(request.message()));
        
        if (request.message() == null || request.message().isBlank()) {
            return ResponseEntity.badRequest().body(AiResponse.error("Image prompt cannot be empty"));
        }
        
        AiResponse response = groqService.enhanceImagePrompt(request.message());
        return ResponseEntity.ok(response);
    }

    // ==================== JSON GENERATOR ====================

    /**
     * Generate structured JSON output.
     * POST /api/json
     * 
     * curl -X POST http://localhost:8080/api/json \
     *   -H "Content-Type: application/json" \
     *   -d '{"message": "Generate a user profile for John Doe, age 30, developer", "jsonSchema": "{\"type\": \"object\"}"}'
     */
    @PostMapping("/json")
    public ResponseEntity<AiResponse> generateJson(@RequestBody AiRequest request) {
        log.info("JSON generation request: {}", truncate(request.message()));
        
        if (request.message() == null || request.message().isBlank()) {
            return ResponseEntity.badRequest().body(AiResponse.error("JSON request cannot be empty"));
        }
        
        AiResponse response = groqService.structuredOutput(request.message(), request.jsonSchema());
        return ResponseEntity.ok(response);
    }

    // ==================== FUNCTION CALLING ====================

    /**
     * Function calling demonstration.
     * POST /api/function
     * 
     * curl -X POST http://localhost:8080/api/function \
     *   -H "Content-Type: application/json" \
     *   -d '{"message": "What is the weather in Tokyo?"}'
     */
    @PostMapping("/function")
    public ResponseEntity<AiResponse> functionCalling(@RequestBody AiRequest request) {
        log.info("Function calling request: {}", truncate(request.message()));
        
        if (request.message() == null || request.message().isBlank()) {
            return ResponseEntity.badRequest().body(AiResponse.error("Function request cannot be empty"));
        }
        
        AiResponse response = groqService.functionCallingExample(request.message());
        return ResponseEntity.ok(response);
    }

    // ==================== SESSION MANAGEMENT ====================

    /**
     * Create a new conversation session.
     * POST /api/session
     */
    @PostMapping("/session")
    public ResponseEntity<AiResponse> createSession() {
        String sessionId = memoryService.createSession();
        return ResponseEntity.ok(AiResponse.success("Session created", null, null, sessionId));
    }

    /**
     * Clear a conversation session.
     * DELETE /api/session/{sessionId}
     */
    @DeleteMapping("/session/{sessionId}")
    public ResponseEntity<AiResponse> clearSession(@PathVariable String sessionId) {
        memoryService.clearSession(sessionId);
        return ResponseEntity.ok(AiResponse.success("Session cleared", null, null, null));
    }

    // ==================== MODELS ====================

    /**
     * Get available models.
     * GET /api/models
     */
    @GetMapping("/models")
    public ResponseEntity<?> getModels() {
        return ResponseEntity.ok(groqService.getAvailableModels());
    }

    // ==================== HELPERS ====================

    private String truncate(String text) {
        if (text == null) return "null";
        return text.length() > 50 ? text.substring(0, 50) + "..." : text;
    }

    private String escapeForSSE(String text) {
        if (text == null) return "";
        return text.replace("\n", "\\n").replace("\r", "");
    }
}
