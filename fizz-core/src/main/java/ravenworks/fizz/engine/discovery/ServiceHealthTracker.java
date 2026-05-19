package ravenworks.fizz.engine.discovery;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


/**
 * @author Raven
 */
@Slf4j
@Component
public class ServiceHealthTracker {

    private static final int FAILURE_THRESHOLD = 5;
    private static final Duration RECOVERY_DURATION = Duration.ofSeconds(60);

    private final Map<String, Integer> consecutiveFailures = new ConcurrentHashMap<>();
    private final Map<String, LocalDateTime> unavailableServices = new ConcurrentHashMap<>();

    public void recordSuccess(@NonNull String serviceName) {
        consecutiveFailures.remove(serviceName);
        if (unavailableServices.remove(serviceName) != null) {
            log.info("Service {} recovered and is now available", serviceName);
        }
    }

    public void recordFailure(@NonNull String serviceName) {
        int failures = consecutiveFailures.merge(serviceName, 1, Integer::sum);
        if (failures >= FAILURE_THRESHOLD) {
            unavailableServices.computeIfAbsent(serviceName, k -> {
                log.warn("Service {} marked unavailable after {} consecutive failures",
                        serviceName, failures);
                return LocalDateTime.now();
            });
        }
    }

    public boolean isAvailable(@NonNull String serviceName) {
        LocalDateTime unavailableSince = unavailableServices.get(serviceName);
        if (unavailableSince == null) {
            return true;
        }
        if (Duration.between(unavailableSince, LocalDateTime.now()).compareTo(RECOVERY_DURATION) >= 0) {
            unavailableServices.remove(serviceName);
            consecutiveFailures.remove(serviceName);
            log.info("Service {} auto-recovered after {}", serviceName, RECOVERY_DURATION);
            return true;
        }
        return false;
    }

}
