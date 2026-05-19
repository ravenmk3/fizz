package ravenworks.fizz.domain.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ravenworks.fizz.domain.entity.TaskEntity;
import ravenworks.fizz.domain.enums.TaskStatus;

import java.time.Instant;
import java.util.List;


public interface TaskRepository extends JpaRepository<TaskEntity, String> {

    long countByJobIdAndStatus(String jobId, TaskStatus status);

    @Modifying
    @Query("UPDATE TaskEntity t SET t.status = :newStatus, t.instanceId = NULL WHERE t.jobId = :jobId AND t.status IN :statuses")
    int resetRunningTasks(@Param("jobId") String jobId,
                          @Param("statuses") List<TaskStatus> statuses,
                          @Param("newStatus") TaskStatus newStatus);

    List<TaskEntity> findByJobIdAndStatusIn(String jobId, List<TaskStatus> statuses);

    @Query("SELECT t FROM TaskEntity t WHERE t.jobId = :jobId AND t.status = 'PENDING' AND t.availableAt <= :now ORDER BY t.availableAt ASC")
    List<TaskEntity> findPendingReady(@Param("jobId") String jobId,
                                      @Param("now") Instant now,
                                      Pageable pageable);

    @Modifying
    @Query("UPDATE TaskEntity t SET t.status = :newStatus, t.instanceId = NULL WHERE t.jobId = :jobId AND t.status IN :statuses")
    int cancelTasks(@Param("jobId") String jobId,
                    @Param("statuses") List<TaskStatus> statuses,
                    @Param("newStatus") TaskStatus newStatus);

}
