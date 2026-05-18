package ravenworks.fizz.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import ravenworks.fizz.domain.enums.JobStatus;

import java.io.Serializable;
import java.time.Instant;


@Setter
@Getter
@Entity
@Table(name = "fizz_active_job")
public class ActiveJobEntity implements Serializable {

    @Id
    @Column(columnDefinition = "CHAR(32)")
    private String id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "job_type", nullable = false)
    private String jobType;

    @Column(name = "mutex_key")
    private String mutexKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JobStatus status;

    @Column(name = "scheduled_at")
    private Instant scheduledAt;

    @Version
    private int version;

}
