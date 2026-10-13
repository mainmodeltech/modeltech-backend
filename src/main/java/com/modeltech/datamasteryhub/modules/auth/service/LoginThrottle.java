package com.modeltech.datamasteryhub.modules.auth.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Freine les essais de mot de passe par couple (adresse e-mail, adresse IP) : 5 échecs en 15 minutes bloquent ce
 * couple pendant 15 minutes. Contrairement à un verrouillage du compte, un tiers qui s'acharne sur l'adresse d'un
 * administrateur ne l'empêche pas de se connecter depuis sa propre adresse IP. Fonctionne que le compte existe ou non
 * (aucun indice sur l'existence d'un compte). État en mémoire, par instance.
 */
@Component
public class LoginThrottle {

    private static final int CLEANUP_THRESHOLD = 10_000;

    private final Map<String, Entry> entries = new ConcurrentHashMap<>();

    @Value("${app.auth.throttle.max-attempts:5}")
    private int maxAttempts;

    @Value("${app.auth.throttle.minutes:15}")
    private int minutes;

    private static final class Entry {
        int failures;
        long windowStartMillis;
        long blockedUntilMillis;
    }

    /** Minutes restantes si ce couple est bloqué, sinon 0. */
    public long blockedMinutes(String email, String ip) {
        Entry entry = entries.get(key(email, ip));
        if (entry == null) return 0;
        synchronized (entry) {
            long left = entry.blockedUntilMillis - System.currentTimeMillis();
            return left > 0 ? Math.max(1, Duration.ofMillis(left).toMinutes() + 1) : 0;
        }
    }

    public void recordFailure(String email, String ip) {
        if (entries.size() > CLEANUP_THRESHOLD) evict();
        Entry entry = entries.computeIfAbsent(key(email, ip), k -> new Entry());
        long now = System.currentTimeMillis();
        synchronized (entry) {
            if (now - entry.windowStartMillis > Duration.ofMinutes(minutes).toMillis()) {
                entry.failures = 0;
                entry.windowStartMillis = now;
            }
            entry.failures++;
            if (entry.failures >= maxAttempts) {
                entry.blockedUntilMillis = now + Duration.ofMinutes(minutes).toMillis();
                entry.failures = 0;
            }
        }
    }

    public void reset(String email, String ip) {
        entries.remove(key(email, ip));
    }

    private void evict() {
        long now = System.currentTimeMillis();
        long window = Duration.ofMinutes(minutes).toMillis();
        entries.entrySet().removeIf(e -> e.getValue().blockedUntilMillis < now && now - e.getValue().windowStartMillis > window);
    }

    private static String key(String email, String ip) {
        return (email == null ? "" : email.toLowerCase(Locale.ROOT).trim()) + "|" + ip;
    }
}
