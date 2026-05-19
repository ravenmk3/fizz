package ravenworks.fizz.domain.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import ravenworks.fizz.domain.entity.JobNotificationEntity;

import java.time.LocalDateTime;
import java.util.List;


public interface JobNotificationRepository extends JpaRepository<JobNotificationEntity, String> {

    List<JobNotificationEntity> findByAvailableAtBefore(LocalDateTime now, Pageable pageable);

}
