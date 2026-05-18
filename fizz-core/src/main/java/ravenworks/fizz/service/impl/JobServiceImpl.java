package ravenworks.fizz.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ravenworks.fizz.common.exception.BusinessException;
import ravenworks.fizz.common.json.JsonUtils;
import ravenworks.fizz.common.util.Uuids;
import ravenworks.fizz.domain.entity.ActiveJobEntity;
import ravenworks.fizz.domain.entity.JobEntity;
import ravenworks.fizz.domain.entity.JobTypeEntity;
import ravenworks.fizz.domain.entity.ServiceEntity;
import ravenworks.fizz.domain.entity.TaskEntity;
import ravenworks.fizz.domain.enums.JobStatus;
import ravenworks.fizz.domain.enums.TaskStatus;
import ravenworks.fizz.domain.repository.ActiveJobRepository;
import ravenworks.fizz.domain.repository.JobRepository;
import ravenworks.fizz.domain.repository.JobTypeRepository;
import ravenworks.fizz.domain.repository.ServiceRepository;
import ravenworks.fizz.domain.repository.TaskRepository;
import ravenworks.fizz.service.JobService;
import ravenworks.fizz.service.dto.CancelJobResponse;
import ravenworks.fizz.service.dto.CreateJobRequest;
import ravenworks.fizz.service.dto.CreateJobResponse;
import ravenworks.fizz.service.dto.JobDetailResponse;
import ravenworks.fizz.service.dto.ListJobsRequest;
import ravenworks.fizz.service.dto.ListJobsResponse;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class JobServiceImpl implements JobService {

    private static final int MAX_PAGE_SIZE = 100;

    private final ServiceRepository serviceRepository;
    private final JobTypeRepository jobTypeRepository;
    private final JobRepository jobRepository;
    private final TaskRepository taskRepository;
    private final ActiveJobRepository activeJobRepository;

    @Override
    @Transactional
    public CreateJobResponse create(CreateJobRequest request) {
        ServiceEntity service = serviceRepository.findByServiceName(request.getServiceName())
                .orElseThrow(() -> new BusinessException(404, "Service not found: " + request.getServiceName()));

        JobTypeEntity jobType = jobTypeRepository.findByJobType(request.getJobType())
                .orElseThrow(() -> new BusinessException(404, "Job type not found: " + request.getJobType()));

        if (!jobType.getServiceName().equals(request.getServiceName())) {
            throw new BusinessException(400,
                    "Job type '" + request.getJobType() + "' does not belong to service '" + request.getServiceName() + "'");
        }

        if (request.getBizKey() != null && !request.getBizKey().isEmpty()) {
            var existing = jobRepository.findByJobTypeAndBizKey(request.getJobType(), request.getBizKey());
            if (existing.isPresent()) {
                JobEntity job = existing.get();
                return new CreateJobResponse(job.getId(), job.getStatus().name(),
                        job.getTotalCount(), job.getCreatedAt(), false);
            }
        }

        String jobId = Uuids.uuid7Hex();
        Instant scheduledAt = request.getScheduledAt() != null ? Instant.parse(request.getScheduledAt()) : null;

        JobEntity job = new JobEntity();
        job.setId(jobId);
        job.setTenantId(request.getTenantId());
        job.setServiceName(request.getServiceName());
        job.setJobType(request.getJobType());
        job.setMutexKey(request.getMutexKey());
        job.setBizKey(request.getBizKey());
        job.setTaskConcurrency(request.getTaskConcurrency() != null ? request.getTaskConcurrency() : 1);
        job.setMaxAttempts(request.getMaxAttempts() != null ? request.getMaxAttempts() : -1);
        job.setStatus(JobStatus.PENDING);
        job.setScheduledAt(scheduledAt);
        job.setTotalCount(request.getTasks().size());
        job.setSucceededCount(0);
        job.setFailedCount(0);
        job.setCancelledCount(0);

        jobRepository.save(job);

        ActiveJobEntity activeJob = new ActiveJobEntity();
        activeJob.setId(jobId);
        activeJob.setTenantId(request.getTenantId());
        activeJob.setMutexKey(request.getMutexKey());
        activeJob.setStatus(JobStatus.PENDING);
        activeJob.setScheduledAt(scheduledAt);
        activeJobRepository.save(activeJob);

        List<TaskEntity> tasks = new ArrayList<>();
        Instant now = Instant.now();
        for (CreateJobRequest.TaskParam t : request.getTasks()) {
            TaskEntity task = new TaskEntity();
            task.setId(Uuids.uuid7Hex());
            task.setJobId(jobId);
            task.setParams(JsonUtils.encode(t.getParams()));
            task.setStatus(TaskStatus.PENDING);
            task.setAttempts(0);
            task.setAvailableAt(now);
            tasks.add(task);
        }
        taskRepository.saveAll(tasks);

        log.info("Job created: id={}, jobType={}, tasks={}", jobId, request.getJobType(), tasks.size());
        return new CreateJobResponse(jobId, JobStatus.PENDING.name(), request.getTasks().size(), job.getCreatedAt(), true);
    }

    @Override
    public JobDetailResponse get(String id) {
        JobEntity job = jobRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "Job not found: " + id));
        return toDetail(job);
    }

    @Override
    public ListJobsResponse list(ListJobsRequest request) {
        int page = request.getPage() != null ? request.getPage() : 1;
        int size = request.getSize() != null ? request.getSize() : 20;
        if (size > MAX_PAGE_SIZE) {
            size = MAX_PAGE_SIZE;
        }
        if (page < 1) {
            page = 1;
        }

        var spec = buildSpecification(request);
        var pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        var pageResult = jobRepository.findAll(spec, pageable);

        List<JobDetailResponse> items = pageResult.getContent().stream()
                .map(this::toDetail)
                .toList();

        return new ListJobsResponse(items, pageResult.getTotalElements(), page, size);
    }

    @Override
    public CancelJobResponse cancel(String id) {
        throw new BusinessException(501, "Cancel job is not implemented yet");
    }

    private Specification<JobEntity> buildSpecification(ListJobsRequest request) {
        return (root, query, cb) -> {
            var predicates = new ArrayList<jakarta.persistence.criteria.Predicate>();

            if (request.getTenantId() != null && !request.getTenantId().isEmpty()) {
                predicates.add(cb.equal(root.get("tenantId"), request.getTenantId()));
            }
            if (request.getStatus() != null && !request.getStatus().isEmpty()) {
                try {
                    JobStatus jobStatus = JobStatus.valueOf(request.getStatus().toUpperCase());
                    predicates.add(cb.equal(root.get("status"), jobStatus));
                } catch (IllegalArgumentException e) {
                    throw new BusinessException(400, "Invalid status: " + request.getStatus());
                }
            }
            if (request.getServiceName() != null && !request.getServiceName().isEmpty()) {
                predicates.add(cb.equal(root.get("serviceName"), request.getServiceName()));
            }
            if (request.getJobType() != null && !request.getJobType().isEmpty()) {
                predicates.add(cb.equal(root.get("jobType"), request.getJobType()));
            }

            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }

    private JobDetailResponse toDetail(JobEntity job) {
        JobDetailResponse dto = new JobDetailResponse();
        dto.setId(job.getId());
        dto.setTenantId(job.getTenantId());
        dto.setServiceName(job.getServiceName());
        dto.setJobType(job.getJobType());
        dto.setMutexKey(job.getMutexKey());
        dto.setTaskConcurrency(job.getTaskConcurrency());
        dto.setMaxAttempts(job.getMaxAttempts());
        dto.setStatus(job.getStatus().name());
        dto.setScheduledAt(job.getScheduledAt());
        dto.setTotalCount(job.getTotalCount());
        dto.setSucceededCount(job.getSucceededCount());
        dto.setFailedCount(job.getFailedCount());
        dto.setCancelledCount(job.getCancelledCount());
        dto.setProgress(calcProgress(job));
        dto.setCreatedAt(job.getCreatedAt());
        dto.setUpdatedAt(job.getUpdatedAt());
        return dto;
    }

    private double calcProgress(JobEntity job) {
        if (job.getTotalCount() == 0) {
            return 0;
        }
        int terminal = job.getSucceededCount() + job.getFailedCount() + job.getCancelledCount();
        return terminal * 100.0 / job.getTotalCount();
    }

}
