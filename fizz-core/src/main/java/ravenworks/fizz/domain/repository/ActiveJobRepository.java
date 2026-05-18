package ravenworks.fizz.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ravenworks.fizz.domain.entity.ActiveJobEntity;


public interface ActiveJobRepository extends JpaRepository<ActiveJobEntity, String> {

}
