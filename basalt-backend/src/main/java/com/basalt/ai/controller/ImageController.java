package com.basalt.ai.controller;

import com.basalt.ai.model.ImageRequest;
import com.basalt.ai.service.ImageGenerationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * REST controller for AI image generation.
 *
 * <p>Uses <b>AI Horde</b> — a free, open-source, crowd-sourced
 * Stable Diffusion API that requires no API key. The backend
 * submits the generation job, polls until complete, and returns
 * the final image URL to the Angular frontend.
 *
 * <h3>Endpoint:</h3>
 * {@code POST /api/images/generate}
 *
 * <h3>Response (JSON):</h3>
 * <pre>{@code
 * {
 *   "imageUrl": "https://...r2.cloudflarestorage.com/stable-horde/..."
 * }
 * }</pre>
 */
@Slf4j
@RestController
@RequestMapping("/images")
@RequiredArgsConstructor
public class ImageController {

    private final ImageGenerationService imageService;

    /**
     * Generates an AI image from a natural-language prompt.
     *
     * <p>The request is forwarded to AI Horde, which generates the image
     * using crowd-sourced Stable Diffusion workers. Typical generation
     * time is 10–30 seconds. The response contains a direct URL to the
     * generated image hosted on Cloudflare R2.
     *
     * @param request the image generation parameters
     * @return JSON with the {@code imageUrl} field, or error details
     */
    @PostMapping("/generate")
    public Mono<ResponseEntity<Map<String, String>>> generateImage(
            @Valid @RequestBody ImageRequest request) {

        log.info("Image generation request — prompt=\"{}\"", request.getPrompt());

        return imageService
                .generateImage(request.getPrompt(), request.getWidth(), request.getHeight())
                .map(imageUrl -> ResponseEntity.ok(Map.of("imageUrl", imageUrl)))
                .onErrorResume(ex -> {
                    String errorMsg = ex.getMessage();
                    log.error("Image generation failed: {}", errorMsg);
                    
                    // Provide helpful error messages based on error type
                    String userMessage;
                    if (errorMsg != null && (errorMsg.contains("429") || errorMsg.contains("Too Many Requests"))) {
                        userMessage = "AI Horde is temporarily overloaded. Retried up to 3 times. Please try again in 1-2 minutes.";
                    } else if (errorMsg != null && (errorMsg.contains("403") || errorMsg.contains("FORBIDDEN"))) {
                        userMessage = "AI Horde requires kudos for this request. Try smaller dimensions (max 512x512) or wait a few minutes for credit regeneration.";
                    } else if (errorMsg != null && (errorMsg.contains("530") || errorMsg.contains("503"))) {
                        userMessage = "Image generation service is temporarily unavailable. Please try again later.";
                    } else {
                        userMessage = "Image generation failed. Please try again with a simpler prompt or smaller image dimensions.";
                    }
                    
                    return Mono.just(ResponseEntity
                            .status(HttpStatus.BAD_GATEWAY)
                            .body(Map.of("error", userMessage)));
                });
    }
}
