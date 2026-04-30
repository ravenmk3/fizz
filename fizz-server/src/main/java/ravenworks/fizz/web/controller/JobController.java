package ravenworks.fizz.web.controller;

import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ravenworks.fizz.common.model.ApiResponse;
import ravenworks.fizz.domain.entity.JobEntity;
import ravenworks.fizz.service.service.JobService;
import ravenworks.fizz.web.dto.CreateJobRequest;
import ravenworks.fizz.web.dto.IdRequest;
import ravenworks.fizz.web.dto.ListJobsRequest;
import ravenworks.fizz.web.dto.PageResponse;

import java.util.LinkedHashMap;
import java.util.Map;


@RestController
@RequestMapping("/api/jobs")
public class JobController {

    private final JobService jobService;

    public JobController(JobService jobService) {
        this.jobService = jobService;
    }

    @PostMapping("/create")
    public ApiResponse<Map<String, Object>> create(@RequestBody CreateJobRequest request) {
        var tasks = request.tasks().stream()
                .map(CreateJobRequest.TaskParam::params)
                .toList();

        var result = jobService.createJob(
                request.tenantId(), request.serviceName(), request.jobType(),
                request.queueingKey(), request.bizKey(),
                request.taskConcurrency(), request.maxAttempts(),
                request.scheduledAt(), tasks
        );

        JobEntity job = result.job();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", job.getId());
        data.put("status", job.getStatus().name());
        data.put("totalCount", job.getTotalCount());
        data.put("createdAt", job.getCreatedAt());
        data.put("created", result.created());
        return ApiResponse.success(data);
    }

    @PostMapping("/get")
    public ApiResponse<Map<String, Object>> get(@RequestBody IdRequest request) {
        JobEntity job = jobService.getJob(request.id());
        return ApiResponse.success(toDetailMap(job));
    }

    @PostMapping("/list")
    public ApiResponse<PageResponse<Map<String, Object>>> list(@RequestBody ListJobsRequest request) {
        Page<JobEntity> page = jobService.listJobs(
                request.tenantId(), request.status(), request.serviceName(),
                request.jobType(), request.pageOrDefault(), request.sizeOrDefault()
        );

        var items = page.getContent().stream().map(this::toDetailMap).toList();
        var result = new PageResponse<>(items, page.getTotalElements(),
                request.pageOrDefault(), request.sizeOrDefault());
        return ApiResponse.success(result);
    }

    @PostMapping("/cancel")
    public ApiResponse<JobService.CancelResult> cancel(@RequestBody IdRequest request) {
        JobService.CancelResult result = jobService.cancelJob(request.id());
        return ApiResponse.success(result);
    }

    private Map<String, Object> toDetailMap(JobEntity job) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", job.getId());
        map.put("tenantId", job.getTenantId());
        map.put("serviceName", job.getServiceName());
        map.put("jobType", job.getJobType());
        map.put("queueingKey", job.getQueueingKey());
        map.put("bizKey", job.getBizKey());
        map.put("taskConcurrency", job.getTaskConcurrency());
        map.put("maxAttempts", job.getMaxAttempts());
        map.put("status", job.getStatus().name());
        map.put("scheduledAt", job.getScheduledAt());
        map.put("totalCount", job.getTotalCount());
        map.put("completedCount", job.getCompletedCount());
        map.put("failedCount", job.getFailedCount());
        int total = job.getTotalCount();
        int finished = job.getCompletedCount() + job.getFailedCount();
        map.put("progress", total > 0 ? finished * 100 / total : 0);
        map.put("createdAt", job.getCreatedAt());
        map.put("updatedAt", job.getUpdatedAt());
        return map;
    }

}
