package ravenworks.fizz.engine.model;

import ravenworks.fizz.engine.enums.JobStatus;
import ravenworks.fizz.engine.enums.NotificationStatus;
import java.time.Instant;

public class JobNotification {

    private String id;
    private String jobId;
    private JobStatus jobStatus;
    private NotificationStatus status;
    private int attempts;
    private int maxAttempts;
    private Instant availableAt;
    private String lastError;
    private int version;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getJobId() { return jobId; }
    public void setJobId(String jobId) { this.jobId = jobId; }

    public JobStatus getJobStatus() { return jobStatus; }
    public void setJobStatus(JobStatus jobStatus) { this.jobStatus = jobStatus; }

    public NotificationStatus getStatus() { return status; }
    public void setStatus(NotificationStatus status) { this.status = status; }

    public int getAttempts() { return attempts; }
    public void setAttempts(int attempts) { this.attempts = attempts; }

    public int getMaxAttempts() { return maxAttempts; }
    public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }

    public Instant getAvailableAt() { return availableAt; }
    public void setAvailableAt(Instant availableAt) { this.availableAt = availableAt; }

    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }

    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }
}
