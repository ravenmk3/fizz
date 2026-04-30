package ravenworks.fizz.domain.entity;

import jakarta.persistence.*;
import ravenworks.fizz.engine.enums.JobStatus;

import java.time.Instant;


@Entity
@Table(name = "fizz_job")
public class JobEntity {

    @Id
    @Column(columnDefinition = "CHAR(32)")
    private String id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "service_name", nullable = false)
    private String serviceName;

    @Column(name = "job_type", nullable = false)
    private String jobType;

    @Column(name = "queueing_key")
    private String queueingKey;

    @Column(name = "biz_key")
    private String bizKey;

    @Column(name = "task_concurrency", nullable = false)
    private int taskConcurrency = 1;

    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts = -1;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JobStatus status = JobStatus.PENDING;

    @Column(name = "scheduled_at")
    private Instant scheduledAt;

    @Column(name = "total_count", nullable = false)
    private int totalCount;

    @Column(name = "completed_count", nullable = false)
    private int completedCount;

    @Column(name = "failed_count", nullable = false)
    private int failedCount;

    @Column(name = "instance_id")
    private String instanceId;

    @Version
    private int version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public String getJobType() {
        return jobType;
    }

    public void setJobType(String jobType) {
        this.jobType = jobType;
    }

    public String getQueueingKey() {
        return queueingKey;
    }

    public void setQueueingKey(String queueingKey) {
        this.queueingKey = queueingKey;
    }

    public String getBizKey() {
        return bizKey;
    }

    public void setBizKey(String bizKey) {
        this.bizKey = bizKey;
    }

    public int getTaskConcurrency() {
        return taskConcurrency;
    }

    public void setTaskConcurrency(int taskConcurrency) {
        this.taskConcurrency = taskConcurrency;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public JobStatus getStatus() {
        return status;
    }

    public void setStatus(JobStatus status) {
        this.status = status;
    }

    public Instant getScheduledAt() {
        return scheduledAt;
    }

    public void setScheduledAt(Instant scheduledAt) {
        this.scheduledAt = scheduledAt;
    }

    public int getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(int totalCount) {
        this.totalCount = totalCount;
    }

    public int getCompletedCount() {
        return completedCount;
    }

    public void setCompletedCount(int completedCount) {
        this.completedCount = completedCount;
    }

    public int getFailedCount() {
        return failedCount;
    }

    public void setFailedCount(int failedCount) {
        this.failedCount = failedCount;
    }

    public String getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

}
