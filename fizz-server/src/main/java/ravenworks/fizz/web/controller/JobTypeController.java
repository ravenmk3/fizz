package ravenworks.fizz.web.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import ravenworks.fizz.common.model.ApiResponse;
import ravenworks.fizz.domain.entity.JobTypeEntity;
import ravenworks.fizz.service.JobTypeService;
import ravenworks.fizz.service.dto.DeleteJobTypeRequest;
import ravenworks.fizz.service.dto.ListJobTypesRequest;
import ravenworks.fizz.service.dto.SaveJobTypeRequest;
import ravenworks.fizz.service.dto.UpdateJobTypeRequest;

import java.util.List;

@Validated
@RestController
@RequiredArgsConstructor
public class JobTypeController {

    private final JobTypeService jobTypeService;

    @PostMapping("/api/job-types/save")
    public ApiResponse<JobTypeEntity> save(@Valid @RequestBody SaveJobTypeRequest request) {
        return ApiResponse.success(jobTypeService.save(request));
    }

    @PostMapping("/api/job-types/update")
    public ApiResponse<JobTypeEntity> update(@Valid @RequestBody UpdateJobTypeRequest request) {
        return ApiResponse.success(jobTypeService.update(request));
    }

    @PostMapping("/api/job-types/list")
    public ApiResponse<List<JobTypeEntity>> list(@RequestBody ListJobTypesRequest request) {
        return ApiResponse.success(jobTypeService.list(request.getServiceName()));
    }

    @PostMapping("/api/job-types/delete")
    public ApiResponse<Void> delete(@Valid @RequestBody DeleteJobTypeRequest request) {
        jobTypeService.delete(request.getJobType());
        return ApiResponse.success();
    }

}
