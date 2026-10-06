package com.modeltech.datamasteryhub.common.ratelimit;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Limitation de débit par adresse IP pour les formulaires publics (contact,
 * diagnostic, candidature partenaire, newsletter) : {@code app.rate-limit.forms.per-hour}
 * requêtes par heure et par IP (10 par défaut). Au-delà : 429.
 *
 * <p>État en mémoire (par instance) : suffisant pour freiner le spam d'un
 * déploiement à une seule instance ; à remplacer par un store partagé si le
 * backend est répliqué.
 *
 * <p>IP cliente : dernière entrée de {@code X-Forwarded-For} (celle ajoutée par le
 * reverse proxy de confiance — Traefik), jamais la première, que le client peut
 * falsifier pour contourner la limite.
 */
@Component
@Slf4j
public class IpRateLimiter {

    private static final Duration WINDOW = Duration.ofHours(1);
    private static final int CLEANUP_THRESHOLD = 5_000;

    private final int perHour;
    private final Map<String, Entry> buckets = new ConcurrentHashMap<>();

    public IpRateLimiter(@Value("${app.rate-limit.forms.per-hour:10}") int perHour) {
        this.perHour = perHour;
    }

    /** Consomme une tentative pour l'IP de la requête ; lève 429 si la limite est atteinte. */
    public void check(HttpServletRequest request, String scope) {
        String ip = clientIp(request);
        if (!tryAcquire(scope, ip)) {
            log.warn("Rate limit dépassé : scope={}, ip={}", scope, ip);
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Trop de tentatives. Veuillez réessayer dans une heure.");
        }
    }

    /** @return false si l'IP a épuisé ses tentatives pour ce scope. */
    boolean tryAcquire(String scope, String ip) {
        if (buckets.size() > CLEANUP_THRESHOLD) evictStaleEntries();
        Entry entry = buckets.computeIfAbsent(scope + ":" + ip, key -> new Entry(newBucket()));
        entry.lastAccessMillis.set(System.currentTimeMillis());
        return entry.bucket.tryConsume(1);
    }

    static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            String[] hops = forwarded.split(",");
            return hops[hops.length - 1].trim();
        }
        return request.getRemoteAddr();
    }

    private Bucket newBucket() {
        return Bucket.builder()
                .addLimit(Bandwidth.classic(perHour, Refill.intervally(perHour, WINDOW)))
                .build();
    }

    private void evictStaleEntries() {
        long cutoff = System.currentTimeMillis() - 2 * WINDOW.toMillis();
        buckets.entrySet().removeIf(e -> e.getValue().lastAccessMillis.get() < cutoff);
    }

    private static final class Entry {
        private final Bucket bucket;
        private final AtomicLong lastAccessMillis = new AtomicLong(System.currentTimeMillis());

        private Entry(Bucket bucket) {
            this.bucket = bucket;
        }
    }
}
