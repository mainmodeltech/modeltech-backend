package com.modeltech.datamasteryhub.modules.payments;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Disjoncteur de la passerelle : après quelques échecs d'affilée à la création d'un paiement, le paiement en ligne est considéré
 * indisponible pendant quelques minutes et le paiement manuel est proposé à la place, sans intervention. Le premier succès referme le
 * disjoncteur. L'état est en mémoire : il se réarme tout seul à un redémarrage.
 */
@Component
@Slf4j
public class GatewayHealth {

    static final int FAILURES_BEFORE_OPENING = 3;
    static final Duration OPEN_FOR = Duration.ofMinutes(10);

    private final AtomicInteger consecutiveFailures = new AtomicInteger();
    private final AtomicReference<Instant> openUntil = new AtomicReference<>(Instant.EPOCH);

    public void recordSuccess() {
        consecutiveFailures.set(0);
        openUntil.set(Instant.EPOCH);
    }

    public void recordFailure() {
        if (consecutiveFailures.incrementAndGet() >= FAILURES_BEFORE_OPENING) {
            Instant until = Instant.now().plus(OPEN_FOR);
            if (openUntil.getAndSet(until).isBefore(Instant.now())) {
                log.error("Paiement en ligne indisponible : {} échecs de la passerelle d'affilée. Le paiement manuel est proposé pendant {} minutes.",
                        FAILURES_BEFORE_OPENING, OPEN_FOR.toMinutes());
            }
        }
    }

    public boolean isDegraded() {
        return Instant.now().isBefore(openUntil.get());
    }

    /** Remise à zéro manuelle (par un administrateur qui sait que la passerelle est rétablie). */
    public void reset() {
        recordSuccess();
    }
}
