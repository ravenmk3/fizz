package ravenworks.fizz.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ravenworks.fizz.domain.entity.ServiceInstanceEntity;
import java.util.List;
import java.util.Optional;

public interface ServiceInstanceRepository extends JpaRepository<ServiceInstanceEntity, String> {

    List<ServiceInstanceEntity> findByServiceName(String serviceName);

    Optional<ServiceInstanceEntity> findByServiceNameAndHostAndPort(String serviceName, String host, int port);

    boolean existsByServiceName(String serviceName);
}
