package ravenworks.fizz.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ravenworks.fizz.domain.entity.ServiceEntity;

import java.util.Optional;


public interface ServiceRepository extends JpaRepository<ServiceEntity, String> {

    Optional<ServiceEntity> findByServiceName(String serviceName);

    boolean existsByServiceName(String serviceName);

}
