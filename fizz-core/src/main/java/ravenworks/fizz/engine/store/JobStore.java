package ravenworks.fizz.engine.store;

import org.springframework.transaction.annotation.Transactional;
import ravenworks.fizz.domain.entity.ActiveJobEntity;
import ravenworks.fizz.domain.entity.JobEntity;
import ravenworks.fizz.domain.entity.TaskEntity;
import ravenworks.fizz.domain.enums.JobStatus;
import ravenworks.fizz.domain.enums.TaskResultStatus;

import java.time.LocalDateTime;
import java.util.List;


/**
 * @author Raven
 */
public interface JobStore {

    List<JobEntity> claimPendingJobs(LocalDateTime now, int limit);

    void recoverActiveJobs();

    ActiveJobEntity findActiveJob(String jobId);

    JobEntity findJob(String jobId);

    List<TaskEntity> fetchReadyTasks(String jobId, LocalDateTime now, int limit);

    List<TaskEntity> loadNonTerminalTasks(String jobId);

    @Transactional(rollbackFor = Exception.class)
    void claimTask(String taskId);

    @Transactional(rollbackFor = Exception.class)
    void markTaskSucceeded(String taskId, String jobId);

    @Transactional(rollbackFor = Exception.class)
    void markTaskRetry(String taskId, LocalDateTime nextAvailable,
                       TaskResultStatus lastResult, String lastMessage);

    @Transactional(rollbackFor = Exception.class)
    void markTaskFailed(String taskId, String jobId, String lastMessage);

    @Transactional(rollbackFor = Exception.class)
    void transitionJobToRunning(JobEntity job);

    @Transactional(rollbackFor = Exception.class)
    void completeJob(String jobId, JobStatus finalStatus);

    @Transactional(rollbackFor = Exception.class)
    void cancelJob(String jobId);

}
