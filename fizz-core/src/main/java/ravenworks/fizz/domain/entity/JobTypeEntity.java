package ravenworks.fizz.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import ravenworks.fizz.domain.enums.BackoffStrategy;

import java.io.Serializable;
import java.time.LocalDateTime;


@Getter
@Setter
@Entity
@Table(name = "fizz_job_type")
public class JobTypeEntity implements Serializable {

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
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

}
