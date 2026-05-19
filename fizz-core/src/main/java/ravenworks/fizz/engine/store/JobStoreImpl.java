package ravenworks.fizz.engine.store;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import ravenworks.fizz.domain.entity.ActiveJobEntity;
import ravenworks.fizz.domain.entity.JobEntity;
import ravenworks.fizz.domain.enums.JobStatus;
import ravenworks.fizz.domain.enums.TaskStatus;
import ravenworks.fizz.domain.repository.ActiveJobRepository;
import ravenworks.fizz.domain.repository.JobRepository;
import ravenworks.fizz.domain.repository.TaskRepository;

import java.time.Instant;
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
    public List<JobEntity> claimPendingJobs(@NonNull Instant now, int limit) {
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
            TaskCounts counts = countTasks(job.getId());
            updateJobCounts(job, counts);
            int terminal = counts.succeeded() + counts.failed() + counts.cancelled();
            if (terminal == job.getTotalCount()) {
                JobStatus finalStatus = resolveFinalStatus(counts);
                terminateJob(job, finalStatus);
                terminated++;
                log.info("Recovery: job {} terminated as {}", job.getId(), finalStatus);
            } else {
                resetRunningTasks(job.getId());
                resetJobToPending(job);
                recovered++;
                log.info("Recovery: job {} reset to PENDING (terminal={}/{})",
                        job.getId(), terminal, job.getTotalCount());
            }
        }
        log.info("Recovery complete: {} jobs recovered, {} jobs terminated", recovered, terminated);
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
        transactionTemplate.executeWithoutResult(status -> {
            job.setSucceededCount(counts.succeeded());
            job.setFailedCount(counts.failed());
            job.setCancelledCount(counts.cancelled());
            jobRepository.save(job);
            log.debug("Job {} counts updated: s={}, f={}, c={}",
                    job.getId(), counts.succeeded(), counts.failed(), counts.cancelled());
        });
    }

    private void resetRunningTasks(String jobId) {
        int updated = taskRepository.resetRunningTasks(jobId,
                List.of(TaskStatus.RUNNING, TaskStatus.WAITING), TaskStatus.PENDING);
        log.debug("Job {} reset {} running/waiting tasks to PENDING", jobId, updated);
    }

    private void terminateJob(JobEntity job, JobStatus status) {
        transactionTemplate.executeWithoutResult(ts -> {
            job.setStatus(status);
            jobRepository.save(job);
            activeJobRepository.deleteById(job.getId());
            log.info("Job {} terminated with status {}", job.getId(), status);
        });
    }

    private void resetJobToPending(JobEntity job) {
        transactionTemplate.executeWithoutResult(ts -> {
            job.setStatus(JobStatus.PENDING);
            jobRepository.save(job);

            ActiveJobEntity active = activeJobRepository.findById(job.getId()).orElse(null);
            if (active != null) {
                active.setStatus(JobStatus.PENDING);
                activeJobRepository.save(active);
            }
        });
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
