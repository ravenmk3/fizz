package ravenworks.fizz.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ravenworks.fizz.domain.entity.ServiceEntity;


public interface ServiceRepository extends JpaRepository<ServiceEntity, String> {

}
