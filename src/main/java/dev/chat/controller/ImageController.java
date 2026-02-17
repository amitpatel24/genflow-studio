package dev.chat.controller;

import dev.chat.service.ImageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Controller for image generation endpoints.
 */
@RestController
@RequestMapping("/api/image")
public class ImageController {

    private static final Logger log = LoggerFactory.getLogger(ImageController.class);

    private final ImageService imageService;

    public ImageController(ImageService imageService) {
        this.imageService = imageService;
    }

    /**
     * Generates an image from a text prompt.
     *
     * @param request JSON body containing prompt and optional parameters
     * @return Generated image as PNG binary
     */
    @PostMapping
    public ResponseEntity<byte[]> generateImage(@RequestBody ImageRequest request) {
        log.info("Received image generation request - prompt length: {}", 
                request.prompt() != null ? request.prompt().length() : 0);

        if (request.prompt() == null || request.prompt().isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        int width = request.width() != null ? request.width() : 512;
        int height = request.height() != null ? request.height() : 512;

        byte[] imageBytes = imageService.generateImage(
                request.prompt(),
                request.negativePrompt(),
                width,
                height
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.IMAGE_PNG);
        headers.setContentLength(imageBytes.length);
        headers.setContentDispositionFormData("attachment", "generated-image.png");

        return new ResponseEntity<>(imageBytes, headers, HttpStatus.OK);
    }

    /**
     * Returns information about the image generation model.
     */
    @GetMapping("/info")
    public ResponseEntity<Map<String, Object>> getModelInfo() {
        return ResponseEntity.ok(Map.of(
                "model", imageService.getModelId(),
                "supportedSizes", new int[]{512, 768, 1024},
                "defaultSize", 512
        ));
    }

    /**
     * Request record for image generation.
     */
    public record ImageRequest(
            String prompt,
            String negativePrompt,
            Integer width,
            Integer height
    ) {}
}
