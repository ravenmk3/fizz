package ravenworks.fizz.engine.discovery;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;


/**
 * @author Raven
 */
@Slf4j
@Component
public class ServiceLoadBalancer implements ServiceHealthIndicator {

    private static final int FAILURE_THRESHOLD = 10;
    private static final Duration RECOVERY_DURATION = Duration.ofMinutes(2);

    private final ServiceDiscovery discovery;
    private final Map<ServiceInstance, AtomicInteger> failureCounts = new ConcurrentHashMap<>();
    private final Map<ServiceInstance, LocalDateTime> unavailableUntil = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> roundRobin = new ConcurrentHashMap<>();

    public ServiceLoadBalancer(@NonNull ServiceDiscovery discovery) {
        this.discovery = discovery;
    }

    public ServiceInstance resolve(@NonNull String serviceName) {
        List<ServiceInstance> instances = this.discovery.getInstances(serviceName);
        List<ServiceInstance> available = instances.stream()
                .filter(this::isInstanceAvailable)
                .toList();
        if (available.isEmpty()) {
            return null;
        }
        AtomicInteger counter = this.roundRobin.computeIfAbsent(serviceName,
                k -> new AtomicInteger());
        int idx = counter.getAndUpdate(i -> (i + 1) % available.size());
        return available.get(idx);
    }

    public void recordSuccess(@NonNull ServiceInstance instance) {
        this.failureCounts.remove(instance);
        if (this.unavailableUntil.remove(instance) != null) {
            log.info("Instance {} recovered", instance.getUri());
        }
    }

    public void recordFailure(@NonNull ServiceInstance instance) {
        int count = this.failureCounts.computeIfAbsent(instance, k -> new AtomicInteger())
                .incrementAndGet();
        if (count >= FAILURE_THRESHOLD) {
            this.unavailableUntil.computeIfAbsent(instance, k -> {
                log.warn("Instance {} marked unavailable after {} failures", instance.getUri(), count);
                return LocalDateTime.now();
            });
        }
    }

    @Override
    public boolean isAvailable(@NonNull String serviceName) {
        return this.discovery.getInstances(serviceName).stream()
                .anyMatch(this::isInstanceAvailable);
    }

    private boolean isInstanceAvailable(@NonNull ServiceInstance instance) {
        LocalDateTime until = this.unavailableUntil.get(instance);
        if (until == null) {
            return true;
        }
        if (Duration.between(until, LocalDateTime.now()).compareTo(RECOVERY_DURATION) >= 0) {
            this.unavailableUntil.remove(instance);
            this.failureCounts.remove(instance);
            log.info("Instance {} auto-recovered after {}", instance.getUri(), RECOVERY_DURATION);
            return true;
        }
        return false;
    }

}