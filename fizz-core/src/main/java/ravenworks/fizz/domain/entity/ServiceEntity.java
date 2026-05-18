package ravenworks.fizz.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.time.Instant;


@Getter
@Setter
@Entity
@Table(name = "fizz_service")
public class ServiceEntity implements Serializable {

    @Id
    @Column(columnDefinition = "CHAR(32)")
    private String id;

    @Column(name = "service_name", nullable = false, unique = true)
    private String serviceName;

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
