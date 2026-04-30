package ravenworks.fizz.service.store;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ravenworks.fizz.domain.entity.JobEntity;
import ravenworks.fizz.domain.repository.JobRepository;
import ravenworks.fizz.engine.enums.JobStatus;
import ravenworks.fizz.engine.model.Job;
import ravenworks.fizz.engine.store.JobStore;


@Component
public class JobStoreImpl implements JobStore {

    private final JobRepository jobRepository;

    public JobStoreImpl(JobRepository jobRepository) {
        this.jobRepository = jobRepository;
    }

    @Override
    public Job findById(String jobId) {
        JobEntity entity = jobRepository.findById(jobId).orElse(null);
        return entity != null ? toModel(entity) : null;
    }

    @Override
    @Transactional(rollbackFor = Throwable.class)
    public void updateStatus(String jobId, JobStatus status) {
        jobRepository.updateStatus(jobId, status);
    }

    @Override
    @Transactional(rollbackFor = Throwable.class)
    public void updateStatusAndInstanceId(String jobId, JobStatus status, String instanceId) {
        jobRepository.updateStatusAndInstanceId(jobId, status, instanceId);
    }

    @Override
    @Transactional(rollbackFor = Throwable.class)
    public void updateStatusAndCounts(String jobId, JobStatus status, int completedCount, int failedCount) {
        jobRepository.updateStatusAndCounts(jobId, status.name(), completedCount, failedCount);
    }

    @Override
    @Transactional(rollbackFor = Throwable.class)
    public void incrementCompletedCount(String jobId) {
        jobRepository.incrementCompletedCount(jobId);
    }

    @Override
    @Transactional(rollbackFor = Throwable.class)
    public void incrementFailedCount(String jobId) {
        jobRepository.incrementFailedCount(jobId);
    }

    private Job toModel(JobEntity e) {
        Job job = new Job();
        job.setId(e.getId());
        job.setTenantId(e.getTenantId());
        job.setServiceName(e.getServiceName());
        job.setJobType(e.getJobType());
        job.setQueueingKey(e.getQueueingKey());
        job.setBizKey(e.getBizKey());
        job.setTaskConcurrency(e.getTaskConcurrency());
        job.setMaxAttempts(e.getMaxAttempts());
        job.setStatus(e.getStatus());
        job.setScheduledAt(e.getScheduledAt());
        job.setTotalCount(e.getTotalCount());
        job.setCompletedCount(e.getCompletedCount());
        job.setFailedCount(e.getFailedCount());
        job.setInstanceId(e.getInstanceId());
        job.setVersion(e.getVersion());
        return job;
    }

}
