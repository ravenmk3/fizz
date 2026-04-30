package ravenworks.fizz.service.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ravenworks.fizz.common.util.UUIDv7;
import ravenworks.fizz.domain.entity.JobEntity;
import ravenworks.fizz.domain.entity.JobTypeEntity;
import ravenworks.fizz.domain.entity.ServiceEntity;
import ravenworks.fizz.domain.entity.ServiceInstanceEntity;
import ravenworks.fizz.domain.repository.JobRepository;
import ravenworks.fizz.domain.repository.JobTypeRepository;
import ravenworks.fizz.domain.repository.ServiceInstanceRepository;
import ravenworks.fizz.domain.repository.ServiceRepository;
import ravenworks.fizz.engine.enums.BackoffStrategy;
import ravenworks.fizz.engine.enums.JobStatus;
import ravenworks.fizz.service.discovery.DatabaseJobTypeRegistry;
import ravenworks.fizz.service.discovery.DatabaseServiceDiscovery;

import java.util.List;


@Service
public class ServiceManagementService {

    private final ServiceRepository serviceRepository;
    private final ServiceInstanceRepository serviceInstanceRepository;
    private final JobTypeRepository jobTypeRepository;
    private final JobRepository jobRepository;
    private final DatabaseServiceDiscovery serviceDiscovery;
    private final DatabaseJobTypeRegistry jobTypeRegistry;

    public ServiceManagementService(ServiceRepository serviceRepository,
                                    ServiceInstanceRepository serviceInstanceRepository,
                                    JobTypeRepository jobTypeRepository,
                                    JobRepository jobRepository,
                                    DatabaseServiceDiscovery serviceDiscovery,
                                    DatabaseJobTypeRegistry jobTypeRegistry) {
        this.serviceRepository = serviceRepository;
        this.serviceInstanceRepository = serviceInstanceRepository;
        this.jobTypeRepository = jobTypeRepository;
        this.jobRepository = jobRepository;
        this.serviceDiscovery = serviceDiscovery;
        this.jobTypeRegistry = jobTypeRegistry;
    }

    // ---- Service CRUD ----

    @Transactional(rollbackFor = Throwable.class)
    public ServiceEntity saveService(String serviceName) {
        return serviceRepository.findByServiceName(serviceName)
                .orElseGet(() -> {
                    ServiceEntity entity = new ServiceEntity();
                    entity.setId(UUIDv7.generate());
                    entity.setServiceName(serviceName);
                    return serviceRepository.save(entity);
                });
    }

    public List<ServiceEntity> listServices() {
        return serviceRepository.findAll();
    }

    @Transactional(rollbackFor = Throwable.class)
    public void deleteService(String serviceName) {
        if (jobTypeRepository.existsByServiceName(serviceName)) {
            throw new JobService.ConflictException("Cannot delete service with associated job types");
        }
        ServiceEntity entity = serviceRepository.findByServiceName(serviceName)
                .orElseThrow(() -> new JobService.ResourceNotFoundException("Service not found: " + serviceName));
        serviceRepository.delete(entity);
    }

    // ---- Service Instance CRUD ----

    @Transactional(rollbackFor = Throwable.class)
    public ServiceInstanceEntity saveServiceInstance(String serviceName, String scheme, String host, int port) {
        if (!serviceRepository.existsByServiceName(serviceName)) {
            throw new JobService.ResourceNotFoundException("Service not found: " + serviceName);
        }
        ServiceInstanceEntity entity = serviceInstanceRepository
                .findByServiceNameAndHostAndPort(serviceName, host, port)
                .orElseGet(() -> {
                    ServiceInstanceEntity e = new ServiceInstanceEntity();
                    e.setId(UUIDv7.generate());
                    e.setServiceName(serviceName);
                    e.setHost(host);
                    e.setPort(port);
                    return e;
                });
        entity.setScheme(scheme != null ? scheme : "http");
        ServiceInstanceEntity saved = serviceInstanceRepository.save(entity);
        serviceDiscovery.invalidate(serviceName);
        return saved;
    }

    public List<ServiceInstanceEntity> listServiceInstances(String serviceName) {
        return serviceInstanceRepository.findByServiceName(serviceName);
    }

    @Transactional(rollbackFor = Throwable.class)
    public void deleteServiceInstance(String id) {
        ServiceInstanceEntity entity = serviceInstanceRepository.findById(id)
                .orElseThrow(() -> new JobService.ResourceNotFoundException("Service instance not found: " + id));
        serviceInstanceRepository.delete(entity);
        serviceDiscovery.invalidate(entity.getServiceName());
    }

    // ---- Job Type CRUD ----

    @Transactional(rollbackFor = Throwable.class)
    public JobTypeEntity saveJobType(String serviceName, String jobType, String taskPath,
                                     String notifyPath, String httpMethod, Integer timeoutMs,
                                     String backoffStrategy, Integer backoffInitialMs, Integer backoffMaxMs,
                                     Integer jobConcurrency, Integer taskConcurrency) {
        if (!serviceRepository.existsByServiceName(serviceName)) {
            throw new JobService.ResourceNotFoundException("Service not found: " + serviceName);
        }
        JobTypeEntity entity = jobTypeRepository.findByJobType(jobType)
                .orElseGet(() -> {
                    JobTypeEntity e = new JobTypeEntity();
                    e.setId(UUIDv7.generate());
                    e.setServiceName(serviceName);
                    e.setJobType(jobType);
                    return e;
                });

        entity.setTaskPath(taskPath);
        entity.setNotifyPath(notifyPath);
        entity.setHttpMethod(httpMethod != null ? httpMethod : "POST");
        if (timeoutMs != null) entity.setTimeoutMs(timeoutMs);
        if (backoffStrategy != null) entity.setBackoffStrategy(BackoffStrategy.valueOf(backoffStrategy));
        if (backoffInitialMs != null) entity.setBackoffInitialMs(backoffInitialMs);
        if (backoffMaxMs != null) entity.setBackoffMaxMs(backoffMaxMs);
        if (jobConcurrency != null) entity.setJobConcurrency(jobConcurrency);
        if (taskConcurrency != null) entity.setTaskConcurrency(taskConcurrency);

        JobTypeEntity saved = jobTypeRepository.save(entity);
        jobTypeRegistry.invalidate(jobType);
        return saved;
    }

    @Transactional(rollbackFor = Throwable.class)
    public JobTypeEntity updateJobType(String jobType, String taskPath, String notifyPath,
                                       String httpMethod, Integer timeoutMs, String backoffStrategy,
                                       Integer backoffInitialMs, Integer backoffMaxMs,
                                       Integer jobConcurrency, Integer taskConcurrency) {
        JobTypeEntity entity = jobTypeRepository.findByJobType(jobType)
                .orElseThrow(() -> new JobService.ResourceNotFoundException("Job type not found: " + jobType));

        if (taskPath != null) entity.setTaskPath(taskPath);
        if (notifyPath != null) entity.setNotifyPath(notifyPath);
        if (httpMethod != null) entity.setHttpMethod(httpMethod);
        if (timeoutMs != null) entity.setTimeoutMs(timeoutMs);
        if (backoffStrategy != null) entity.setBackoffStrategy(BackoffStrategy.valueOf(backoffStrategy));
        if (backoffInitialMs != null) entity.setBackoffInitialMs(backoffInitialMs);
        if (backoffMaxMs != null) entity.setBackoffMaxMs(backoffMaxMs);
        if (jobConcurrency != null) entity.setJobConcurrency(jobConcurrency);
        if (taskConcurrency != null) entity.setTaskConcurrency(taskConcurrency);

        JobTypeEntity saved = jobTypeRepository.save(entity);
        jobTypeRegistry.invalidate(jobType);
        return saved;
    }

    public List<JobTypeEntity> listJobTypes(String serviceName) {
        if (serviceName != null) {
            return jobTypeRepository.findByServiceName(serviceName);
        }
        return jobTypeRepository.findAll();
    }

    @Transactional(rollbackFor = Throwable.class)
    public void deleteJobType(String jobType) {
        JobTypeEntity entity = jobTypeRepository.findByJobType(jobType)
                .orElseThrow(() -> new JobService.ResourceNotFoundException("Job type not found: " + jobType));

        // Check active jobs
        List<JobEntity> activeJobs = jobRepository.findByStatus(JobStatus.PENDING);
        activeJobs.addAll(jobRepository.findByStatus(JobStatus.RUNNING));
        boolean hasActive = activeJobs.stream().anyMatch(j -> j.getJobType().equals(jobType));
        if (hasActive) {
            throw new JobService.ConflictException("Cannot delete job type with active jobs");
        }

        jobTypeRepository.delete(entity);
        jobTypeRegistry.invalidate(jobType);
    }

}
