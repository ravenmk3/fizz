package ravenworks.fizz.service.discovery;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import org.springframework.stereotype.Component;
import ravenworks.fizz.domain.entity.JobTypeEntity;
import ravenworks.fizz.domain.repository.JobTypeRepository;
import ravenworks.fizz.engine.discovery.JobTypeConfig;
import ravenworks.fizz.engine.discovery.JobTypeRegistry;
import ravenworks.fizz.engine.enums.BackoffStrategy;
import java.time.Duration;

@Component
public class DatabaseJobTypeRegistry implements JobTypeRegistry {

    private final LoadingCache<String, JobTypeConfig> cache;

    public DatabaseJobTypeRegistry(JobTypeRepository jobTypeRepo) {
        this.cache = Caffeine.newBuilder()
                .maximumSize(200)
                .expireAfterWrite(Duration.ofSeconds(30))
                .build(jobType -> {
                    JobTypeEntity entity = jobTypeRepo.findByJobType(jobType)
                            .orElseThrow(() -> new IllegalArgumentException("Job type not found: " + jobType));
                    return new JobTypeConfig(
                            entity.getServiceName(),
                            entity.getJobType(),
                            entity.getTaskPath(),
                            entity.getNotifyPath(),
                            entity.getHttpMethod(),
                            entity.getTimeoutMs(),
                            entity.getBackoffStrategy(),
                            entity.getBackoffInitialMs(),
                            entity.getBackoffMaxMs(),
                            entity.getJobConcurrency(),
                            entity.getTaskConcurrency()
                    );
                });
    }

    @Override
    public JobTypeConfig get(String jobType) {
        return cache.get(jobType);
    }

    public void invalidate(String jobType) {
        cache.invalidate(jobType);
    }
}
