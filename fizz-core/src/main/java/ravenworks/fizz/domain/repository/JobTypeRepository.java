package ravenworks.fizz.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ravenworks.fizz.domain.entity.JobTypeEntity;

import java.util.List;
import java.util.Optional;


public interface JobTypeRepository extends JpaRepository<JobTypeEntity, String> {

    Optional<JobTypeEntity> findByJobType(String jobType);

    List<JobTypeEntity> findByServiceName(String serviceName);

    boolean existsByServiceName(String serviceName);

}
