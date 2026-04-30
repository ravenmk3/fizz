package ravenworks.fizz.engine.runtime;

import ravenworks.fizz.engine.discovery.JobTypeConfig;
import ravenworks.fizz.engine.discovery.JobTypeRegistry;
import ravenworks.fizz.engine.discovery.ServiceDiscovery;
import ravenworks.fizz.engine.discovery.ServiceEndpoint;
import ravenworks.fizz.engine.enums.JobStatus;
import ravenworks.fizz.engine.enums.TaskStatus;
import ravenworks.fizz.engine.invoker.TaskInvoker;
import ravenworks.fizz.engine.model.Job;
import ravenworks.fizz.engine.model.Task;
import ravenworks.fizz.engine.store.JobStore;
import ravenworks.fizz.engine.store.TaskStore;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;


public class JobRunner extends Actor {

    private final Actor parent;
    private final TaskStore taskStore;
    private final JobStore jobStore;
    private final ServiceDiscovery serviceDiscovery;
    private final JobTypeRegistry jobTypeRegistry;
    private final TaskInvoker taskInvoker;

    private final String jobId;
    private final String jobType;
    private final String serviceName;
    private final int taskConcurrency;
    private final int totalCount;
    private final int maxAttempts;
    private int pendingTaskCount;
    private AtomicInteger completedCount;
    private AtomicInteger failedCount;
    private volatile boolean cancelled;

    private final Map<String, TaskRunner> runningTasks = new ConcurrentHashMap<>();
    private final PriorityQueue<DelayedEntry> delayedTasks = new PriorityQueue<>();
    private final Set<String> delayedTaskIds = ConcurrentHashMap.newKeySet();

    public JobRunner(Actor parent, TaskStore taskStore, JobStore jobStore,
                     ServiceDiscovery serviceDiscovery, JobTypeRegistry jobTypeRegistry,
                     TaskInvoker taskInvoker, Job job) {
        this.parent = parent;
        this.taskStore = taskStore;
        this.jobStore = jobStore;
        this.serviceDiscovery = serviceDiscovery;
        this.jobTypeRegistry = jobTypeRegistry;
        this.taskInvoker = taskInvoker;
        this.jobId = job.getId();
        this.jobType = job.getJobType();
        this.serviceName = job.getServiceName();
        this.taskConcurrency = job.getTaskConcurrency();
        this.totalCount = job.getTotalCount();
        this.maxAttempts = job.getMaxAttempts();
        this.completedCount = new AtomicInteger(job.getCompletedCount());
        this.failedCount = new AtomicInteger(job.getFailedCount());
    }

    @Override
    protected String componentName() {
        return "job-" + jobId;
    }

    @Override
    protected void beforeLoop() {
        this.pendingTaskCount = taskStore.countByJobIdAndStatus(jobId, TaskStatus.PENDING);
        LOGGER.info("JobRunner activated: jobId={}, taskConcurrency={}, totalCount={}, pendingTaskCount={}, recovered={}/{}",
                jobId, taskConcurrency, totalCount, pendingTaskCount, completedCount.get(), failedCount.get());
        trySchedule();
    }

    @Override
    protected void handle(Object message) {
        switch (message) {
            case TaskSucceeded m -> handleTaskSucceeded(m);
            case TaskFailed m -> handleTaskFailed(m);
            case TaskDelayed m -> handleTaskDelayed(m);
            case TaskCancelled m -> handleTaskCancelled(m);
            case Cancel m -> handleCancel();
            default -> {
            }
        }
    }

    private void handleTaskSucceeded(TaskSucceeded m) {
        runningTasks.remove(m.taskId());
        completedCount.incrementAndGet();
        jobStore.incrementCompletedCount(jobId);
        trySchedule();
    }

    private void handleTaskFailed(TaskFailed m) {
        runningTasks.remove(m.taskId());
        failedCount.incrementAndGet();
        jobStore.incrementFailedCount(jobId);
        trySchedule();
    }

    private void handleTaskDelayed(TaskDelayed m) {
        runningTasks.remove(m.taskId());
        Task task = taskStore.findById(m.taskId());
        if (task != null) {
            Instant availableAt = task.getAvailableAt() != null
                    ? task.getAvailableAt()
                    : Instant.now();
            delayedTasks.add(new DelayedEntry(m.taskId(), availableAt));
            delayedTaskIds.add(m.taskId());
        }
        trySchedule();
    }

    private void handleTaskCancelled(TaskCancelled m) {
        runningTasks.remove(m.taskId());
        trySchedule();
    }

    private void handleCancel() {
        cancelled = true;
        taskStore.cancelPendingByJobId(jobId);
        runningTasks.values().forEach(TaskRunner::cancel);
        LOGGER.info("JobRunner cancelled: jobId={}", jobId);
    }

    @Override
    protected void onIdle() {
        trySchedule();
    }

    private void trySchedule() {
        if (cancelled) {
            if (runningTasks.isEmpty()) {
                parent.tell(new SchedulingGroup.JobCancelled(jobId));
                running = false;
                LOGGER.info("JobRunner cancelled and drained: jobId={}", jobId);
            }
            return;
        }

        if (pendingTaskCount <= 0 && delayedTasks.isEmpty() && runningTasks.isEmpty()) {
            JobStatus finalStatus = failedCount.get() > 0 ? JobStatus.FAILED : JobStatus.SUCCESS;
            jobStore.updateStatus(jobId, finalStatus);
            if (finalStatus == JobStatus.SUCCESS) {
                parent.tell(new SchedulingGroup.JobSucceeded(jobId));
            } else {
                parent.tell(new SchedulingGroup.JobFailed(jobId));
            }
            running = false;
            LOGGER.info("JobRunner completed: jobId={}, status={}, completed={}/{}, failed={}/{}",
                    jobId, finalStatus, completedCount.get(), totalCount, failedCount.get(), totalCount);
            return;
        }

        dispatchFromDatabase();
        dispatchFromDelayed();
    }

    private void dispatchFromDatabase() {
        while (runningTasks.size() < taskConcurrency && pendingTaskCount > 0) {
            int limit = taskConcurrency - runningTasks.size();
            List<Task> pending = taskStore.findDispatchable(jobId, limit, Instant.now());
            if (pending.isEmpty()) break;

            for (Task task : pending) {
                if (runningTasks.containsKey(task.getId())) continue;
                if (delayedTaskIds.contains(task.getId())) continue;

                task.setStatus(TaskStatus.RUNNING);
                task.setInstanceId(InstanceId.VALUE);
                taskStore.save(task);

                pendingTaskCount--;
                launchTask(task);

                if (runningTasks.size() >= taskConcurrency) break;
            }
        }
    }

    private void dispatchFromDelayed() {
        while (runningTasks.size() < taskConcurrency && !delayedTasks.isEmpty()) {
            DelayedEntry entry = delayedTasks.peek();
            if (entry == null || entry.availableAt.isAfter(Instant.now())) break;

            delayedTasks.poll();
            delayedTaskIds.remove(entry.taskId());

            if (runningTasks.containsKey(entry.taskId())) continue;

            Task task = taskStore.findById(entry.taskId());
            if (task == null) continue;

            task.setStatus(TaskStatus.RUNNING);
            task.setInstanceId(InstanceId.VALUE);
            taskStore.save(task);

            launchTask(task);
        }
    }

    private void launchTask(Task task) {
        JobTypeConfig config;
        ServiceEndpoint endpoint;
        try {
            config = jobTypeRegistry.get(jobType);
            endpoint = serviceDiscovery.resolve(serviceName);
        } catch (Exception e) {
            LOGGER.error("Failed to resolve config/endpoint for job {} task {}", jobId, task.getId(), e);
            return;
        }

        TaskRunner runner = new TaskRunner(task, maxAttempts, config, endpoint, taskInvoker, this, taskStore);
        runningTasks.put(task.getId(), runner);
        runner.execute();
    }

    @Override
    public void shutdown() {
        super.shutdown();
        runningTasks.values().forEach(TaskRunner::cancel);
    }

    @Override
    public void awaitTermination(long timeoutMs) {
        for (TaskRunner tc : runningTasks.values()) {
            tc.join(timeoutMs);
        }
        joinSelf(30_000);
    }

    public record TaskSucceeded(String taskId) {

    }


    public record TaskFailed(String taskId) {

    }


    public record TaskDelayed(String taskId) {

    }


    public record TaskCancelled(String taskId) {

    }


    public record Cancel() {

    }


    private record DelayedEntry(String taskId, Instant availableAt) implements Comparable<DelayedEntry> {

        @Override
        public int compareTo(DelayedEntry o) {
            return this.availableAt.compareTo(o.availableAt);
        }

    }

}
