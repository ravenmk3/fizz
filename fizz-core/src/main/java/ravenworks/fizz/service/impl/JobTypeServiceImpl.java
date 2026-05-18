package ravenworks.fizz.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ravenworks.fizz.common.exception.BusinessException;
import ravenworks.fizz.common.util.Uuids;
import ravenworks.fizz.domain.entity.JobTypeEntity;
import ravenworks.fizz.domain.entity.ServiceEntity;
import ravenworks.fizz.domain.enums.BackoffStrategy;
import ravenworks.fizz.domain.enums.JobStatus;
import ravenworks.fizz.domain.repository.JobRepository;
import ravenworks.fizz.domain.repository.JobTypeRepository;
import ravenworks.fizz.domain.repository.ServiceRepository;
import ravenworks.fizz.service.JobTypeService;
import ravenworks.fizz.service.dto.SaveJobTypeRequest;
import ravenworks.fizz.service.dto.UpdateJobTypeRequest;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class JobTypeServiceImpl implements JobTypeService {

    private final ServiceRepository serviceRepository;
    private final JobTypeRepository jobTypeRepository;
    private final JobRepository jobRepository;

    @Override
    @Transactional
    public JobTypeEntity save(SaveJobTypeRequest request) {
        serviceRepository.findByServiceName(request.getServiceName())
                .orElseThrow(() -> new BusinessException(404, "Service not found: " + request.getServiceName()));

        BackoffStrategy backoffStrategy;
        try {
            backoffStrategy = BackoffStrategy.valueOf(request.getBackoffStrategy().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(400, "Invalid backoffStrategy: " + request.getBackoffStrategy());
        }

        JobTypeEntity entity = jobTypeRepository.findByJobType(request.getJobType())
                .orElseGet(() -> {
                    JobTypeEntity newEntity = new JobTypeEntity();
                    newEntity.setId(Uuids.uuid7Hex());
                    return newEntity;
                });

        entity.setServiceName(request.getServiceName());
        entity.setJobType(request.getJobType());
        entity.setTaskPath(request.getTaskPath());
        entity.setHttpMethod(request.getHttpMethod() != null ? request.getHttpMethod() : "POST");
        entity.setTimeoutMs(request.getTimeoutMs() != null ? request.getTimeoutMs() : 30000);
        entity.setBackoffStrategy(backoffStrategy);
        entity.setBackoffInitialMs(request.getBackoffInitialMs() != null ? request.getBackoffInitialMs() : 10000);
        entity.setBackoffMaxMs(request.getBackoffMaxMs() != null ? request.getBackoffMaxMs() : 300000);
        entity.setJobConcurrency(request.getJobConcurrency() != null ? request.getJobConcurrency() : 10);
        entity.setTaskConcurrency(request.getTaskConcurrency() != null ? request.getTaskConcurrency() : 1);
        entity.setNotifyPath(request.getNotifyPath());

        jobTypeRepository.save(entity);
        log.info("Job type saved: jobType={}, serviceName={}", request.getJobType(), request.getServiceName());
        return entity;
    }

    @Override
    @Transactional
    public JobTypeEntity update(UpdateJobTypeRequest request) {
        JobTypeEntity entity = jobTypeRepository.findByJobType(request.getJobType())
                .orElseThrow(() -> new BusinessException(404, "Job type not found: " + request.getJobType()));

        if (request.getTaskPath() != null) {
            entity.setTaskPath(request.getTaskPath());
        }
        if (request.getHttpMethod() != null) {
            entity.setHttpMethod(request.getHttpMethod());
        }
        if (request.getTimeoutMs() != null) {
            entity.setTimeoutMs(request.getTimeoutMs());
        }
        if (request.getBackoffStrategy() != null) {
            try {
                entity.setBackoffStrategy(BackoffStrategy.valueOf(request.getBackoffStrategy().toUpperCase()));
            } catch (IllegalArgumentException e) {
                throw new BusinessException(400, "Invalid backoffStrategy: " + request.getBackoffStrategy());
            }
        }
        if (request.getBackoffInitialMs() != null) {
            entity.setBackoffInitialMs(request.getBackoffInitialMs());
        }
        if (request.getBackoffMaxMs() != null) {
            entity.setBackoffMaxMs(request.getBackoffMaxMs());
        }
        if (request.getJobConcurrency() != null) {
            entity.setJobConcurrency(request.getJobConcurrency());
        }
        if (request.getTaskConcurrency() != null) {
            entity.setTaskConcurrency(request.getTaskConcurrency());
        }
        if (request.getNotifyPath() != null) {
            entity.setNotifyPath(request.getNotifyPath());
        }

        jobTypeRepository.save(entity);
        log.info("Job type updated: jobType={}", request.getJobType());
        return entity;
    }

    @Override
    public List<JobTypeEntity> list(String serviceName) {
        if (serviceName != null && !serviceName.isEmpty()) {
            return jobTypeRepository.findAllByServiceName(serviceName);
        }
        return jobTypeRepository.findAll();
    }

    @Override
    @Transactional
    public void delete(String jobType) {
        JobTypeEntity entity = jobTypeRepository.findByJobType(jobType)
                .orElseThrow(() -> new BusinessException(404, "Job type not found: " + jobType));

        long activeCount = jobRepository.countByJobTypeAndStatusIn(jobType,
                List.of(JobStatus.PENDING, JobStatus.RUNNING));
        if (activeCount > 0) {
            throw new BusinessException(409,
                    "Cannot delete job type '" + jobType + "': " + activeCount + " active job(s) exist");
        }

        jobTypeRepository.delete(entity);
        log.info("Job type deleted: {}", jobType);
    }

}
