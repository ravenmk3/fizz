package ravenworks.fizz.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ravenworks.fizz.domain.entity.JobNotificationEntity;


public interface JobNotificationRepository extends JpaRepository<JobNotificationEntity, String> {

}
