package ravenworks.fizz.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import ravenworks.fizz.domain.entity.JobEntity;


public interface JobRepository extends JpaRepository<JobEntity, String>, JpaSpecificationExecutor<JobEntity> {

}
