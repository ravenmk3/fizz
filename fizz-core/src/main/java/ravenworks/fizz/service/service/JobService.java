package ravenworks.fizz.service.service;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ravenworks.fizz.domain.entity.*;
import ravenworks.fizz.domain.repository.*;
import ravenworks.fizz.engine.component.Scheduler;
import ravenworks.fizz.engine.enums.JobStatus;
import ravenworks.fizz.engine.enums.TaskStatus;
import ravenworks.fizz.common.json.JsonUtils;
import ravenworks.fizz.common.util.UUIDv7;
import ravenworks.fizz.service.event.JobCreatedEvent;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
public class JobService {

    private final JobRepository jobRepository;
    private final TaskRepository taskRepository;
    private final ActiveJobRepository activeJobRepository;
    private final JobTypeRepository jobTypeRepository;
    private final ServiceRepository serviceRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Scheduler scheduler;

    public JobService(JobRepository jobRepository, TaskRepository taskRepository,
                      ActiveJobRepository activeJobRepository, JobTypeRepository jobTypeRepository,
                      ServiceRepository serviceRepository,
                      ApplicationEventPublisher eventPublisher,
                      Scheduler scheduler) {
        this.jobRepository = jobRepository;
        this.taskRepository = taskRepository;
        this.activeJobRepository = activeJobRepository;
        this.jobTypeRepository = jobTypeRepository;
        this.serviceRepository = serviceRepository;
        this.eventPublisher = eventPublisher;
        this.scheduler = scheduler;
    }

    @Transactional(rollbackFor = Throwable.class)
    public CreateJobResult createJob(String tenantId, String serviceName, String jobType,
                                      String queueingKey, String bizKey,
                                      Integer taskConcurrency, Integer maxAttempts,
                                      Instant scheduledAt, List<Map<String, Object>> tasks) {
        if (!serviceRepository.existsByServiceName(serviceName)) {
            throw new IllegalArgumentException("Service not found: " + serviceName);
        }

        JobTypeEntity jobTypeEntity = jobTypeRepository.findByJobType(jobType)
                .orElseThrow(() -> new IllegalArgumentException("Job type not found: " + jobType));
        if (!jobTypeEntity.getServiceName().equals(serviceName)) {
            throw new IllegalArgumentException("Job type '" + jobType + "' does not belong to service '" + serviceName + "'");
        }

        if (tasks == null || tasks.isEmpty()) {
            throw new IllegalArgumentException("Tasks must not be empty");
        }
        if (taskConcurrency != null && taskConcurrency <= 0) {
            throw new IllegalArgumentException("taskConcurrency must be > 0");
        }

        if (bizKey != null && !bizKey.isBlank()) {
            var existing = jobRepository.findByJobTypeAndBizKey(jobType, bizKey);
            if (existing.isPresent()) {
                return new CreateJobResult(existing.get(), false);
            }
        }

        JobEntity job = new JobEntity();
        job.setId(UUIDv7.generate());
        job.setTenantId(tenantId);
        job.setServiceName(serviceName);
        job.setJobType(jobType);
        job.setQueueingKey(queueingKey);
        job.setBizKey(bizKey);
        job.setTaskConcurrency(taskConcurrency != null ? taskConcurrency : jobTypeEntity.getTaskConcurrency());
        job.setMaxAttempts(maxAttempts != null ? maxAttempts : -1);
        job.setStatus(JobStatus.PENDING);
        job.setScheduledAt(scheduledAt);
        job.setTotalCount(tasks.size());
        jobRepository.save(job);

        for (Map<String, Object> params : tasks) {
            TaskEntity task = new TaskEntity();
            task.setId(UUIDv7.generate());
            task.setJobId(job.getId());
            task.setParams(JsonUtils.toJson(params));
            task.setStatus(TaskStatus.PENDING);
            taskRepository.save(task);
        }

        ActiveJobEntity activeJob = new ActiveJobEntity();
        activeJob.setId(job.getId());
        activeJob.setTenantId(tenantId);
        activeJob.setQueueingKey(queueingKey);
        activeJob.setStatus(JobStatus.PENDING);
        activeJob.setScheduledAt(scheduledAt);
        activeJobRepository.save(activeJob);

        eventPublisher.publishEvent(new JobCreatedEvent(job.getId(), tenantId, jobType, scheduledAt));

        return new CreateJobResult(job, true);
    }

    @Transactional(rollbackFor = Throwable.class)
    public CancelResult cancelJob(String jobId) {
        JobEntity job = jobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Job not found: " + jobId));

        if (job.getStatus() == JobStatus.SUCCESS
                || job.getStatus() == JobStatus.FAILED
                || job.getStatus() == JobStatus.CANCELLED) {
            throw new ConflictException("Job is already in terminal state: " + job.getStatus());
        }

        job.setStatus(JobStatus.CANCELLED);
        jobRepository.save(job);

        int cancelledTasks = taskRepository.cancelPendingByJobId(jobId);
        activeJobRepository.deleteById(jobId);

        scheduler.cancelJob(jobId, job.getTenantId(), job.getJobType());

        return new CancelResult(jobId, "CANCELLED", cancelledTasks);
    }

    public JobEntity getJob(String jobId) {
        return jobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Job not found: " + jobId));
    }

    public Page<JobEntity> listJobs(String tenantId, String status, String serviceName,
                                    String jobType, int page, int size) {
        Specification<JobEntity> spec = (root, query, cb) -> cb.conjunction();

        if (tenantId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("tenantId"), tenantId));
        }
        if (status != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), JobStatus.valueOf(status)));
        }
        if (serviceName != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("serviceName"), serviceName));
        }
        if (jobType != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("jobType"), jobType));
        }

        PageRequest pageRequest = PageRequest.of(page - 1, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "createdAt"));
        return jobRepository.findAll(spec, pageRequest);
    }

    public record CreateJobResult(JobEntity job, boolean created) {}

    public record CancelResult(String id, String status, int cancelledTasks) {}

    public static class ResourceNotFoundException extends RuntimeException {
        public ResourceNotFoundException(String message) { super(message); }
    }

    public static class ConflictException extends RuntimeException {
        public ConflictException(String message) { super(message); }
    }
}
