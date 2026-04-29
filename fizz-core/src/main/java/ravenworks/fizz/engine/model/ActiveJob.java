package ravenworks.fizz.engine.model;

import ravenworks.fizz.engine.enums.JobStatus;
import java.time.Instant;

public class ActiveJob {

    private String id;
    private String tenantId;
    private String queueingKey;
    private JobStatus status;
    private Instant scheduledAt;
    private int version;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getQueueingKey() { return queueingKey; }
    public void setQueueingKey(String queueingKey) { this.queueingKey = queueingKey; }

    public JobStatus getStatus() { return status; }
    public void setStatus(JobStatus status) { this.status = status; }

    public Instant getScheduledAt() { return scheduledAt; }
    public void setScheduledAt(Instant scheduledAt) { this.scheduledAt = scheduledAt; }

    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }
}
