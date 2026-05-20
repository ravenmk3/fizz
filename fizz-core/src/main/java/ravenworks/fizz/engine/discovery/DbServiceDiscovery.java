package ravenworks.fizz.engine.discovery;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ravenworks.fizz.domain.repository.ServiceInstanceRepository;

import java.util.List;
import java.util.concurrent.TimeUnit;


/**
 * @author Raven
 */
@Slf4j
@Component
public class DbServiceDiscovery implements ServiceDiscovery {

    private final ServiceInstanceRepository repo;
    private final LoadingCache<String, List<ServiceInstance>> cache;

    public DbServiceDiscovery(@NonNull ServiceInstanceRepository repo) {
        this.repo = repo;
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(60, TimeUnit.SECONDS)
                .build(this::loadInstances);
    }

    @Override
    public List<ServiceInstance> getInstances(@NonNull String serviceName) {
        List<ServiceInstance> instances = this.cache.get(serviceName);
        return instances != null ? instances : List.of();
    }

    private List<ServiceInstance> loadInstances(String serviceName) {
        return this.repo.findAllByServiceName(serviceName)
                .stream()
                .map(e -> new ServiceInstance(e.getScheme(), e.getHost(), e.getPort()))
                .toList();
    }

}