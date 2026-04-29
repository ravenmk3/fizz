package ravenworks.fizz.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import ravenworks.fizz.domain.entity.JobNotificationEntity;
import ravenworks.fizz.engine.enums.NotificationStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface JobNotificationRepository extends JpaRepository<JobNotificationEntity, String> {

    @Query("SELECT n FROM JobNotificationEntity n WHERE n.status = 'PENDING' AND n.availableAt <= :now ORDER BY n.availableAt ASC LIMIT :limit")
    List<JobNotificationEntity> findPendingBefore(Instant now, int limit);

    @Query("SELECT MIN(n.availableAt) FROM JobNotificationEntity n WHERE n.status = 'PENDING'")
    Optional<Instant> findNearestAvailableAt();
}
