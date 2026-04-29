package ravenworks.fizz.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import ravenworks.fizz.domain.entity.ActiveJobEntity;
import ravenworks.fizz.engine.enums.JobStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ActiveJobRepository extends JpaRepository<ActiveJobEntity, String> {

    List<ActiveJobEntity> findByStatusOrderByIdAsc(JobStatus status);

    List<ActiveJobEntity> findByStatusIn(Collection<JobStatus> statuses);

    long countByStatus(JobStatus status);

    long countByTenantIdAndStatus(String tenantId, JobStatus status);

    boolean existsByQueueingKeyAndStatus(String queueingKey, JobStatus status);

    @Modifying
    @Query("UPDATE ActiveJobEntity a SET a.status = :status, a.version = a.version + 1 WHERE a.id = :id")
    int updateStatus(String id, JobStatus status);

    @Query("SELECT MIN(a.scheduledAt) FROM ActiveJobEntity a WHERE a.status = 'PENDING' AND a.scheduledAt IS NOT NULL")
    Optional<Instant> findNearestScheduledAt();
}
