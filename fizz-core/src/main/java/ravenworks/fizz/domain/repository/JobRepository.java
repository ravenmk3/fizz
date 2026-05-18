package ravenworks.fizz.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import ravenworks.fizz.domain.entity.JobEntity;
import ravenworks.fizz.domain.enums.JobStatus;

import java.util.Collection;
import java.util.Optional;


public interface JobRepository extends JpaRepository<JobEntity, String>, JpaSpecificationExecutor<JobEntity> {

    Optional<JobEntity> findByJobTypeAndBizKey(String jobType, String bizKey);

    long countByJobTypeAndStatusIn(String jobType, Collection<JobStatus> statuses);

}
