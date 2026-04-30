package ravenworks.fizz.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;


@Entity
@Table(name = "fizz_scheduler_lock")
public class SchedulerLockEntity {

    @Id
    private int id = 1;

    @Column(name = "instance_id", nullable = false, length = 32)
    private String instanceId;

    @Column(name = "acquired_at", nullable = false)
    private Instant acquiredAt;

    @Column(name = "heartbeat_at", nullable = false)
    private Instant heartbeatAt;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }

    public Instant getAcquiredAt() {
        return acquiredAt;
    }

    public void setAcquiredAt(Instant acquiredAt) {
        this.acquiredAt = acquiredAt;
    }

    public Instant getHeartbeatAt() {
        return heartbeatAt;
    }

    public void setHeartbeatAt(Instant heartbeatAt) {
        this.heartbeatAt = heartbeatAt;
    }

}
