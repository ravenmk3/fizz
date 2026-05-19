package ravenworks.fizz.engine.discovery;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ravenworks.fizz.domain.repository.ServiceInstanceRepository;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;


/**
 * @author Raven
 */
@Slf4j
@Component
public class DbServiceDiscovery implements ServiceDiscovery {

    private final ServiceInstanceRepository repo;
    private final Map<String, AtomicInteger> counters = new ConcurrentHashMap<>();
    private final LoadingCache<String, List<ServiceInstance>> cache;

    public DbServiceDiscovery(@NonNull ServiceInstanceRepository repo) {
        this.repo = repo;
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(60, TimeUnit.SECONDS)
                .build(this::loadInstances);
    }

    @Override
    public ServiceInstance resolve(@NonNull String serviceName) {
        List<ServiceInstance> instances = this.cache.get(serviceName);
        if (instances.isEmpty()) {
            return null;
        }
        AtomicInteger counter = this.counters.computeIfAbsent(serviceName, k -> new AtomicInteger());
        int idx = counter.getAndUpdate(i -> (i + 1) % instances.size());
        return instances.get(idx);
    }

    private List<ServiceInstance> loadInstances(String serviceName) {
        return this.repo.findAllByServiceName(serviceName)
                .stream()
                .map(e -> new ServiceInstance(e.getScheme(), e.getHost(), e.getPort()))
                .toList();
    }

}
