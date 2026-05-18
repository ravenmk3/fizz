package ravenworks.fizz.web.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import ravenworks.fizz.common.model.ApiResponse;
import ravenworks.fizz.domain.entity.ServiceInstanceEntity;
import ravenworks.fizz.service.ServiceInstanceService;
import ravenworks.fizz.service.dto.DeleteServiceInstanceRequest;
import ravenworks.fizz.service.dto.ListServiceInstancesRequest;
import ravenworks.fizz.service.dto.SaveServiceInstanceRequest;

import java.util.List;

@Validated
@RestController
@RequiredArgsConstructor
public class ServiceInstanceController {

    private final ServiceInstanceService serviceInstanceService;

    @PostMapping("/api/service-instances/save")
    public ApiResponse<ServiceInstanceEntity> save(@Valid @RequestBody SaveServiceInstanceRequest request) {
        return ApiResponse.success(serviceInstanceService.save(request));
    }

    @PostMapping("/api/service-instances/list")
    public ApiResponse<List<ServiceInstanceEntity>> list(@RequestBody ListServiceInstancesRequest request) {
        String serviceName = request.getServiceName();
        return ApiResponse.success(serviceInstanceService.list(serviceName));
    }

    @PostMapping("/api/service-instances/delete")
    public ApiResponse<Void> delete(@Valid @RequestBody DeleteServiceInstanceRequest request) {
        serviceInstanceService.delete(request.getId());
        return ApiResponse.success();
    }

}
