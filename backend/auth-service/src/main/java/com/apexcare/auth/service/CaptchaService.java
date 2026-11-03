package com.apexcare.auth.service;

import com.apexcare.auth.dto.CaptchaResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * CAPTCHA generation and validation.
 *
 * <p><b>Single-instance only.</b> The store is an in-process
 * {@link ConcurrentHashMap}. In a multi-instance deployment, CAPTCHAs
 * generated on one instance cannot be validated on another. Before scaling
 * horizontally, migrate the store to Redis (or another shared cache) — the
 * public API of this class does not change.
 *
 * <p><b>No bypass tokens.</b> Any "demo bypass" logic MUST NOT exist in
 * production code — an attacker with the bypass string gets unlimited
 * auth attempts.
 */
@Service
public class CaptchaService {

    private static final Logger log = LoggerFactory.getLogger(CaptchaService.class);

    private static final String CHARSET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"; // no 0/O, 1/I
    private static final int CAPTCHA_LENGTH = 5;
    private static final long TTL_MINUTES = 5L;

    /** Hard cap on the store — protects against DoS via CAPTCHA-farming. */
    private static final int MAX_STORE_SIZE = 10_000;

    /** Maximum wrong-answer attempts per CAPTCHA ID before it is discarded. */
    private static final int MAX_ATTEMPTS_PER_CAPTCHA = 3;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final Map<String, CaptchaRecord> store = new ConcurrentHashMap<>();

    private record CaptchaRecord(String answer, Instant expiresAt, AtomicLong attempts) {}

    // -----------------------------------------------------------------
    // GENERATE
    // -----------------------------------------------------------------
    public CaptchaResponse generateCaptcha() {
        // Enforce a hard cap rather than silently accepting unbounded growth.
        if (store.size() >= MAX_STORE_SIZE) {
            evictExpired();
            if (store.size() >= MAX_STORE_SIZE) {
                // Still full after cleanup — reject rather than grow without bound.
                log.warn("CAPTCHA store at capacity ({}); rejecting new request",
                        MAX_STORE_SIZE);
                throw new IllegalStateException(
                        "CAPTCHA service temporarily unavailable. Please retry.");
            }
        }

        String captchaId = UUID.randomUUID().toString();
        StringBuilder answerBuilder = new StringBuilder(CAPTCHA_LENGTH);
        for (int i = 0; i < CAPTCHA_LENGTH; i++) {
            answerBuilder.append(CHARSET.charAt(RANDOM.nextInt(CHARSET.length())));
        }
        String answer = answerBuilder.toString();
        Instant expiresAt = Instant.now().plus(TTL_MINUTES, ChronoUnit.MINUTES);

        store.put(captchaId,
                new CaptchaRecord(answer, expiresAt, new AtomicLong(0)));

        String svg = buildSvgChallenge(answer);

        log.debug("CAPTCHA generated id={} expiresAt={}", captchaId, expiresAt);

        return new CaptchaResponse(captchaId, svg, expiresAt);
    }

    // -----------------------------------------------------------------
    // VALIDATE
    // -----------------------------------------------------------------
    /**
     * Validates a CAPTCHA answer.
     *
     * <p>On success the record is consumed (single-use).
     * <p>On failure the attempt count is incremented; after
     * {@link #MAX_ATTEMPTS_PER_CAPTCHA} failures the record is discarded.
     */
    public boolean validateCaptcha(String captchaId, String userAnswer) {
        if (captchaId == null || userAnswer == null) {
            return false;
        }

        CaptchaRecord record = store.get(captchaId);
        if (record == null) {
            log.warn("CAPTCHA validation failed: unknown or consumed id={}", captchaId);
            return false;
        }

        if (Instant.now().isAfter(record.expiresAt())) {
            store.remove(captchaId);
            log.warn("CAPTCHA validation failed: expired id={}", captchaId);
            return false;
        }

        long attempts = record.attempts().incrementAndGet();
        if (attempts > MAX_ATTEMPTS_PER_CAPTCHA) {
            store.remove(captchaId);
            log.warn("CAPTCHA validation failed: too many attempts id={} attempts={}",
                    captchaId, attempts);
            return false;
        }

        boolean match = constantTimeEquals(
                record.answer().toUpperCase(),
                userAnswer.trim().toUpperCase());

        if (match) {
            // Single-use: remove on success as well.
            store.remove(captchaId);
            log.debug("CAPTCHA validated id={}", captchaId);
            return true;
        }

        log.warn("CAPTCHA validation failed: wrong answer id={} attempts={}",
                captchaId, attempts);
        return false;
    }

    // -----------------------------------------------------------------
    // CLEANUP
    // -----------------------------------------------------------------
    /** Scheduled cleanup so the store does not grow unbounded. */
    @Scheduled(fixedDelayString = "${apexcare.captcha.cleanup-interval-ms:60000}")
    public void scheduledCleanup() {
        int before = store.size();
        evictExpired();
        int removed = before - store.size();
        if (removed > 0) {
            log.debug("CAPTCHA cleanup: evicted {} expired entries", removed);
        }
    }

    private void evictExpired() {
        Instant now = Instant.now();
        store.entrySet().removeIf(e -> now.isAfter(e.getValue().expiresAt()));
    }

    // -----------------------------------------------------------------
    // SVG RENDERING
    // -----------------------------------------------------------------
    private String buildSvgChallenge(String text) {
        int width = 160;
        int height = 50;

        StringBuilder svg = new StringBuilder(1024);
        svg.append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"")
           .append(width).append("\" height=\"").append(height)
           .append("\" viewBox=\"0 0 ").append(width).append(' ')
           .append(height).append("\">");

        svg.append("<defs><linearGradient id=\"bg\" x1=\"0%\" y1=\"0%\" x2=\"100%\" y2=\"100%\">")
           .append("<stop offset=\"0%\" stop-color=\"#0f172a\"/>")
           .append("<stop offset=\"100%\" stop-color=\"#1e293b\"/>")
           .append("</linearGradient></defs>");

        svg.append("<rect width=\"").append(width).append("\" height=\"")
           .append(height).append("\" rx=\"8\" fill=\"url(#bg)\"/>");

        String[] lineColors = {"#0d9488", "#38bdf8", "#818cf8", "#f43f5e", "#f59e0b"};
        for (int i = 0; i < 4; i++) {
            int x1 = RANDOM.nextInt(20);
            int y1 = RANDOM.nextInt(height);
            int x2 = width - RANDOM.nextInt(20);
            int y2 = RANDOM.nextInt(height);
            String color = lineColors[RANDOM.nextInt(lineColors.length)];
            svg.append("<line x1=\"").append(x1).append("\" y1=\"").append(y1)
               .append("\" x2=\"").append(x2).append("\" y2=\"").append(y2)
               .append("\" stroke=\"").append(color)
               .append("\" stroke-width=\"1.5\" opacity=\"0.45\"/>");
        }

        for (int i = 0; i < 18; i++) {
            int cx = RANDOM.nextInt(width);
            int cy = RANDOM.nextInt(height);
            int r = 1 + RANDOM.nextInt(2);
            svg.append("<circle cx=\"").append(cx).append("\" cy=\"").append(cy)
               .append("\" r=\"").append(r).append("\" fill=\"#94a3b8\" opacity=\"0.4\"/>");
        }

        String[] textColors = {"#2dd4bf", "#38bdf8", "#a78bfa", "#fb7185", "#facc15"};
        int charSpacing = (width - 30) / text.length();

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            int x = 20 + (i * charSpacing) + (RANDOM.nextInt(6) - 3);
            int y = 33 + (RANDOM.nextInt(6) - 3);
            int rotate = RANDOM.nextInt(36) - 18;
            String color = textColors[i % textColors.length];

            svg.append("<text x=\"").append(x).append("\" y=\"").append(y)
               .append("\" fill=\"").append(color)
               .append("\" font-family=\"'Courier New', monospace, sans-serif\"")
               .append(" font-size=\"24\" font-weight=\"bold\"")
               .append(" transform=\"rotate(").append(rotate).append(' ')
               .append(x).append(' ').append(y).append(")\">")
               .append(c)
               .append("</text>");
        }

        svg.append("</svg>");
        return svg.toString();
    }

    // -----------------------------------------------------------------
    // HELPERS
    // -----------------------------------------------------------------
    private static boolean constantTimeEquals(String a, String b) {
        byte[] ab = a.getBytes(StandardCharsets.UTF_8);
        byte[] bb = b.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(ab, bb);
    }
}
