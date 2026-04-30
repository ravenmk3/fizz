package ravenworks.fizz.web.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ravenworks.fizz.common.model.ApiResponse;
import ravenworks.fizz.domain.entity.ServiceInstanceEntity;
import ravenworks.fizz.service.service.ServiceManagementService;
import ravenworks.fizz.web.dto.IdRequest;
import ravenworks.fizz.web.dto.SaveServiceInstanceRequest;
import ravenworks.fizz.web.dto.ServiceNameRequest;

import java.util.LinkedHashMap;
import java.util.Map;


@RestController
@RequestMapping("/api/service-instances")
public class ServiceInstanceController {

    private final ServiceManagementService service;

    public ServiceInstanceController(ServiceManagementService service) {
        this.service = service;
    }

    @PostMapping("/save")
    public ApiResponse<Map<String, Object>> save(@RequestBody SaveServiceInstanceRequest request) {
        ServiceInstanceEntity entity = service.saveServiceInstance(
                request.serviceName(), request.scheme(), request.host(), request.port()
        );
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", entity.getId());
        data.put("serviceName", entity.getServiceName());
        data.put("scheme", entity.getScheme());
        data.put("host", entity.getHost());
        data.put("port", entity.getPort());
        return ApiResponse.success(data);
    }

    @PostMapping("/delete")
    public ApiResponse<Void> delete(@RequestBody IdRequest request) {
        service.deleteServiceInstance(request.id());
        return ApiResponse.success();
    }

    @PostMapping("/list")
    public ApiResponse<?> list(@RequestBody ServiceNameRequest request) {
        var instances = service.listServiceInstances(request.serviceName()).stream().map(i -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", i.getId());
            m.put("scheme", i.getScheme());
            m.put("host", i.getHost());
            m.put("port", i.getPort());
            return m;
        }).toList();
        return ApiResponse.success(instances);
    }

}
