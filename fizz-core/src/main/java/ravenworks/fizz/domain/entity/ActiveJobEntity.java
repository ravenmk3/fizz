package ravenworks.fizz.domain.entity;

import jakarta.persistence.*;
import ravenworks.fizz.engine.enums.JobStatus;

import java.time.Instant;


@Entity
@Table(name = "fizz_active_job")
public class ActiveJobEntity {

    @Id
    @Column(columnDefinition = "CHAR(32)")
    private String id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "queueing_key")
    private String queueingKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JobStatus status;

    @Column(name = "scheduled_at")
    private Instant scheduledAt;

    @Version
    private int version;

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

    public String getQueueingKey() {
        return queueingKey;
    }

    public void setQueueingKey(String queueingKey) {
        this.queueingKey = queueingKey;
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

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

}
