package ravenworks.fizz.engine.model;

import ravenworks.fizz.engine.enums.TaskResultStatus;
import ravenworks.fizz.engine.enums.TaskStatus;
import java.time.Instant;

public class Task {

    private String id;
    private String jobId;
    private String params;
    private TaskStatus status;
    private int attempts;
    private Instant availableAt;
    private TaskResultStatus lastResult;
    private String lastError;
    private String instanceId;
    private int version;

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
}
