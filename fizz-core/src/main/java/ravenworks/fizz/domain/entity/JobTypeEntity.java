package ravenworks.fizz.domain.entity;

import jakarta.persistence.*;
import ravenworks.fizz.engine.enums.BackoffStrategy;
import java.time.Instant;

@Entity
@Table(name = "fizz_job_type")
public class JobTypeEntity {

    @Id
    @Column(columnDefinition = "CHAR(32)")
    private String id;

    @Column(name = "job_type", nullable = false, unique = true)
    private String jobType;

    @Column(name = "service_name", nullable = false)
    private String serviceName;

    @Column(name = "task_path", nullable = false)
    private String taskPath;

    @Column(name = "notify_path")
    private String notifyPath;

    @Column(name = "http_method", nullable = false)
    private String httpMethod = "POST";

    @Column(name = "timeout_ms", nullable = false)
    private int timeoutMs = 30000;

    @Enumerated(EnumType.STRING)
    @Column(name = "backoff_strategy", nullable = false)
    private BackoffStrategy backoffStrategy = BackoffStrategy.FIXED;

    @Column(name = "backoff_initial_ms", nullable = false)
    private int backoffInitialMs = 10000;

    @Column(name = "backoff_max_ms", nullable = false)
    private int backoffMaxMs = 300000;

    @Column(name = "job_concurrency", nullable = false)
    private int jobConcurrency = 10;

    @Column(name = "task_concurrency", nullable = false)
    private int taskConcurrency = 1;

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

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getJobType() { return jobType; }
    public void setJobType(String jobType) { this.jobType = jobType; }

    public String getServiceName() { return serviceName; }
    public void setServiceName(String serviceName) { this.serviceName = serviceName; }

    public String getTaskPath() { return taskPath; }
    public void setTaskPath(String taskPath) { this.taskPath = taskPath; }

    public String getNotifyPath() { return notifyPath; }
    public void setNotifyPath(String notifyPath) { this.notifyPath = notifyPath; }

    public String getHttpMethod() { return httpMethod; }
    public void setHttpMethod(String httpMethod) { this.httpMethod = httpMethod; }

    public int getTimeoutMs() { return timeoutMs; }
    public void setTimeoutMs(int timeoutMs) { this.timeoutMs = timeoutMs; }

    public BackoffStrategy getBackoffStrategy() { return backoffStrategy; }
    public void setBackoffStrategy(BackoffStrategy backoffStrategy) { this.backoffStrategy = backoffStrategy; }

    public int getBackoffInitialMs() { return backoffInitialMs; }
    public void setBackoffInitialMs(int backoffInitialMs) { this.backoffInitialMs = backoffInitialMs; }

    public int getBackoffMaxMs() { return backoffMaxMs; }
    public void setBackoffMaxMs(int backoffMaxMs) { this.backoffMaxMs = backoffMaxMs; }

    public int getJobConcurrency() { return jobConcurrency; }
    public void setJobConcurrency(int jobConcurrency) { this.jobConcurrency = jobConcurrency; }

    public int getTaskConcurrency() { return taskConcurrency; }
    public void setTaskConcurrency(int taskConcurrency) { this.taskConcurrency = taskConcurrency; }

    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
