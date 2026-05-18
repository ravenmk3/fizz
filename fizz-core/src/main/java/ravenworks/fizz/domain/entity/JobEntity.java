package ravenworks.fizz.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import ravenworks.fizz.domain.enums.JobStatus;

import java.io.Serializable;
import java.time.Instant;


@Getter
@Setter
@Entity
@Table(name = "fizz_job")
public class JobEntity implements Serializable {

    @Id
    @Column(columnDefinition = "CHAR(32)")
    private String id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "service_name", nullable = false)
    private String serviceName;

    @Column(name = "job_type", nullable = false)
    private String jobType;

    @Column(name = "mutex_key")
    private String mutexKey;

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

    @Column(name = "succeeded_count", nullable = false)
    private int succeededCount;

    @Column(name = "failed_count", nullable = false)
    private int failedCount;

    @Column(name = "cancelled_count", nullable = false)
    private int cancelledCount;

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
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

}
