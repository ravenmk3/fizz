package ravenworks.fizz.web.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import ravenworks.fizz.common.model.ApiResponse;
import ravenworks.fizz.service.JobService;
import ravenworks.fizz.service.dto.CancelJobRequest;
import ravenworks.fizz.service.dto.CancelJobResponse;
import ravenworks.fizz.service.dto.CreateJobRequest;
import ravenworks.fizz.service.dto.CreateJobResponse;
import ravenworks.fizz.service.dto.GetJobRequest;
import ravenworks.fizz.service.dto.JobDetailResponse;
import ravenworks.fizz.service.dto.ListJobsRequest;
import ravenworks.fizz.service.dto.ListJobsResponse;

@Validated
@RestController
@RequiredArgsConstructor
public class JobController {

    private final JobService jobService;

    @PostMapping("/api/jobs/create")
    public ApiResponse<CreateJobResponse> create(@Valid @RequestBody CreateJobRequest request) {
        return ApiResponse.success(jobService.create(request));
    }

    @PostMapping("/api/jobs/get")
    public ApiResponse<JobDetailResponse> get(@Valid @RequestBody GetJobRequest request) {
        return ApiResponse.success(jobService.get(request.getId()));
    }

    @PostMapping("/api/jobs/list")
    public ApiResponse<ListJobsResponse> list(@RequestBody ListJobsRequest request) {
        return ApiResponse.success(jobService.list(request));
    }

    @PostMapping("/api/jobs/cancel")
    public ApiResponse<CancelJobResponse> cancel(@Valid @RequestBody CancelJobRequest request) {
        return ApiResponse.success(jobService.cancel(request.getId()));
    }

}
