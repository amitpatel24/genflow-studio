package dev.chat.controller;

import dev.chat.dto.VisionRequest;
import dev.chat.dto.VisionResponse;
import dev.chat.service.GeminiVisionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST Controller for image vision analysis using Google Gemini.
 */
@RestController
@RequestMapping("/api")
public class VisionController {

    private static final Logger log = LoggerFactory.getLogger(VisionController.class);
    
    private final GeminiVisionService visionService;

    public VisionController(GeminiVisionService visionService) {
        this.visionService = visionService;
    }

    /**
     * Analyze an image using Gemini Vision.
     * 
     * POST /api/vision
     * 
     * Request:
     * {
     *   "imageBase64": "base64 encoded image data",
     *   "prompt": "Describe this image" (optional)
     * }
     * 
     * curl -X POST http://localhost:8080/api/vision \
     *   -H "Content-Type: application/json" \
     *   -d '{"imageBase64": "...", "prompt": "What is in this image?"}'
     */
    @PostMapping("/vision")
    public ResponseEntity<VisionResponse> analyzeImage(@RequestBody VisionRequest request) {
        log.info("Vision analysis request received");
        
        if (request.imageBase64() == null || request.imageBase64().isBlank()) {
            return ResponseEntity.badRequest()
                .body(VisionResponse.error("Image data is required"));
        }
        
        // Clean base64 data (remove data URL prefix if present)
        String base64Data = cleanBase64(request.imageBase64());
        
        VisionResponse response = visionService.analyzeImage(base64Data, request.prompt());
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
