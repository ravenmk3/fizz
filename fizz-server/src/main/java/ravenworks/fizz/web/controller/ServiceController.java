package ravenworks.fizz.web.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import ravenworks.fizz.common.model.ApiResponse;
import ravenworks.fizz.service.ServiceConfigService;
import ravenworks.fizz.service.dto.DeleteServiceRequest;
import ravenworks.fizz.service.dto.SaveServiceRequest;
import ravenworks.fizz.service.dto.ServiceListItemResponse;

import java.util.List;


@Validated
@RestController
@RequiredArgsConstructor
public class ServiceController {

    private final ServiceConfigService serviceConfigService;

    @PostMapping("/api/services/save")
    public ApiResponse<Void> save(@Valid @RequestBody SaveServiceRequest request) {
        serviceConfigService.save(request.getServiceName());
        return ApiResponse.success();
    }

    @PostMapping("/api/services/list")
    public ApiResponse<List<ServiceListItemResponse>> list() {
        return ApiResponse.success(serviceConfigService.list());
    }

    @PostMapping("/api/services/delete")
    public ApiResponse<Void> delete(@Valid @RequestBody DeleteServiceRequest request) {
        serviceConfigService.delete(request.getServiceName());
        return ApiResponse.success();
    }

}
