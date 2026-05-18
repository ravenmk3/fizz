package ravenworks.fizz.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ravenworks.fizz.common.exception.BusinessException;
import ravenworks.fizz.common.util.Uuids;
import ravenworks.fizz.domain.entity.ServiceInstanceEntity;
import ravenworks.fizz.domain.repository.ServiceInstanceRepository;
import ravenworks.fizz.domain.repository.ServiceRepository;
import ravenworks.fizz.service.ServiceInstanceService;
import ravenworks.fizz.service.dto.SaveServiceInstanceRequest;

import java.util.List;


@Slf4j
@Service
@RequiredArgsConstructor
public class ServiceInstanceServiceImpl implements ServiceInstanceService {

    private final ServiceRepository serviceRepository;
    private final ServiceInstanceRepository serviceInstanceRepository;

    @Override
    @Transactional
    public ServiceInstanceEntity save(SaveServiceInstanceRequest request) {
        serviceRepository.findByServiceName(request.getServiceName())
                .orElseThrow(() -> new BusinessException(404, "Service not found: " + request.getServiceName()));

        ServiceInstanceEntity entity = new ServiceInstanceEntity();
        entity.setId(Uuids.uuid7Hex());
        entity.setServiceName(request.getServiceName());
        entity.setScheme(request.getScheme() != null ? request.getScheme() : "http");
        entity.setHost(request.getHost());
        entity.setPort(request.getPort());
        serviceInstanceRepository.save(entity);
        log.info("Service instance created: id={}, serviceName={}", entity.getId(), request.getServiceName());
        return entity;
    }

    @Override
    public List<ServiceInstanceEntity> list(String serviceName) {
        if (serviceName != null && !serviceName.isEmpty()) {
            return serviceInstanceRepository.findAllByServiceName(serviceName);
        }
        return serviceInstanceRepository.findAll();
    }

    @Override
    @Transactional
    public void delete(String id) {
        ServiceInstanceEntity entity = serviceInstanceRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "Service instance not found: " + id));
        serviceInstanceRepository.delete(entity);
        log.info("Service instance deleted: id={}", id);
    }

}
