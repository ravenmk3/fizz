package ravenworks.fizz.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ravenworks.fizz.common.exception.BusinessException;
import ravenworks.fizz.common.util.Uuids;
import ravenworks.fizz.domain.entity.ServiceEntity;
import ravenworks.fizz.domain.entity.ServiceInstanceEntity;
import ravenworks.fizz.domain.repository.JobTypeRepository;
import ravenworks.fizz.domain.repository.ServiceInstanceRepository;
import ravenworks.fizz.domain.repository.ServiceRepository;
import ravenworks.fizz.service.ServiceConfigService;
import ravenworks.fizz.service.dto.ServiceListItemResponse;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ServiceConfigServiceImpl implements ServiceConfigService {

    private final ServiceRepository serviceRepository;
    private final ServiceInstanceRepository serviceInstanceRepository;
    private final JobTypeRepository jobTypeRepository;

    @Override
    @Transactional
    public void save(String serviceName) {
        if (serviceRepository.findByServiceName(serviceName).isPresent()) {
            log.info("Service already exists: {}", serviceName);
            return;
        }
        ServiceEntity entity = new ServiceEntity();
        entity.setId(Uuids.uuid7Hex());
        entity.setServiceName(serviceName);
        serviceRepository.save(entity);
        log.info("Service created: {}", serviceName);
    }

    @Override
    public List<ServiceListItemResponse> list() {
        List<ServiceEntity> services = serviceRepository.findAll();
        return services.stream()
                .map(svc -> {
                    List<ServiceInstanceEntity> instances = serviceInstanceRepository.findAllByServiceName(svc.getServiceName());
                    List<ServiceListItemResponse.ServiceInstanceDto> instanceDtos = instances.stream()
                            .map(inst -> new ServiceListItemResponse.ServiceInstanceDto(
                                    inst.getId(), inst.getScheme(), inst.getHost(), inst.getPort()))
                            .toList();
                    return new ServiceListItemResponse(svc.getServiceName(), instanceDtos);
                })
                .toList();
    }

    @Override
    @Transactional
    public void delete(String serviceName) {
        ServiceEntity service = serviceRepository.findByServiceName(serviceName)
                .orElseThrow(() -> new BusinessException(404, "Service not found: " + serviceName));

        boolean hasJobTypes = !jobTypeRepository.findAllByServiceName(serviceName).isEmpty();
        if (hasJobTypes) {
            throw new BusinessException(409, "Cannot delete service '" + serviceName + "': referenced by job types");
        }

        boolean hasInstances = !serviceInstanceRepository.findAllByServiceName(serviceName).isEmpty();
        if (hasInstances) {
            throw new BusinessException(409, "Cannot delete service '" + serviceName + "': referenced by service instances");
        }

        serviceRepository.delete(service);
        log.info("Service deleted: {}", serviceName);
    }

}
