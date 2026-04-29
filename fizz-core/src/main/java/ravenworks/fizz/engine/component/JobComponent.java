package ravenworks.fizz.engine.component;

import lombok.extern.slf4j.Slf4j;
import ravenworks.fizz.engine.discovery.JobTypeConfig;
import ravenworks.fizz.engine.discovery.JobTypeRegistry;
import ravenworks.fizz.engine.discovery.ServiceDiscovery;
import ravenworks.fizz.engine.discovery.ServiceEndpoint;
import ravenworks.fizz.engine.enums.JobStatus;
import ravenworks.fizz.engine.enums.TaskResultStatus;
import ravenworks.fizz.engine.enums.TaskStatus;
import ravenworks.fizz.engine.model.Task;
import ravenworks.fizz.engine.store.JobStore;
import ravenworks.fizz.engine.store.TaskStore;

import java.net.http.HttpClient;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public class JobComponent extends Component {

    private static final AtomicInteger SEQ = new AtomicInteger(0);
    private final int seq = SEQ.incrementAndGet();

    private final Component parent;
    private final TaskStore taskStore;
    private final JobStore jobStore;
    private final ServiceDiscovery serviceDiscovery;
    private final JobTypeRegistry jobTypeRegistry;
    private final HttpClient httpClient;
    private final ConcurrentHashMap<String, TaskComponent> runningTasks = new ConcurrentHashMap<>();

    private String jobId;
    private String jobType;
    private String serviceName;
    private String instanceId;
    private Semaphore taskSlots;
    private int totalCount;
    private int maxAttempts;
    private AtomicInteger completedCount = new AtomicInteger(0);
    private AtomicInteger failedCount = new AtomicInteger(0);
    private volatile boolean cancelled;
    private boolean activated;

    public JobComponent(Component parent, TaskStore taskStore, JobStore jobStore,
                        ServiceDiscovery serviceDiscovery, JobTypeRegistry jobTypeRegistry,
                        HttpClient httpClient, String instanceId) {
        this.parent = parent;
        this.taskStore = taskStore;
        this.jobStore = jobStore;
        this.serviceDiscovery = serviceDiscovery;
        this.jobTypeRegistry = jobTypeRegistry;
        this.httpClient = httpClient;
        this.instanceId = instanceId;
    }

    @Override
    protected String componentName() {
        return "job-" + (jobId != null ? jobId : "#" + seq);
    }

    @Override
    protected void handle(Object message) {
        switch (message) {
            case Activate m -> handleActivate(m);
            case TaskCompleted m -> handleTaskCompleted(m);
            case TaskCancelled m -> handleTaskCancelled(m);
            case Cancel m -> handleCancel();
            default -> {}
        }
    }

    private void handleActivate(Activate m) {
        this.jobId = m.jobId();
        this.jobType = m.jobType();
        this.serviceName = m.serviceName();
        this.totalCount = m.totalCount();
        this.maxAttempts = m.maxAttempts();
        this.completedCount.set(m.completedCount());
        this.failedCount.set(m.failedCount());
        this.taskSlots = new Semaphore(m.taskConcurrency());
        this.activated = true;
        Thread.currentThread().setName(componentName());
        log.info("Job activated: jobId={}, taskConcurrency={}, totalCount={}, recovered={}/{}",
                jobId, m.taskConcurrency(), totalCount, m.completedCount(), m.failedCount());
        tryDispatch();
    }

    private void handleTaskCompleted(TaskCompleted m) {
        switch (m.status()) {
            case SUCCESS -> {
                runningTasks.remove(m.taskId());
                taskSlots.release();
                completedCount.incrementAndGet();
                jobStore.incrementCompletedCount(jobId);
            }
            case FAILED -> {
                runningTasks.remove(m.taskId());
                taskSlots.release();
                failedCount.incrementAndGet();
                jobStore.incrementFailedCount(jobId);
            }
            case IN_PROGRESS -> {
            }
        }

        checkCompletion();
        tryDispatch();
    }

    private void handleTaskCancelled(TaskCancelled m) {
        runningTasks.remove(m.taskId());
        taskSlots.release();
        checkCompletion();
    }

    private void handleCancel() {
        cancelled = true;
        taskStore.cancelPendingByJobId(jobId);
        runningTasks.values().forEach(TaskComponent::cancel);
        log.info("Job cancelled: jobId={}", jobId);
    }

    private void tryDispatch() {
        if (!activated || cancelled) return;

        while (taskSlots.tryAcquire()) {
            Task task = fetchPendingTask();
            if (task == null) {
                taskSlots.release();
                return;
            }

            task.setStatus(TaskStatus.RUNNING);
            task.setInstanceId(instanceId);
            taskStore.save(task);

            JobTypeConfig config;
            ServiceEndpoint endpoint;
            try {
                config = jobTypeRegistry.get(jobType);
                endpoint = serviceDiscovery.resolve(serviceName);
            } catch (Exception e) {
                log.error("Failed to resolve config/endpoint for job {}", jobId, e);
                taskSlots.release();
                return;
            }

            TaskComponent tc = new TaskComponent();
            runningTasks.put(task.getId(), tc);
            tc.setup(task.getId(), jobId, task.getParams(), maxAttempts,
                    config, endpoint, httpClient, this, taskStore);
            tc.execute();
        }
    }

    private Task fetchPendingTask() {
        List<Task> pending = taskStore.findDispatchable(jobId, 1, Instant.now());
        return pending.isEmpty() ? null : pending.get(0);
    }

    private void checkCompletion() {
        if (!activated) return;
        int finished = completedCount.get() + failedCount.get();
        if (finished >= totalCount) {
            JobStatus finalStatus;
            if (cancelled) {
                finalStatus = JobStatus.CANCELLED;
            } else if (completedCount.get() >= totalCount) {
                finalStatus = JobStatus.SUCCESS;
            } else {
                finalStatus = JobStatus.FAILED;
            }
            jobStore.updateStatus(jobId, finalStatus);
            parent.tell(new TenantJobScheduler.JobCompleted(jobId, finalStatus, completedCount.get(), failedCount.get()));
            running = false;
            log.info("Job completed: jobId={}, status={}, completed={}/{}, failed={}/{}",
                    jobId, finalStatus, completedCount.get(), totalCount, failedCount.get(), totalCount);
        }
    }

    @Override
    public void shutdown() {
        super.shutdown();
        runningTasks.values().forEach(TaskComponent::cancel);
    }

    @Override
    public void awaitTermination(long timeoutMs) {
        for (TaskComponent tc : runningTasks.values()) {
            tc.join(timeoutMs);
        }
        joinSelf(30_000);
    }

    public record Activate(String jobId, String jobType, String serviceName,
                           int taskConcurrency, int totalCount, int maxAttempts,
                           int completedCount, int failedCount) {}

    public record TaskCompleted(String taskId, TaskResultStatus status) {}

    public record TaskCancelled(String taskId) {}

    public record Cancel() {}
}
