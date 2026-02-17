package dev.chat.controller;

import dev.chat.dto.ChatRequest;
import dev.chat.dto.ChatResponse;
import dev.chat.dto.ModelInfo;
import dev.chat.service.AiService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controller handling AI chat requests and page routing.
 * Uses constructor injection for clean dependency management.
 */
@Controller
public class AiController {

    private static final Logger log = LoggerFactory.getLogger(AiController.class);
    
    private final AiService aiService;

    public AiController(AiService aiService) {
        this.aiService = aiService;
    }

    /**
     * Serves the main chat page with available models.
     */
    @GetMapping("/")
    public String index(Model model) {
        List<ModelInfo> models = aiService.getAvailableModels();
        model.addAttribute("models", models);
        return "index";
    }

    /**
     * Handles chat API requests from the frontend.
     */
    @PostMapping("/api/chat")
    @ResponseBody
    public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest request) {
        log.info("Received chat request - Model: {}, Message length: {}", 
            request.model(), 
            request.message() != null ? request.message().length() : 0);
        
        if (!request.isValid()) {
            return ResponseEntity.badRequest()
                .body(ChatResponse.error("Message and model are required"));
        }
        
        ChatResponse response = aiService.chat(request.message(), request.model());
        log.info("Chat response generated successfully for model: {}", request.model());
        
        return ResponseEntity.ok(response);
    }

    /**
     * Returns list of available AI models.
     */
    @GetMapping("/api/models")
    @ResponseBody
    public ResponseEntity<List<ModelInfo>> getModels() {
        return ResponseEntity.ok(aiService.getAvailableModels());
    }

    /**
     * Health check endpoint.
     */
    @GetMapping("/api/health")
    @ResponseBody
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("OK");
    }
}
