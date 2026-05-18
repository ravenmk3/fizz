package ravenworks.fizz.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ravenworks.fizz.domain.entity.ServiceInstanceEntity;

import java.util.List;


public interface ServiceInstanceRepository extends JpaRepository<ServiceInstanceEntity, String> {

    List<ServiceInstanceEntity> findAllByServiceName(String serviceName);

}
