package ravenworks.fizz.service.store;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ravenworks.fizz.domain.entity.ActiveJobEntity;
import ravenworks.fizz.domain.repository.ActiveJobRepository;
import ravenworks.fizz.engine.enums.JobStatus;
import ravenworks.fizz.engine.model.ActiveJob;
import ravenworks.fizz.engine.store.ActiveJobStore;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Component
public class ActiveJobStoreImpl implements ActiveJobStore {

    private final ActiveJobRepository activeJobRepository;

    public ActiveJobStoreImpl(ActiveJobRepository activeJobRepository) {
        this.activeJobRepository = activeJobRepository;
    }

    @Override
    public List<ActiveJob> findPendingJobs() {
        return activeJobRepository.findByStatusOrderByIdAsc(JobStatus.PENDING)
                .stream().map(this::toModel).toList();
    }

    @Override
    public List<ActiveJob> findRunningJobs() {
        return activeJobRepository.findByStatusOrderByIdAsc(JobStatus.RUNNING)
                .stream().map(this::toModel).toList();
    }

    @Override
    public List<ActiveJob> findByStatusIn(List<JobStatus> statuses) {
        return activeJobRepository.findByStatusIn(statuses)
                .stream().map(this::toModel).toList();
    }

    @Override
    public long countByStatus(JobStatus status) {
        return activeJobRepository.countByStatus(status);
    }

    @Override
    public long countByTenantIdAndStatus(String tenantId, JobStatus status) {
        return activeJobRepository.countByTenantIdAndStatus(tenantId, status);
    }

    @Override
    public boolean existsByQueueingKeyAndStatus(String queueingKey, JobStatus status) {
        return activeJobRepository.existsByQueueingKeyAndStatus(queueingKey, status);
    }

    @Override
    @Transactional(rollbackFor = Throwable.class)
    public void insert(ActiveJob activeJob) {
        ActiveJobEntity entity = new ActiveJobEntity();
        entity.setId(activeJob.getId());
        entity.setTenantId(activeJob.getTenantId());
        entity.setQueueingKey(activeJob.getQueueingKey());
        entity.setStatus(activeJob.getStatus());
        entity.setScheduledAt(activeJob.getScheduledAt());
        activeJobRepository.save(entity);
    }

    @Override
    @Transactional(rollbackFor = Throwable.class)
    public void updateStatus(String jobId, JobStatus status) {
        activeJobRepository.updateStatus(jobId, status);
    }

    @Override
    @Transactional(rollbackFor = Throwable.class)
    public void delete(String jobId) {
        activeJobRepository.deleteById(jobId);
    }

    @Override
    public Optional<Instant> findNearestScheduledAt() {
        return activeJobRepository.findNearestScheduledAt();
    }

    private ActiveJob toModel(ActiveJobEntity e) {
        ActiveJob aj = new ActiveJob();
        aj.setId(e.getId());
        aj.setTenantId(e.getTenantId());
        aj.setQueueingKey(e.getQueueingKey());
        aj.setStatus(e.getStatus());
        aj.setScheduledAt(e.getScheduledAt());
        aj.setVersion(e.getVersion());
        return aj;
    }
}
