package ravenworks.fizz.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import ravenworks.fizz.domain.entity.TaskEntity;
import ravenworks.fizz.engine.enums.TaskStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface TaskRepository extends JpaRepository<TaskEntity, String> {

    @Query("SELECT t FROM TaskEntity t WHERE t.jobId = :jobId AND t.status = 'PENDING' AND t.availableAt <= :now ORDER BY t.id ASC LIMIT :limit")
    List<TaskEntity> findDispatchable(String jobId, int limit, Instant now);

    int countByJobIdAndStatus(String jobId, TaskStatus status);

    @Modifying
    @Query("UPDATE TaskEntity t SET t.status = 'CANCELLED', t.version = t.version + 1 WHERE t.jobId = :jobId AND t.status = 'PENDING'")
    int cancelPendingByJobId(String jobId);

    @Modifying
    @Query(value = "UPDATE fizz_task SET status = 'PENDING', instance_id = NULL, version = version + 1 WHERE status = 'RUNNING' AND (instance_id != :instanceId OR instance_id IS NULL)", nativeQuery = true)
    int recoverDanglingTasks(String instanceId);

    @Query("SELECT MIN(t.availableAt) FROM TaskEntity t WHERE t.status = 'PENDING'")
    Optional<Instant> findNearestAvailableAt();
}
