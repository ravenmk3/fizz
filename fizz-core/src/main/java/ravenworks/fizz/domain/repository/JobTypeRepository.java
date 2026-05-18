package ravenworks.fizz.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ravenworks.fizz.domain.entity.JobTypeEntity;


public interface JobTypeRepository extends JpaRepository<JobTypeEntity, String> {

}
