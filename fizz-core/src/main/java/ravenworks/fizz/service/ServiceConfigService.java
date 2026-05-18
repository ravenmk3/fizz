package ravenworks.fizz.service;

import ravenworks.fizz.service.dto.ServiceListItemResponse;

import java.util.List;


public interface ServiceConfigService {

    void save(String serviceName);

    List<ServiceListItemResponse> list();

    void delete(String serviceName);

}
