package ravenworks.fizz.service;

import ravenworks.fizz.domain.entity.ServiceInstanceEntity;
import ravenworks.fizz.service.dto.SaveServiceInstanceRequest;

import java.util.List;

public interface ServiceInstanceService {

    ServiceInstanceEntity save(SaveServiceInstanceRequest request);

    List<ServiceInstanceEntity> list(String serviceName);

    void delete(String id);

}
