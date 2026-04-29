package ravenworks.fizz.domain.entity;

import jakarta.persistence.*;
import ravenworks.fizz.engine.enums.TaskResultStatus;
import ravenworks.fizz.engine.enums.TaskStatus;
import java.time.Instant;

@Entity
@Table(name = "fizz_task")
public class TaskEntity {

    @Id
    @Column(columnDefinition = "CHAR(32)")
    private String id;

    @Column(name = "job_id", nullable = false, columnDefinition = "CHAR(32)")
    private String jobId;

    @Column(nullable = false, columnDefinition = "JSON")
    private String params;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskStatus status = TaskStatus.PENDING;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "available_at", nullable = false)
    private Instant availableAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "last_result")
    private TaskResultStatus lastResult;

    @Column(name = "last_error", length = 512)
    private String lastError;

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
        if (availableAt == null) availableAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getJobId() { return jobId; }
    public void setJobId(String jobId) { this.jobId = jobId; }

    public String getParams() { return params; }
    public void setParams(String params) { this.params = params; }

    public TaskStatus getStatus() { return status; }
    public void setStatus(TaskStatus status) { this.status = status; }

    public int getAttempts() { return attempts; }
    public void setAttempts(int attempts) { this.attempts = attempts; }

    public Instant getAvailableAt() { return availableAt; }
    public void setAvailableAt(Instant availableAt) { this.availableAt = availableAt; }

    public TaskResultStatus getLastResult() { return lastResult; }
    public void setLastResult(TaskResultStatus lastResult) { this.lastResult = lastResult; }

    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }

    public String getInstanceId() { return instanceId; }
    public void setInstanceId(String instanceId) { this.instanceId = instanceId; }

    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
