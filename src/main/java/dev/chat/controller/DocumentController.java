package dev.chat.controller;

import dev.chat.dto.DocumentRequest;
import dev.chat.dto.VisionResponse;
import dev.chat.service.GeminiVisionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST Controller for document/PDF analysis using Google Gemini.
 */
@RestController
@RequestMapping("/api")
public class DocumentController {

    private static final Logger log = LoggerFactory.getLogger(DocumentController.class);
    
    private final GeminiVisionService visionService;

    public DocumentController(GeminiVisionService visionService) {
        this.visionService = visionService;
    }

    /**
     * Analyze a PDF document using Gemini.
     * 
     * POST /api/document
     * 
     * Request:
     * {
     *   "fileBase64": "base64 encoded PDF data",
     *   "prompt": "Custom analysis prompt" (optional)
     * }
     * 
     * curl -X POST http://localhost:8080/api/document \
     *   -H "Content-Type: application/json" \
     *   -d '{"fileBase64": "..."}'
     */
    @PostMapping("/document")
    public ResponseEntity<VisionResponse> analyzeDocument(@RequestBody DocumentRequest request) {
        log.info("Document analysis request received");
        
        if (request.fileBase64() == null || request.fileBase64().isBlank()) {
            return ResponseEntity.badRequest()
                .body(VisionResponse.error("Document data is required"));
        }
        
        // Clean base64 data (remove data URL prefix if present)
        String base64Data = cleanBase64(request.fileBase64());
        
        VisionResponse response = visionService.analyzeDocument(base64Data, request.prompt());
        return ResponseEntity.ok(response);
    }

    /**
     * Remove data URL prefix from base64 string if present.
     */
    private String cleanBase64(String base64Data) {
        if (base64Data != null && base64Data.contains(",")) {
            return base64Data.substring(base64Data.indexOf(",") + 1);
        }
        return base64Data;
    }
}
