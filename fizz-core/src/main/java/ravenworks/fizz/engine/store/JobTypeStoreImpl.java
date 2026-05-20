package ravenworks.fizz.engine.store;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import lombok.NonNull;
import org.springframework.stereotype.Component;
import ravenworks.fizz.domain.entity.JobTypeEntity;
import ravenworks.fizz.domain.repository.JobTypeRepository;

import java.util.concurrent.TimeUnit;


/**
 * @author Raven
 */
@Component
public class JobTypeStoreImpl implements JobTypeStore {

    private final LoadingCache<String, JobTypeEntity> cache;

    public JobTypeStoreImpl(@NonNull JobTypeRepository jobTypeRepository) {
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(1, TimeUnit.MINUTES)
                .build(jobType -> jobTypeRepository.findByJobType(jobType).orElse(null));
    }

    @Override
    public JobTypeEntity get(@NonNull String jobType) {
        return this.cache.get(jobType);
    }

}