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
    private static final int PROBATION_SUCCESSES = 10;
    private static final Duration RECOVERY_DURATION = Duration.ofSeconds(30);

    private final ServiceDiscovery discovery;
    private final Map<ServiceInstance, AtomicInteger> failureCounts = new ConcurrentHashMap<>();
    private final Map<ServiceInstance, InstanceState> states = new ConcurrentHashMap<>();
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
        InstanceState state = this.states.get(instance);
        if (state == null) {
            this.failureCounts.remove(instance);
            return;
        }
        if (state.probation) {
            state.probationSuccesses++;
            if (state.probationSuccesses >= PROBATION_SUCCESSES) {
                this.states.remove(instance);
                this.failureCounts.remove(instance);
                log.info("Instance {} fully recovered", instance.getUri());
            }
        }
    }

    public void recordFailure(@NonNull ServiceInstance instance) {
        InstanceState state = this.states.get(instance);
        if (state != null && state.probation) {
            state.probation = false;
            state.unavailableSince = LocalDateTime.now();
            state.probationSuccesses = 0;
            log.warn("Instance {} returned to unavailable during probation", instance.getUri());
            return;
        }
        int count = this.failureCounts.computeIfAbsent(instance, k -> new AtomicInteger())
                .incrementAndGet();
        if (count >= FAILURE_THRESHOLD) {
            this.states.computeIfAbsent(instance, k -> {
                log.warn("Instance {} marked unavailable after {} failures", instance.getUri(), count);
                return new InstanceState(LocalDateTime.now(), false, 0);
            });
        }
    }

    @Override
    public boolean isAvailable(@NonNull String serviceName) {
        return this.discovery.getInstances(serviceName).stream()
                .anyMatch(this::isInstanceAvailable);
    }

    private boolean isInstanceAvailable(@NonNull ServiceInstance instance) {
        InstanceState state = this.states.get(instance);
        if (state == null) {
            return true;
        }
        if (!state.probation) {
            Duration elapsed = Duration.between(state.unavailableSince, LocalDateTime.now());
            if (elapsed.compareTo(RECOVERY_DURATION) < 0) {
                return false;
            }
            state.probation = true;
            state.probationSuccesses = 0;
            log.info("Instance {} entered probation", instance.getUri());
        }
        return true;
    }

    private static class InstanceState {

        LocalDateTime unavailableSince;
        boolean probation;
        int probationSuccesses;

        InstanceState(LocalDateTime unavailableSince, boolean probation, int probationSuccesses) {
            this.unavailableSince = unavailableSince;
            this.probation = probation;
            this.probationSuccesses = probationSuccesses;
        }

    }

}
