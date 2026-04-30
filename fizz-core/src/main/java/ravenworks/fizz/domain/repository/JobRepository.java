package ravenworks.fizz.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import ravenworks.fizz.domain.entity.JobEntity;
import ravenworks.fizz.engine.enums.JobStatus;

import java.util.List;
import java.util.Optional;


public interface JobRepository extends JpaRepository<JobEntity, String>, JpaSpecificationExecutor<JobEntity> {

    List<JobEntity> findByStatus(JobStatus status);

    List<JobEntity> findByTenantIdAndStatus(String tenantId, JobStatus status);

    Optional<JobEntity> findByJobTypeAndBizKey(String jobType, String bizKey);

    @Modifying
    @Query("UPDATE JobEntity j SET j.status = :status, j.version = j.version + 1 WHERE j.id = :id")
    int updateStatus(String id, JobStatus status);

    @Modifying
    @Query("UPDATE JobEntity j SET j.status = :status, j.instanceId = :instanceId, j.version = j.version + 1 WHERE j.id = :id")
    int updateStatusAndInstanceId(String id, JobStatus status, String instanceId);

    @Modifying
    @Query(value = "UPDATE fizz_job SET completed_count = completed_count + 1, version = version + 1 WHERE id = :id", nativeQuery = true)
    int incrementCompletedCount(String id);

    @Modifying
    @Query(value = "UPDATE fizz_job SET failed_count = failed_count + 1, version = version + 1 WHERE id = :id", nativeQuery = true)
    int incrementFailedCount(String id);

    @Modifying
    @Query(value = "UPDATE fizz_job SET status = :status, completed_count = :completedCount, failed_count = :failedCount, version = version + 1 WHERE id = :id", nativeQuery = true)
    int updateStatusAndCounts(String id, String status, int completedCount, int failedCount);

}
