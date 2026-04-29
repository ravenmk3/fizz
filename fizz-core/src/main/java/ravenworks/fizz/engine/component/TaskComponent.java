package ravenworks.fizz.engine.component;

import lombok.extern.slf4j.Slf4j;
import ravenworks.fizz.common.json.JsonUtils;
import ravenworks.fizz.common.model.ApiResponse;
import ravenworks.fizz.engine.discovery.JobTypeConfig;
import ravenworks.fizz.engine.discovery.ServiceEndpoint;
import ravenworks.fizz.engine.enums.TaskResultStatus;
import ravenworks.fizz.engine.enums.TaskStatus;
import ravenworks.fizz.engine.model.ExecutionResult;
import ravenworks.fizz.engine.model.Task;
import ravenworks.fizz.engine.model.TaskResult;
import ravenworks.fizz.engine.store.TaskStore;

import com.fasterxml.jackson.core.type.TypeReference;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.locks.LockSupport;

@Slf4j
public class TaskComponent {

    private String taskId;
    private String jobId;
    private String params;
    private int maxAttempts;
    private JobTypeConfig config;
    private ServiceEndpoint endpoint;
    private HttpClient httpClient;
    private Component parent;
    private TaskStore taskStore;

    private volatile boolean cancelled;
    private Thread taskThread;

    public void setup(String taskId, String jobId, String params, int maxAttempts,
                       JobTypeConfig config, ServiceEndpoint endpoint,
                       HttpClient httpClient, Component parent, TaskStore taskStore) {
        this.taskId = taskId;
        this.jobId = jobId;
        this.params = params;
        this.maxAttempts = maxAttempts;
        this.config = config;
        this.endpoint = endpoint;
        this.httpClient = httpClient;
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
                        parent.tell(new JobComponent.TaskCompleted(taskId, TaskResultStatus.SUCCESS));
                        return;
                    }
                    case FAILED -> {
                        attempts++;
                        if (maxAttempts != -1 && attempts >= maxAttempts) {
                            parent.tell(new JobComponent.TaskCompleted(taskId, TaskResultStatus.FAILED));
                            return;
                        }
                        sleepBackoff(attempts);
                    }
                    case IN_PROGRESS -> {
                        parent.tell(new JobComponent.TaskCompleted(taskId, TaskResultStatus.IN_PROGRESS));
                        Instant retryAfter = result.retryAfter() != null
                                ? result.retryAfter()
                                : Instant.now().plusMillis(config.backoffInitialMs());
                        long sleepMs = Duration.between(Instant.now(), retryAfter).toMillis();
                        if (sleepMs > 0) {
                            LockSupport.parkNanos(sleepMs * 1_000_000);
                        }
                    }
                }
            }
            if (cancelled) {
                parent.tell(new JobComponent.TaskCancelled(taskId));
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
            log.error("Failed to persist task state: taskId={}", taskId, e);
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
        try {
            URI uri = URI.create(endpoint.baseUrl() + config.taskPath());
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .method(config.httpMethod(), HttpRequest.BodyPublishers.ofString(params))
                    .timeout(Duration.ofMillis(config.timeoutMs()))
                    .header("Content-Type", "application/json")
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return parseResponse(response);
        } catch (Exception e) {
            log.error("Task HTTP failed: taskId={}, error={}", taskId, e.getMessage());
            return TaskResult.failed(e.getMessage());
        }
    }

    private TaskResult parseResponse(HttpResponse<String> response) {
        String body = response.body();
        if (body == null || body.isBlank()) {
            return TaskResult.failed("Empty response body");
        }
        try {
            ApiResponse<ExecutionResult> resp = JsonUtils.fromJson(body,
                    new TypeReference<ApiResponse<ExecutionResult>>() {});
            if (resp.getCode() != 0) {
                String message = resp.getMessage();
                return TaskResult.failed(message != null ? message : "Remote error code: " + resp.getCode());
            }
            ExecutionResult data = resp.getData();
            if (data == null) {
                return TaskResult.success();
            }
            TaskResultStatus status = TaskResultStatus.valueOf(data.getStatus());
            Instant retryAfter = (data.getRetryAfter() != null && !data.getRetryAfter().equals("null"))
                    ? Instant.parse(data.getRetryAfter()) : null;
            return new TaskResult(status, data.getMessage(), retryAfter);
        } catch (Exception e) {
            return TaskResult.failed("Failed to parse response: " + e.getMessage());
        }
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
