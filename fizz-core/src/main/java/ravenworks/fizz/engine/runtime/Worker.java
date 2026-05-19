package ravenworks.fizz.engine.runtime;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import ravenworks.fizz.common.runtime.EventLoop;
import ravenworks.fizz.domain.entity.ActiveJobEntity;
import ravenworks.fizz.domain.entity.JobEntity;
import ravenworks.fizz.domain.entity.JobTypeEntity;
import ravenworks.fizz.domain.entity.TaskEntity;
import ravenworks.fizz.domain.enums.BackoffStrategy;
import ravenworks.fizz.domain.enums.JobStatus;
import ravenworks.fizz.domain.enums.TaskResultStatus;
import ravenworks.fizz.domain.enums.TaskStatus;
import ravenworks.fizz.engine.discovery.ServiceHealthTracker;
import ravenworks.fizz.engine.invoker.TaskInvoker;
import ravenworks.fizz.engine.model.TaskResult;
import ravenworks.fizz.engine.store.JobStore;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;


/**
 * @author Raven
 */
@Slf4j
public class Worker {

    private final String name;
    private final EventLoop eventLoop;
    private final JobStore jobStore;
    private final TaskInvoker taskInvoker;
    private final JobTypeEntity jobType;
    private final String serviceName;
    private final ServiceHealthTracker healthTracker;

    private final Map<String, JobContext> assignedJobs = new LinkedHashMap<>();
    private final Map<String, JobContext> runningJobs = new LinkedHashMap<>();
    private final Set<String> runningMutexKeys = new HashSet<>();

    private JobListener listener = JobListener.NOOP;

    public Worker(@NonNull String name,
                  @NonNull JobStore jobStore,
                  @NonNull TaskInvoker taskInvoker,
                  @NonNull JobTypeEntity jobType,
                  @NonNull ServiceHealthTracker healthTracker) {
        this.name = name;
        this.jobStore = jobStore;
        this.taskInvoker = taskInvoker;
        this.jobType = jobType;
        this.serviceName = jobType.getServiceName();
        this.healthTracker = healthTracker;
        this.eventLoop = new EventLoop(name, 10_000, this::dispatch);
    }

    public void start() {
        this.eventLoop.start();
    }

    public CompletableFuture<Void> shutdown() {
        return this.eventLoop.shutdown();
    }

    public void assign(@NonNull JobEntity job) {
        this.eventLoop.enqueue(new JobAssigned(job));
    }

    public CompletableFuture<Void> cancel(@NonNull ActiveJobEntity job) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        this.eventLoop.enqueue(new CancelJobRequest(job, future));
        return future;
    }

    public void setListener(@NonNull JobListener listener) {
        this.listener = listener;
    }

    // ---------- Event Dispatch ----------

    private void dispatch(Object event) {
        switch (event) {
            case EventLoop.Idle _ -> this.onIdle();
            case EventLoop.Started _ -> this.onStarted();
            case EventLoop.PreShutdown _ -> this.onPreShutdown();
            case EventLoop.Terminated _ -> this.onTerminated();
            case TryDispatch _ -> this.tryDispatch();
            case JobAssigned(JobEntity job) -> this.onJobAssigned(job);
            case CancelJobRequest req -> this.onCancelJob(req);
            case TaskCompleted tc -> this.onTaskCompleted(tc);
            default -> log.warn("Unhandled event: {}", event);
        }
    }

    private void onStarted() {
        log.info("Worker [{}] started: jobType={}, serviceName={}, jobConcurrency={}",
                this.name, this.jobType.getJobType(), this.serviceName,
                this.jobType.getJobConcurrency());
        this.tryDispatch();
    }

    private void onIdle() {
        this.tryDispatch();
    }

    private void onPreShutdown() {
        int totalFutures = 0;
        for (JobContext ctx : runningJobs.values()) {
            ctx.futures.values().forEach(f -> f.cancel(true));
            totalFutures += ctx.futures.size();
            ctx.futures.clear();
        }
        if (totalFutures > 0) {
            log.warn("Worker [{}] shutting down, cancelled {} in-flight tasks",
                    this.name, totalFutures);
        }
    }

    private void onTerminated() {
        log.info("Worker [{}] terminated", this.name);
    }

    private void onJobAssigned(JobEntity job) {
        log.info("Worker [{}] assigned job: id={}, type={}, tasks={}",
                this.name, job.getId(), job.getJobType(), job.getTotalCount());
        JobContext ctx = new JobContext(this, job);
        this.assignedJobs.put(job.getId(), ctx);
        this.enqueueTryDispatch();
    }

    private void onCancelJob(CancelJobRequest req) {
        ActiveJobEntity active = req.job();
        String jobId = active.getId();
        log.info("Worker [{}] cancel job: id={}, type={}", this.name, jobId, active.getJobType());

        JobContext ctx = this.assignedJobs.get(jobId);
        if (ctx == null) {
            log.warn("Cancel: job {} not found in assigned", jobId);
            req.future().complete(null);
            return;
        }

        ctx.futures.values().forEach(f -> f.cancel(true));
        ctx.futures.clear();

        this.jobStore.cancelJob(jobId);

        if (ctx.active) {
            this.runningJobs.remove(jobId);
            if (ctx.job.getMutexKey() != null) {
                this.runningMutexKeys.remove(ctx.job.getMutexKey());
            }
        }
        this.assignedJobs.remove(jobId);

        this.listener.onStatusChanged(ctx.job, JobStatus.CANCELLED);

        req.future().complete(null);
        this.enqueueTryDispatch();
    }

    // ---------- Dispatch Logic ----------

    private void tryDispatch() {
        this.promoteJobs();
        this.dispatchTasks();
        long futures = this.runningJobs.values().stream()
                .mapToInt(ctx -> ctx.futures.size()).sum();
        int pendingInMemory = this.runningJobs.values().stream()
                .mapToInt(ctx -> (int) ctx.tasks.values().stream()
                        .filter(t -> t.getStatus() == TaskStatus.PENDING).count())
                .sum();
        log.debug("Worker [{}] dispatch: runningJobs={}, assignedJobs={}, futures={}, pendingInMemory={}",
                this.name, this.runningJobs.size(), this.assignedJobs.size(), futures, pendingInMemory);
    }

    private void promoteJobs() {
        for (JobContext ctx : this.assignedJobs.values()) {
            if (ctx.active) {
                continue;
            }
            JobEntity job = ctx.job;

            if (this.runningJobs.size() >= this.jobType.getJobConcurrency()) {
                log.debug("Worker [{}] jobConcurrency saturated ({}/{}), defer promotions",
                        this.name, this.runningJobs.size(), this.jobType.getJobConcurrency());
                break;
            }
            if (job.getScheduledAt() != null && job.getScheduledAt().isAfter(LocalDateTime.now())) {
                log.debug("Worker [{}] job {} not yet scheduled, at={}",
                        this.name, job.getId(), job.getScheduledAt());
                continue;
            }
            if (job.getMutexKey() != null && this.runningMutexKeys.contains(job.getMutexKey())) {
                String holder = this.runningJobs.values().stream()
                        .filter(c -> job.getMutexKey().equals(c.job.getMutexKey()))
                        .findFirst()
                        .map(c -> c.job.getId())
                        .orElse("?");
                log.info("Worker [{}] job {} blocked by mutexKey={}, held by job {}",
                        this.name, job.getId(), job.getMutexKey(), holder);
                continue;
            }
            if (!this.healthTracker.isAvailable(this.serviceName)) {
                log.warn("Worker [{}] service {} unavailable, skip dispatch",
                        this.name, this.serviceName);
                break;
            }
            if (!ctx.tasksLoaded) {
                this.loadTasks(ctx);
            }
            if (!this.hasReadyTask(ctx)) {
                log.debug("Worker [{}] job {} skipped: no ready task", this.name, job.getId());
                continue;
            }
            if (job.getStatus() == JobStatus.CLAIMED) {
                this.jobStore.transitionJobToRunning(job);
            }
            ctx.active = true;
            this.runningJobs.put(job.getId(), ctx);
            if (job.getMutexKey() != null) {
                this.runningMutexKeys.add(job.getMutexKey());
            }
            log.info("Job {} promoted to running (active={}/{})",
                    job.getId(), this.runningJobs.size(), this.jobType.getJobConcurrency());
            this.listener.onStatusChanged(job, JobStatus.RUNNING);
        }
    }

    private void loadTasks(JobContext ctx) {
        List<TaskEntity> tasks = this.jobStore.loadNonTerminalTasks(ctx.job.getId());
        long pending = tasks.stream().filter(t -> t.getStatus() == TaskStatus.PENDING).count();
        long waiting = tasks.stream().filter(t -> t.getStatus() == TaskStatus.WAITING).count();
        long running = tasks.stream().filter(t -> t.getStatus() == TaskStatus.RUNNING).count();
        for (TaskEntity task : tasks) {
            ctx.tasks.put(task.getId(), task);
        }
        ctx.tasksLoaded = true;
        log.info("Worker [{}] job {} loaded tasks: total={}, pending={}, waiting={}, running={}",
                this.name, ctx.job.getId(), tasks.size(), pending, waiting, running);
    }

    private boolean hasReadyTask(JobContext ctx) {
        LocalDateTime now = LocalDateTime.now();
        return ctx.tasks.values().stream()
                .anyMatch(t -> !t.getAvailableAt().isAfter(now)
                        && (t.getStatus() == TaskStatus.PENDING
                        || t.getStatus() == TaskStatus.WAITING));
    }

    private void dispatchTasks() {
        for (JobContext ctx : this.runningJobs.values()) {
            int taskConcurrency = ctx.job.getTaskConcurrency();
            if (taskConcurrency <= 0) {
                taskConcurrency = this.jobType.getTaskConcurrency();
            }
            int running = ctx.futures.size();
            int available = taskConcurrency - running;
            if (available <= 0) {
                continue;
            }

            LocalDateTime now = LocalDateTime.now();
            List<TaskEntity> ready = ctx.tasks.values().stream()
                    .filter(t -> !t.getAvailableAt().isAfter(now)
                            && (t.getStatus() == TaskStatus.PENDING
                            || t.getStatus() == TaskStatus.WAITING))
                    .sorted((a, b) -> a.getAvailableAt().compareTo(b.getAvailableAt()))
                    .limit(available)
                    .toList();

            if (!ready.isEmpty()) {
                log.debug("Worker [{}] job {} dispatching {} tasks (running={}, capacity={})",
                        this.name, ctx.job.getId(), ready.size(), running, taskConcurrency);
            }

            for (TaskEntity task : ready) {
                this.dispatchTask(ctx, task);
            }
        }
    }

    private void dispatchTask(JobContext ctx, TaskEntity task) {
        if (!this.healthTracker.isAvailable(this.serviceName)) {
            return;
        }
        try {
            this.jobStore.claimTask(task.getId());
        } catch (Exception e) {
            log.warn("Worker [{}] failed to claim task {}: {}", this.name, task.getId(), e.getMessage());
            return;
        }
        task.setStatus(TaskStatus.RUNNING);

        CompletableFuture<TaskResult> future = this.taskInvoker.invoke(
                this.serviceName,
                this.jobType.getHttpMethod(),
                this.jobType.getTaskPath(),
                task.getParams(),
                this.jobType.getTimeoutMs());

        ctx.futures.put(task.getId(), future);
        future.whenComplete((result, error) -> {
            this.eventLoop.enqueue(new TaskCompleted(task.getId(), ctx.job.getId(), result, error));
        });

        log.debug("Worker [{}] task {} dispatched for job {}, attempt={}",
                this.name, task.getId(), ctx.job.getId(), task.getAttempts());
    }

    // ---------- Task Result Handling ----------

    private void onTaskCompleted(TaskCompleted event) {
        JobContext ctx = this.assignedJobs.get(event.jobId());
        if (ctx == null) {
            log.warn("Worker [{}] orphan task completed: job {} not in assigned (task={})",
                    this.name, event.jobId(), event.taskId());
            return;
        }
        TaskEntity task = ctx.tasks.get(event.taskId());
        if (task == null) {
            log.warn("Worker [{}] orphan task completed: task {} not in job {} context",
                    this.name, event.taskId(), event.jobId());
            return;
        }

        ctx.futures.remove(event.taskId());

        if (event.error() != null) {
            String message = event.error().getMessage();
            if (message == null) {
                message = event.error().getClass().getSimpleName();
            }
            handleTaskFailure(ctx, task, message);
        } else if (event.result() != null) {
            switch (event.result().status()) {
                case TaskResultStatus.SUCCEEDED -> handleTaskSuccess(ctx, task);
                case TaskResultStatus.FAILED -> handleTaskFailure(ctx, task, event.result().message());
                case TaskResultStatus.IN_PROGRESS -> handleTaskInProgress(ctx, task, event.result());
            }
        } else {
            handleTaskFailure(ctx, task, "Unknown result");
        }

        logJobProgress(ctx);
        boolean completed = checkJobCompletion(ctx);
        if (!completed) {
            maybeDeactivateJob(ctx);
        }
        this.enqueueTryDispatch();
    }

    private void handleTaskSuccess(JobContext ctx, TaskEntity task) {
        this.jobStore.markTaskSucceeded(task.getId(), ctx.job.getId());
        ctx.job.setSucceededCount(ctx.job.getSucceededCount() + 1);
        ctx.tasks.remove(task.getId());
        this.healthTracker.recordSuccess(this.serviceName);
        log.debug("Task {} succeeded for job {}", task.getId(), ctx.job.getId());
    }

    private void handleTaskFailure(JobContext ctx, TaskEntity task, String message) {
        int maxAttempts = ctx.job.getMaxAttempts();
        int currentAttempt = task.getAttempts();

        this.healthTracker.recordFailure(this.serviceName);

        if (maxAttempts == -1 || currentAttempt < maxAttempts) {
            LocalDateTime nextRetry = computeBackoff(currentAttempt);
            this.jobStore.markTaskRetry(task.getId(), nextRetry,
                    TaskResultStatus.FAILED, message);
            task.setStatus(TaskStatus.WAITING);
            task.setAvailableAt(nextRetry);
            log.warn("Task {} failed (attempt {}/{}), retry at {}: {}",
                    task.getId(), currentAttempt,
                    maxAttempts == -1 ? "∞" : String.valueOf(maxAttempts),
                    nextRetry, message);
        } else {
            this.jobStore.markTaskFailed(task.getId(), ctx.job.getId(), message);
            ctx.job.setFailedCount(ctx.job.getFailedCount() + 1);
            ctx.tasks.remove(task.getId());
            log.warn("Worker [{}] task {} permanently failed after {} attempts: {}",
                    this.name, task.getId(), currentAttempt, message);
        }
    }

    private void handleTaskInProgress(JobContext ctx, TaskEntity task, TaskResult result) {
        LocalDateTime retryAfter = result.retryAfter() != null
                ? result.retryAfter()
                : LocalDateTime.now().plusSeconds(60);
        this.jobStore.markTaskRetry(task.getId(), retryAfter,
                TaskResultStatus.IN_PROGRESS, result.message());
        task.setStatus(TaskStatus.WAITING);
        task.setAvailableAt(retryAfter);
        log.info("Worker [{}] task {} IN_PROGRESS (attempt {}), retry after {}: {}",
                this.name, task.getId(), task.getAttempts(), retryAfter,
                result.message() != null ? result.message() : "");
    }

    private LocalDateTime computeBackoff(int attempts) {
        int initial = this.jobType.getBackoffInitialMs();
        int max = this.jobType.getBackoffMaxMs();
        BackoffStrategy strategy = this.jobType.getBackoffStrategy();

        long delayMs;
        if (strategy == BackoffStrategy.EXPONENTIAL) {
            delayMs = (long) initial * (1L << (attempts - 1));
        } else {
            delayMs = initial;
        }
        delayMs = Math.min(delayMs, max);
        return LocalDateTime.now().plus(Duration.ofMillis(delayMs));
    }

    // ---------- Job Lifecycle ----------

    private boolean checkJobCompletion(JobContext ctx) {
        int terminal = ctx.job.getSucceededCount()
                + ctx.job.getFailedCount()
                + ctx.job.getCancelledCount();
        if (terminal < ctx.job.getTotalCount()) {
            return false;
        }

        JobStatus finalStatus;
        if (ctx.job.getCancelledCount() > 0) {
            finalStatus = JobStatus.CANCELLED;
        } else if (ctx.job.getFailedCount() > 0) {
            finalStatus = JobStatus.FAILED;
        } else {
            finalStatus = JobStatus.SUCCEEDED;
        }

        this.jobStore.completeJob(ctx.job.getId(), finalStatus);
        this.runningJobs.remove(ctx.job.getId());
        if (ctx.job.getMutexKey() != null) {
            this.runningMutexKeys.remove(ctx.job.getMutexKey());
        }
        this.assignedJobs.remove(ctx.job.getId());
        log.info("Job {} completed: {}", ctx.job.getId(), finalStatus);
        this.listener.onStatusChanged(ctx.job, finalStatus);
        return true;
    }

    private void maybeDeactivateJob(JobContext ctx) {
        if (!ctx.active || !ctx.futures.isEmpty()) {
            return;
        }
        boolean allWaiting = ctx.tasks.isEmpty()
                || ctx.tasks.values().stream().allMatch(t -> t.getStatus() == TaskStatus.WAITING);
        if (!allWaiting) {
            return;
        }
        boolean hasWaitingJob = this.assignedJobs.values().stream()
                .anyMatch(c -> !c.active);
        if (!hasWaitingJob) {
            return;
        }
        ctx.active = false;
        this.runningJobs.remove(ctx.job.getId());
        if (ctx.job.getMutexKey() != null) {
            this.runningMutexKeys.remove(ctx.job.getMutexKey());
        }
        log.info("Job {} deactivated (all tasks waiting), released slot", ctx.job.getId());
    }

    private void logJobProgress(JobContext ctx) {
        int terminal = ctx.job.getSucceededCount()
                + ctx.job.getFailedCount()
                + ctx.job.getCancelledCount();
        int total = ctx.job.getTotalCount();
        if (total == 0) {
            return;
        }
        int pct = terminal * 100 / total;
        int prevPct = (terminal - 1) * 100 / total;
        if (pct != prevPct && pct % 25 == 0) {
            log.info("Worker [{}] job {} progress: {}/{} ({}%), s={}, f={}, c={}",
                    this.name, ctx.job.getId(), terminal, total, pct,
                    ctx.job.getSucceededCount(), ctx.job.getFailedCount(),
                    ctx.job.getCancelledCount());
            this.listener.onProgressChanged(ctx.job, terminal, total);
        }
    }

    private void enqueueTryDispatch() {
        this.eventLoop.enqueue(new TryDispatch());
    }

    // ---------- JobContext ----------


    static class JobContext {

        final Worker worker;
        final JobEntity job;
        final Map<String, TaskEntity> tasks = new HashMap<>();
        final Map<String, CompletableFuture<TaskResult>> futures = new HashMap<>();
        boolean active;
        boolean tasksLoaded;

        JobContext(Worker worker, JobEntity job) {
            this.worker = worker;
            this.job = job;
        }

    }

    // ---------- Events ----------


    record JobAssigned(JobEntity job) {

    }


    record CancelJobRequest(ActiveJobEntity job, CompletableFuture<Void> future) {

    }


    record TryDispatch() {

    }


    record TaskCompleted(String taskId, String jobId, TaskResult result, Throwable error) {

    }

}
