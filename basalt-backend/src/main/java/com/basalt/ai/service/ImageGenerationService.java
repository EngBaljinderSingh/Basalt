package com.basalt.ai.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Image generation service with primary (AI Horde) and fallback (Pollinations) providers.
 *
 * <h3>Providers:</h3>
 * <ul>
 *   <li><strong>AI Horde:</strong> Free, crowd-sourced Stable Diffusion (primary)</li>
 *   <li><strong>Pollinations:</strong> Reliable public image endpoint (fallback)</li>
 * </ul>
 *
 * @see <a href="https://aihorde.net/api">AI Horde API docs</a>
 * @see <a href="https://image.pollinations.ai">Pollinations API</a>
 */
@Slf4j
@Service
public class ImageGenerationService {

    private static final String HORDE_BASE = "https://aihorde.net/api/v2";
        private static final String POLLINATIONS_BASE = "https://image.pollinations.ai/prompt/";
    private static final String ANONYMOUS_API_KEY = "0000000000";
    private static final int MAX_POLL_ATTEMPTS = 60;         // ~2 minutes max
    private static final Duration POLL_INTERVAL = Duration.ofSeconds(2);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);
    private static final int SUBMIT_RETRY_ATTEMPTS = 3;      // Retry rate-limited requests
    private static final Duration INITIAL_RETRY_DELAY = Duration.ofSeconds(2);

    private static final String CLIENT_AGENT = "basalt:1.0:unknown";

    private final WebClient hordeClient;

    public ImageGenerationService(WebClient.Builder webClientBuilder) {
        this.hordeClient = webClientBuilder
                .baseUrl(HORDE_BASE)
                .defaultHeader("apikey", ANONYMOUS_API_KEY)
                .defaultHeader("Client-Agent", CLIENT_AGENT)
                .defaultHeader("User-Agent", CLIENT_AGENT)
                .build();
    }

    /**
     * Generates an AI image from the given prompt.
          * Tries AI Horde first, falls back to Pollinations if Horde fails.
     *
     * @param prompt user's text prompt
     * @param width  desired width (clamped to nearest multiple of 64, max 1024)
     * @param height desired height (clamped to nearest multiple of 64, max 1024)
     * @return Mono containing the public image URL
     */
    public Mono<String> generateImage(String prompt, int width, int height) {
        int w = clampDimension(width);
        int h = clampDimension(height);

        log.info("Starting image generation — prompt=\"{}\", {}x{}", prompt, w, h);

        return generateImageWithHorde(prompt, w, h)
                .onErrorResume(ex -> {
                    log.warn("AI Horde failed ({}), using Pollinations fallback", ex.getMessage());
                    return generateImageWithPollinations(prompt, w, h);
                })
                .timeout(Duration.ofMinutes(3))
                .doOnSuccess(url -> log.info("Image generated successfully"))
                .doOnError(ex -> log.error("Image generation failed: {}", ex.getMessage()));
    }

    // ── AI Horde Provider ──────────────────────────────────────────────────

    private Mono<String> generateImageWithHorde(String prompt, int width, int height) {
        Map<String, Object> body = Map.of(
                "prompt", prompt + ", detailed, high quality",
                "params", Map.of(
                        "width", width,
                        "height", height,
                        "steps", 25,
                        "cfg_scale", 7.5,
                        "n", 1
                ),
                "nsfw", false,
                "censor_nsfw", true
        );

        return submitJobToHorde(body, 0)
                .flatMap(this::pollHordeUntilDone);
    }

    private Mono<String> submitJobToHorde(Map<String, Object> body, int attemptNumber) {
        return hordeClient.post()
                .uri("/generate/async")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(REQUEST_TIMEOUT)
                .map(resp -> (String) resp.get("id"))
                .onErrorResume(WebClientResponseException.class, ex -> {
                    boolean isRateLimited = ex.getStatusCode().value() == 429;
                    String message = ex.getResponseBodyAsString();
                    
                    if (isRateLimited && attemptNumber < SUBMIT_RETRY_ATTEMPTS) {
                        long delaySeconds = (long) Math.pow(2, attemptNumber) * INITIAL_RETRY_DELAY.getSeconds();
                        log.warn("AI Horde rate-limited. Retrying in {}s (attempt {}/{})",
                                delaySeconds, attemptNumber + 1, SUBMIT_RETRY_ATTEMPTS);
                        return Mono.delay(Duration.ofSeconds(delaySeconds))
                                .flatMap(ignored -> submitJobToHorde(body, attemptNumber + 1));
                    }
                    
                    String errorMsg = ex.getStatusCode().value() == 503
                            ? "AI Horde is temporarily overloaded. Retried " + (attemptNumber + 1) + " times."
                            : "AI Horde error (" + ex.getStatusCode() + "): " + message;
                    
                    log.error(errorMsg);
                    return Mono.error(new RuntimeException(errorMsg));
                });
    }

    @SuppressWarnings("unchecked")
    private Mono<String> pollHordeUntilDone(String jobId) {
        return Mono.defer(() -> hordeClient.get()
                .uri("/generate/status/{id}", jobId)
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(REQUEST_TIMEOUT))
                .flatMap(status -> {
                    Boolean done = (Boolean) status.get("done");
                    if (Boolean.TRUE.equals(done)) {
                        List<Map<String, Object>> gens =
                                (List<Map<String, Object>>) status.get("generations");
                        if (gens != null && !gens.isEmpty()) {
                            return Mono.just((String) gens.get(0).get("img"));
                        }
                        return Mono.error(new RuntimeException("No generations in completed job"));
                    }
                    return Mono.empty();
                })
                .repeatWhenEmpty(MAX_POLL_ATTEMPTS, flux -> flux.delayElements(POLL_INTERVAL))
                .switchIfEmpty(Mono.error(new RuntimeException(
                        "Image generation timed out after " + MAX_POLL_ATTEMPTS + " poll attempts")))
                .cast(String.class);
    }

    // ── Pollinations Provider (Fallback) ───────────────────────────────────

    private Mono<String> generateImageWithPollinations(String prompt, int width, int height) {
        String encodedPrompt = URLEncoder.encode(prompt + ", detailed, high quality", StandardCharsets.UTF_8);
        String imageUrl = POLLINATIONS_BASE + encodedPrompt
                + "?width=" + width
                + "&height=" + height
                + "&nologo=true"
                + "&model=flux";
        return Mono.just(imageUrl);
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private static int clampDimension(int value) {
        int clamped = Math.max(64, Math.min(value, 1024));
        return (clamped / 64) * 64;  // round down to nearest 64
    }
}

