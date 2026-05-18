package ravenworks.fizz.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ravenworks.fizz.domain.entity.ServiceInstanceEntity;


public interface ServiceInstanceRepository extends JpaRepository<ServiceInstanceEntity, String> {

}
