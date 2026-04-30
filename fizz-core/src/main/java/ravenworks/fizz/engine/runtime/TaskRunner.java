package ravenworks.fizz.engine.runtime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ravenworks.fizz.engine.discovery.JobTypeConfig;
import ravenworks.fizz.engine.discovery.ServiceEndpoint;
import ravenworks.fizz.engine.enums.TaskStatus;
import ravenworks.fizz.engine.invoker.TaskInvoker;
import ravenworks.fizz.engine.model.Task;
import ravenworks.fizz.engine.model.TaskResult;
import ravenworks.fizz.engine.store.TaskStore;

import java.time.Instant;
import java.util.concurrent.locks.LockSupport;


public class TaskRunner {

    private final Logger LOGGER = LoggerFactory.getLogger(getClass());

    private final String taskId;
    private final String jobId;
    private final String params;
    private final int maxAttempts;
    private final JobTypeConfig config;
    private final ServiceEndpoint endpoint;
    private final TaskInvoker taskInvoker;
    private final Actor parent;
    private final TaskStore taskStore;

    private volatile boolean cancelled;
    private Thread taskThread;

    public TaskRunner(Task task, int maxAttempts, JobTypeConfig config,
                      ServiceEndpoint endpoint, TaskInvoker taskInvoker,
                      Actor parent, TaskStore taskStore) {
        this.taskId = task.getId();
        this.jobId = task.getJobId();
        this.params = task.getParams();
        this.maxAttempts = maxAttempts;
        this.config = config;
        this.endpoint = endpoint;
        this.taskInvoker = taskInvoker;
        this.parent = parent;
        this.taskStore = taskStore;
    }

    public void execute() {
        taskThread = Thread.ofVirtual().name("task-" + taskId).start(() -> {
            int attempts = 0;
            while (!cancelled && (maxAttempts == -1 || attempts < maxAttempts)) {
                TaskResult result = executeHttp();
                persist(attempts, result);
                switch (result.status()) {
                    case SUCCESS -> {
                        parent.tell(new JobRunner.TaskSucceeded(taskId));
                        return;
                    }
                    case FAILED -> {
                        attempts++;
                        if (maxAttempts != -1 && attempts >= maxAttempts) {
                            parent.tell(new JobRunner.TaskFailed(taskId));
                            return;
                        }
                        sleepBackoff(attempts);
                    }
                    case IN_PROGRESS -> {
                        parent.tell(new JobRunner.TaskDelayed(taskId));
                        return;
                    }
                }
            }
            if (cancelled) {
                parent.tell(new JobRunner.TaskCancelled(taskId));
            }
        });
    }

    private void persist(int attempts, TaskResult result) {
        try {
            Task task = new Task();
            task.setId(taskId);
            task.setJobId(jobId);
            task.setParams(this.params);
            task.setAttempts(attempts);
            task.setLastResult(result.status());
            task.setLastError(result.message());

            switch (result.status()) {
                case SUCCESS -> {
                    task.setStatus(TaskStatus.SUCCESS);
                    task.setAvailableAt(Instant.now());
                }
                case FAILED -> {
                    task.setStatus(TaskStatus.RUNNING);
                    task.setAvailableAt(Instant.now());
                }
                case IN_PROGRESS -> {
                    task.setStatus(TaskStatus.PENDING);
                    Instant retryAt = result.retryAfter() != null
                            ? result.retryAfter()
                            : Instant.now().plusMillis(config.backoffInitialMs());
                    task.setAvailableAt(retryAt);
                }
            }
            taskStore.save(task);
        } catch (Exception e) {
            LOGGER.error("Failed to persist task state: taskId={}", taskId, e);
        }
    }

    public void cancel() {
        cancelled = true;
        Thread t = taskThread;
        if (t != null) {
            LockSupport.unpark(t);
        }
    }

    public boolean join(long timeoutMs) {
        Thread t = taskThread;
        if (t != null) {
            try {
                t.join(timeoutMs);
                return !t.isAlive();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        return true;
    }

    private TaskResult executeHttp() {
        return taskInvoker.invoke(endpoint, config.taskPath(), config.httpMethod(),
                params, config.timeoutMs());
    }

    private void sleepBackoff(int attempt) {
        long ms = switch (config.backoffStrategy()) {
            case FIXED -> config.backoffInitialMs();
            case EXPONENTIAL -> {
                long val = config.backoffInitialMs() * (long) Math.pow(2, Math.max(attempt - 1, 0));
                yield Math.min(val, config.backoffMaxMs());
            }
        };
        LockSupport.parkNanos(ms * 1_000_000);
    }

}
