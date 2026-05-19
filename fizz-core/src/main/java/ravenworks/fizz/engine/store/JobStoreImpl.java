package ravenworks.fizz.engine.store;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import ravenworks.fizz.common.runtime.InstanceId;
import ravenworks.fizz.domain.entity.ActiveJobEntity;
import ravenworks.fizz.domain.entity.JobEntity;
import ravenworks.fizz.domain.entity.TaskEntity;
import ravenworks.fizz.domain.enums.JobStatus;
import ravenworks.fizz.domain.enums.TaskResultStatus;
import ravenworks.fizz.domain.enums.TaskStatus;
import ravenworks.fizz.domain.repository.ActiveJobRepository;
import ravenworks.fizz.domain.repository.JobRepository;
import ravenworks.fizz.domain.repository.TaskRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;


/**
 * @author Raven
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JobStoreImpl implements JobStore {

    private final ActiveJobRepository activeJobRepository;
    private final JobRepository jobRepository;
    private final TaskRepository taskRepository;
    private final TransactionTemplate transactionTemplate;


    public record TaskCounts(int succeeded, int failed, int cancelled) {

    }

    @Override
    public List<JobEntity> claimPendingJobs(@NonNull LocalDateTime now, int limit) {
        List<ActiveJobEntity> pendingActives = activeJobRepository.findPendingReady(JobStatus.PENDING, now);
        if (pendingActives.isEmpty()) {
            return List.of();
        }

        List<JobEntity> claimed = new ArrayList<>();
        int count = 0;
        for (ActiveJobEntity active : pendingActives) {
            if (count >= limit) {
                break;
            }
            try {
                JobEntity claimedJob = transactionTemplate.execute(status -> claimOne(active.getId()));
                if (claimedJob != null) {
                    claimed.add(claimedJob);
                }
                count++;
            } catch (OptimisticLockingFailureException e) {
                log.debug("Job {} was claimed by another instance", active.getId());
            }
        }
        log.info("Claimed {} jobs out of {} pending", claimed.size(), Math.min(pendingActives.size(), limit));
        return claimed;
    }

    private JobEntity claimOne(String jobId) {
        ActiveJobEntity active = activeJobRepository.findById(jobId).orElse(null);
        if (active == null || active.getStatus() != JobStatus.PENDING) {
            return null;
        }
        JobEntity job = jobRepository.findById(jobId).orElse(null);
        if (job == null || job.getStatus() != JobStatus.PENDING) {
            return null;
        }
        active.setStatus(JobStatus.CLAIMED);
        job.setStatus(JobStatus.CLAIMED);
        activeJobRepository.save(active);
        jobRepository.save(job);
        log.debug("Job {} claimed", jobId);
        return job;
    }

    @Override
    public ActiveJobEntity findActiveJob(String jobId) {
        return activeJobRepository.findById(jobId).orElse(null);
    }

    @Override
    public void recoverActiveJobs() {
        List<JobEntity> jobs = jobRepository.findActiveJobsNotInStatus(JobStatus.PENDING);
        if (jobs.isEmpty()) {
            log.info("Recovery: no active jobs to recover");
            return;
        }
        int recovered = 0;
        int terminated = 0;
        for (JobEntity job : jobs) {
            Boolean jobTerminated = transactionTemplate.execute(ts -> {
                TaskCounts counts = countTasks(job.getId());
                updateJobCounts(job, counts);
                int terminal = counts.succeeded() + counts.failed() + counts.cancelled();
                if (terminal == job.getTotalCount()) {
                    JobStatus finalStatus = resolveFinalStatus(counts);
                    completeRecoveryTermination(job, finalStatus);
                    log.info("Recovery: job {} terminated as {}", job.getId(), finalStatus);
                    return true;
                }
                resetRunningTasks(job.getId());
                resetJobToPendingRecovery(job);
                log.info("Recovery: job {} reset to PENDING (terminal={}/{})",
                        job.getId(), terminal, job.getTotalCount());
                return false;
            });
            if (Boolean.TRUE.equals(jobTerminated)) {
                terminated++;
            } else {
                recovered++;
            }
        }
        log.info("Recovery complete: {} jobs recovered, {} jobs terminated", recovered, terminated);
    }

    @Override
    public List<TaskEntity> fetchReadyTasks(@NonNull String jobId,
                                            @NonNull LocalDateTime now,
                                            int limit) {
        return taskRepository.findPendingReady(jobId, now, PageRequest.of(0, Math.max(1, limit)));
    }

    @Override
    public List<TaskEntity> loadNonTerminalTasks(@NonNull String jobId) {
        return taskRepository.findByJobIdAndStatusIn(jobId,
                List.of(TaskStatus.PENDING, TaskStatus.RUNNING, TaskStatus.WAITING));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void claimTask(@NonNull String taskId) {
        TaskEntity task = taskRepository.findById(taskId).orElse(null);
        if (task == null || (task.getStatus() != TaskStatus.PENDING
                && task.getStatus() != TaskStatus.WAITING)) {
            return;
        }
        task.setStatus(TaskStatus.RUNNING);
        task.setAttempts(task.getAttempts() + 1);
        task.setInstanceId(InstanceId.VALUE);
        taskRepository.save(task);
        log.debug("Task {} claimed (attempt {})", taskId, task.getAttempts());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markTaskSucceeded(@NonNull String taskId,
                                  @NonNull String jobId) {
        TaskEntity task = taskRepository.findById(taskId).orElse(null);
        if (task == null) {
            return;
        }
        task.setStatus(TaskStatus.SUCCEEDED);
        task.setLastResult(TaskResultStatus.SUCCEEDED);
        task.setInstanceId(null);
        taskRepository.save(task);

        JobEntity job = jobRepository.findById(jobId).orElse(null);
        if (job != null) {
            job.setSucceededCount(job.getSucceededCount() + 1);
            jobRepository.save(job);
        }
        log.debug("Task {} succeeded, job {} succeededCount={}",
                taskId, jobId, job != null ? job.getSucceededCount() : 0);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markTaskRetry(@NonNull String taskId,
                              @NonNull LocalDateTime nextAvailable,
                              @NonNull TaskResultStatus lastResult,
                              String lastMessage) {
        TaskEntity task = taskRepository.findById(taskId).orElse(null);
        if (task == null) {
            return;
        }
        task.setStatus(TaskStatus.WAITING);
        task.setAvailableAt(nextAvailable);
        task.setLastResult(lastResult);
        task.setLastMessage(truncate(lastMessage));
        task.setInstanceId(null);
        taskRepository.save(task);
        log.debug("Task {} set WAITING, availableAt={}", taskId, nextAvailable);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markTaskFailed(@NonNull String taskId,
                               @NonNull String jobId,
                               String lastMessage) {
        TaskEntity task = taskRepository.findById(taskId).orElse(null);
        if (task == null) {
            return;
        }
        task.setStatus(TaskStatus.FAILED);
        task.setLastResult(TaskResultStatus.FAILED);
        task.setLastMessage(truncate(lastMessage));
        task.setInstanceId(null);
        taskRepository.save(task);

        JobEntity job = jobRepository.findById(jobId).orElse(null);
        if (job != null) {
            job.setFailedCount(job.getFailedCount() + 1);
            jobRepository.save(job);
        }
        log.debug("Task {} failed, job {} failedCount={}",
                taskId, jobId, job != null ? job.getFailedCount() : 0);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void transitionJobToRunning(@NonNull JobEntity job) {
        job.setStatus(JobStatus.RUNNING);
        job.setInstanceId(InstanceId.VALUE);
        jobRepository.save(job);

        ActiveJobEntity active = activeJobRepository.findById(job.getId()).orElse(null);
        if (active != null) {
            active.setStatus(JobStatus.RUNNING);
            activeJobRepository.save(active);
        }
        log.info("Job {} transitioned to RUNNING", job.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void completeJob(@NonNull String jobId,
                            @NonNull JobStatus finalStatus) {
        JobEntity job = jobRepository.findById(jobId).orElse(null);
        if (job == null) {
            return;
        }
        job.setStatus(finalStatus);
        jobRepository.save(job);
        activeJobRepository.deleteById(jobId);
        log.info("Job {} completed with status {}", jobId, finalStatus);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelJob(@NonNull String jobId) {
        int cancelled = taskRepository.cancelTasks(jobId,
                List.of(TaskStatus.PENDING, TaskStatus.RUNNING, TaskStatus.WAITING),
                TaskStatus.CANCELLED);

        JobEntity job = jobRepository.findById(jobId).orElse(null);
        if (job != null) {
            job.setCancelledCount(job.getCancelledCount() + cancelled);
            job.setStatus(JobStatus.CANCELLED);
            jobRepository.save(job);
        }

        activeJobRepository.deleteById(jobId);
        log.info("Job {} cancelled ({} tasks)", jobId, cancelled);
    }

    private TaskCounts countTasks(String jobId) {
        int succeeded = (int) taskRepository.countByJobIdAndStatus(jobId, TaskStatus.SUCCEEDED);
        int failed = (int) taskRepository.countByJobIdAndStatus(jobId, TaskStatus.FAILED);
        int cancelled = (int) taskRepository.countByJobIdAndStatus(jobId, TaskStatus.CANCELLED);
        return new TaskCounts(succeeded, failed, cancelled);
    }

    private void updateJobCounts(JobEntity job, TaskCounts counts) {
        if (job.getSucceededCount() == counts.succeeded()
                && job.getFailedCount() == counts.failed()
                && job.getCancelledCount() == counts.cancelled()) {
            return;
        }
        job.setSucceededCount(counts.succeeded());
        job.setFailedCount(counts.failed());
        job.setCancelledCount(counts.cancelled());
        jobRepository.save(job);
        log.debug("Job {} counts updated: s={}, f={}, c={}",
                job.getId(), counts.succeeded(), counts.failed(), counts.cancelled());
    }

    private void resetRunningTasks(String jobId) {
        int updated = taskRepository.resetRunningTasks(jobId,
                List.of(TaskStatus.RUNNING, TaskStatus.WAITING), TaskStatus.PENDING);
        log.debug("Job {} reset {} running/waiting tasks to PENDING", jobId, updated);
    }

    private void completeRecoveryTermination(JobEntity job, JobStatus status) {
        job.setStatus(status);
        jobRepository.save(job);
        activeJobRepository.deleteById(job.getId());
    }

    private void resetJobToPendingRecovery(JobEntity job) {
        job.setStatus(JobStatus.PENDING);
        jobRepository.save(job);

        ActiveJobEntity active = activeJobRepository.findById(job.getId()).orElse(null);
        if (active != null) {
            active.setStatus(JobStatus.PENDING);
            activeJobRepository.save(active);
        }
    }

    private static String truncate(String message) {
        if (message != null && message.length() > 512) {
            return message.substring(0, 512);
        }
        return message;
    }

    private static JobStatus resolveFinalStatus(TaskCounts counts) {
        if (counts.cancelled() > 0) {
            return JobStatus.CANCELLED;
        }
        if (counts.failed() > 0) {
            return JobStatus.FAILED;
        }
        return JobStatus.SUCCEEDED;
    }

}
