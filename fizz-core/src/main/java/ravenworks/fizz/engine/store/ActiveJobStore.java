package ravenworks.fizz.engine.store;

import ravenworks.fizz.engine.enums.JobStatus;
import ravenworks.fizz.engine.model.ActiveJob;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ActiveJobStore {

    List<ActiveJob> findPendingJobs();

    List<ActiveJob> findRunningJobs();

    List<ActiveJob> findByStatusIn(List<JobStatus> statuses);

    long countByStatus(JobStatus status);

    long countByTenantIdAndStatus(String tenantId, JobStatus status);

    boolean existsByQueueingKeyAndStatus(String queueingKey, JobStatus status);

    void insert(ActiveJob activeJob);

    void updateStatus(String jobId, JobStatus status);

    void delete(String jobId);

    Optional<Instant> findNearestScheduledAt();
}
