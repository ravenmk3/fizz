package ravenworks.fizz.web.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ravenworks.fizz.domain.entity.JobTypeEntity;
import ravenworks.fizz.service.service.ServiceManagementService;
import ravenworks.fizz.common.model.ApiResponse;
import ravenworks.fizz.web.dto.SaveJobTypeRequest;
import ravenworks.fizz.web.dto.DeleteJobTypeRequest;
import ravenworks.fizz.web.dto.ServiceNameRequest;
import ravenworks.fizz.web.dto.UpdateJobTypeRequest;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/job-types")
public class JobTypeController {

    private final ServiceManagementService service;

    public JobTypeController(ServiceManagementService service) {
        this.service = service;
    }

    @PostMapping("/save")
    public ApiResponse<Map<String, Object>> save(@RequestBody SaveJobTypeRequest request) {
        JobTypeEntity entity = service.saveJobType(
                request.serviceName(), request.jobType(), request.taskPath(),
                request.notifyPath(), request.httpMethod(), request.timeoutMs(),
                request.backoffStrategy(), request.backoffInitialMs(), request.backoffMaxMs(),
                request.jobConcurrency(), request.taskConcurrency()
        );
        return ApiResponse.success(toMap(entity));
    }

    @PostMapping("/update")
    public ApiResponse<Map<String, Object>> update(@RequestBody UpdateJobTypeRequest request) {
        JobTypeEntity entity = service.updateJobType(
                request.jobType(), request.taskPath(), request.notifyPath(),
                request.httpMethod(), request.timeoutMs(), request.backoffStrategy(),
                request.backoffInitialMs(), request.backoffMaxMs(),
                request.jobConcurrency(), request.taskConcurrency()
        );
        return ApiResponse.success(toMap(entity));
    }

    @PostMapping("/list")
    public ApiResponse<?> list(@RequestBody(required = false) ServiceNameRequest request) {
        String serviceName = request != null ? request.serviceName() : null;
        var list = service.listJobTypes(serviceName).stream().map(this::toMap).toList();
        return ApiResponse.success(list);
    }

    @PostMapping("/delete")
    public ApiResponse<Void> delete(@RequestBody DeleteJobTypeRequest request) {
        service.deleteJobType(request.jobType());
        return ApiResponse.success();
    }

    private Map<String, Object> toMap(JobTypeEntity e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("jobType", e.getJobType());
        m.put("serviceName", e.getServiceName());
        m.put("taskPath", e.getTaskPath());
        m.put("notifyPath", e.getNotifyPath());
        m.put("httpMethod", e.getHttpMethod());
        m.put("timeoutMs", e.getTimeoutMs());
        m.put("backoffStrategy", e.getBackoffStrategy().name());
        m.put("backoffInitialMs", e.getBackoffInitialMs());
        m.put("backoffMaxMs", e.getBackoffMaxMs());
        m.put("jobConcurrency", e.getJobConcurrency());
        m.put("taskConcurrency", e.getTaskConcurrency());
        return m;
    }
}
