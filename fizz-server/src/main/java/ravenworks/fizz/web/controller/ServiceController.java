package ravenworks.fizz.web.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ravenworks.fizz.common.model.ApiResponse;
import ravenworks.fizz.domain.entity.ServiceEntity;
import ravenworks.fizz.domain.entity.ServiceInstanceEntity;
import ravenworks.fizz.service.service.ServiceManagementService;
import ravenworks.fizz.web.dto.SaveServiceRequest;
import ravenworks.fizz.web.dto.ServiceNameRequest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


@RestController
@RequestMapping("/api/services")
public class ServiceController {

    private final ServiceManagementService service;

    public ServiceController(ServiceManagementService service) {
        this.service = service;
    }

    @PostMapping("/save")
    public ApiResponse<Map<String, Object>> save(@RequestBody SaveServiceRequest request) {
        ServiceEntity entity = service.saveService(request.serviceName());
        return ApiResponse.success(Map.of("serviceName", entity.getServiceName()));
    }

    @PostMapping("/list")
    public ApiResponse<List<Map<String, Object>>> list(@RequestBody(required = false) Object ignored) {
        List<ServiceEntity> services = service.listServices();
        List<Map<String, Object>> result = new ArrayList<>();
        for (ServiceEntity svc : services) {
            List<ServiceInstanceEntity> instances = service.listServiceInstances(svc.getServiceName());
            var instanceList = instances.stream().map(i -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", i.getId());
                m.put("scheme", i.getScheme());
                m.put("host", i.getHost());
                m.put("port", i.getPort());
                return m;
            }).toList();
            result.add(Map.of("serviceName", svc.getServiceName(), "instances", instanceList));
        }
        return ApiResponse.success(result);
    }

    @PostMapping("/delete")
    public ApiResponse<Void> delete(@RequestBody ServiceNameRequest request) {
        service.deleteService(request.serviceName());
        return ApiResponse.success();
    }

}
