package ravenworks.fizz.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ravenworks.fizz.domain.entity.TaskEntity;


public interface TaskRepository extends JpaRepository<TaskEntity, String> {

}
