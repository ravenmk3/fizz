package ravenworks.fizz.engine.store;

import ravenworks.fizz.engine.enums.JobStatus;
import ravenworks.fizz.engine.model.Job;


public interface JobStore {

    Job findById(String jobId);

    void updateStatus(String jobId, JobStatus status);

    void updateStatusAndInstanceId(String jobId, JobStatus status, String instanceId);

    void updateStatusAndCounts(String jobId, JobStatus status, int completedCount, int failedCount);

    void incrementCompletedCount(String jobId);

    void incrementFailedCount(String jobId);

}
