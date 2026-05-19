package ravenworks.fizz.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import ravenworks.fizz.domain.enums.TaskResultStatus;
import ravenworks.fizz.domain.enums.TaskStatus;

import java.io.Serializable;
import java.time.LocalDateTime;


@Getter
@Setter
@Entity
@Table(name = "fizz_task")
public class TaskEntity implements Serializable {

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
    private LocalDateTime availableAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "last_result")
    private TaskResultStatus lastResult;

    @Column(name = "last_message", length = 512)
    private String lastMessage;

    @Column(name = "instance_id")
    private String instanceId;

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
        if (this.availableAt == null) {
            this.availableAt = this.createdAt;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

}
