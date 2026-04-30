package ravenworks.fizz.service.discovery;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import org.springframework.stereotype.Component;
import ravenworks.fizz.domain.repository.ServiceInstanceRepository;
import ravenworks.fizz.engine.discovery.ServiceDiscovery;
import ravenworks.fizz.engine.discovery.ServiceEndpoint;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;


@Component
public class DatabaseServiceDiscovery implements ServiceDiscovery {

    private final LoadingCache<String, List<ServiceEndpoint>> cache;
    private final ConcurrentHashMap<String, AtomicInteger> roundRobinCounters = new ConcurrentHashMap<>();

    public DatabaseServiceDiscovery(ServiceInstanceRepository instanceRepo) {
        this.cache = Caffeine.newBuilder()
                .maximumSize(100)
                .expireAfterWrite(Duration.ofSeconds(30))
                .build(serviceName -> instanceRepo.findByServiceName(serviceName).stream()
                        .map(e -> new ServiceEndpoint(e.getScheme(), e.getHost(), e.getPort()))
                        .toList());
    }

    @Override
    public ServiceEndpoint resolve(String serviceName) {
        List<ServiceEndpoint> endpoints = cache.get(serviceName);
        if (endpoints == null || endpoints.isEmpty()) {
            throw new IllegalArgumentException("No instances found for service: " + serviceName);
        }
        AtomicInteger counter = roundRobinCounters
                .computeIfAbsent(serviceName, k -> new AtomicInteger(0));
        int index = Math.floorMod(counter.getAndIncrement(), endpoints.size());
        return endpoints.get(index);
    }

    public void invalidate(String serviceName) {
        cache.invalidate(serviceName);
        roundRobinCounters.remove(serviceName);
    }

}
