package ravenworks.fizz.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ravenworks.fizz.domain.entity.ActiveJobEntity;
import ravenworks.fizz.domain.enums.JobStatus;

import java.time.Instant;
import java.util.List;


public interface ActiveJobRepository extends JpaRepository<ActiveJobEntity, String> {

    @Query("SELECT a FROM ActiveJobEntity a WHERE a.status = :status AND (a.scheduledAt IS NULL OR a.scheduledAt <= :now)")
    List<ActiveJobEntity> findPendingReady(@Param("status") JobStatus status, @Param("now") Instant now);

    List<ActiveJobEntity> findByStatusNot(JobStatus status);

}
