package ravenworks.fizz.engine.runtime;

import ravenworks.fizz.engine.config.SchedulerConfig;
import ravenworks.fizz.engine.discovery.JobTypeRegistry;
import ravenworks.fizz.engine.discovery.ServiceDiscovery;
import ravenworks.fizz.engine.enums.JobStatus;
import ravenworks.fizz.engine.enums.TaskStatus;
import ravenworks.fizz.engine.invoker.NotificationInvoker;
import ravenworks.fizz.engine.invoker.TaskInvoker;
import ravenworks.fizz.engine.model.ActiveJob;
import ravenworks.fizz.engine.model.Job;
import ravenworks.fizz.engine.store.ActiveJobStore;
import ravenworks.fizz.engine.store.JobStore;
import ravenworks.fizz.engine.store.SchedulerLockStore;
import ravenworks.fizz.engine.store.TaskStore;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


public class Scheduler extends Actor {

    private final SchedulerLockStore lockStore;
    private final ActiveJobStore activeJobStore;
    private final JobStore jobStore;
    private final TaskStore taskStore;
    private final SchedulerConfig config;
    private final JobTypeRegistry jobTypeRegistry;
    private final ServiceDiscovery serviceDiscovery;
    private final NotificationDispatcher notificationDispatcher;
    private final TaskInvoker taskInvoker;
    private final NotificationInvoker notificationInvoker;

    private final Map<String, SchedulingGroup> groups = new ConcurrentHashMap<>();
    private volatile boolean dispatchable;

    public Scheduler(SchedulerLockStore lockStore, ActiveJobStore activeJobStore,
                     JobStore jobStore, TaskStore taskStore, SchedulerConfig config,
                     JobTypeRegistry jobTypeRegistry, ServiceDiscovery serviceDiscovery,
                     NotificationDispatcher notificationDispatcher,
                     TaskInvoker taskInvoker, NotificationInvoker notificationInvoker) {
        this.lockStore = lockStore;
        this.activeJobStore = activeJobStore;
        this.jobStore = jobStore;
        this.taskStore = taskStore;
        this.config = config;
        this.jobTypeRegistry = jobTypeRegistry;
        this.serviceDiscovery = serviceDiscovery;
        this.notificationDispatcher = notificationDispatcher;
        this.taskInvoker = taskInvoker;
        this.notificationInvoker = notificationInvoker;
    }

    @Override
    protected String componentName() {
        return "fizz-scheduler";
    }

    @Override
    protected long timeoutMs() {
        return 60000;
    }

    @Override
    protected void handle(Object message) {
        switch (message) {
            case SchedulerCoordinator.CoordinatorMsg.LockAcquired m -> handleLockAcquired();
            case SchedulerCoordinator.CoordinatorMsg.LockLost m -> handleLockLost();
            case SchedulerMsg.JobSubmitted m -> handleJobSubmitted(m);
            case SchedulerMsg.JobCompleted m -> handleJobCompleted(m);
            case SchedulerMsg.CancelJob m -> handleCancel(m);
            case SchedulerMsg.GroupDead m -> handleGroupDead(m);
            default -> {
            }
        }
    }

    private void handleLockAcquired() {
        dispatchable = true;
        LOGGER.info("Lock acquired, starting recovery...");
        doRecovery();
    }

    private void handleLockLost() {
        dispatchable = false;
        LOGGER.warn("Lock lost, shutting down all groups");
        groups.values().forEach(SchedulingGroup::shutdown);
        groups.clear();
    }

    private void handleJobSubmitted(SchedulerMsg.JobSubmitted m) {
        if (!dispatchable) return;
        String key = key(m.tenantId(), m.jobType());
        SchedulingGroup sg = groups.computeIfAbsent(key, k -> {
            int concurrency = resolveJobConcurrency(m.jobType());
            SchedulingGroup g = new SchedulingGroup(this, m.tenantId(), m.jobType(),
                    concurrency, activeJobStore, jobStore, taskStore,
                    jobTypeRegistry, serviceDiscovery, notificationDispatcher, taskInvoker, notificationInvoker);
            g.start();
            LOGGER.info("Created scheduling group: {}", key);
            return g;
        });
        sg.tell(new SchedulingGroup.SubmitJob(m.jobId(), m.scheduledAt()));
    }

    private void handleJobCompleted(SchedulerMsg.JobCompleted m) {
        notificationDispatcher.notify(m.jobId(), m.status(), jobStore,
                jobTypeRegistry, serviceDiscovery, notificationInvoker);
    }

    private void handleCancel(SchedulerMsg.CancelJob m) {
        String key = key(m.tenantId(), m.jobType());
        SchedulingGroup sg = groups.get(key);
        if (sg != null) {
            sg.tell(new SchedulingGroup.CancelJob(m.jobId()));
        }
    }

    private void handleGroupDead(SchedulerMsg.GroupDead m) {
        String key = key(m.tenantId(), m.jobType());
        groups.remove(key);
        LOGGER.info("Group removed (idle): {}", key);
    }

    @Override
    public void shutdown() {
        dispatchable = false;
        running = false;
        Thread t = thread;
        if (t != null) {
            java.util.concurrent.locks.LockSupport.unpark(t);
        }
        groups.values().forEach(SchedulingGroup::shutdown);
    }

    @Override
    public void awaitTermination(long timeoutMs) {
        for (SchedulingGroup sg : groups.values()) {
            sg.awaitTermination(timeoutMs);
        }
        joinSelf(30_000);
        taskStore.recoverDanglingTasks(InstanceId.VALUE);
        lockStore.release(InstanceId.VALUE);
        LOGGER.info("Scheduler stopped");
    }

    private void doRecovery() {
        try {
            taskStore.recoverDanglingTasks(InstanceId.VALUE);
            notificationDispatcher.recoverPending(jobStore, jobTypeRegistry, serviceDiscovery, notificationInvoker);
        } catch (Exception e) {
            LOGGER.error("Recovery step 1 failed", e);
        }

        List<ActiveJob> all = activeJobStore.findByStatusIn(
                List.of(JobStatus.PENDING, JobStatus.RUNNING));

        for (ActiveJob aj : all) {
            Job job = jobStore.findById(aj.getId());
            if (job == null) {
                activeJobStore.delete(aj.getId());
                continue;
            }
            if (job.getStatus() != JobStatus.PENDING) {
                try {
                    int completed = taskStore.countByJobIdAndStatus(job.getId(), TaskStatus.SUCCESS);
                    int failed = taskStore.countByJobIdAndStatus(job.getId(), TaskStatus.FAILED);
                    taskStore.resetRunningToPendingByJobId(job.getId());
                    jobStore.updateStatusAndCounts(job.getId(), JobStatus.PENDING, completed, failed);
                    LOGGER.info("Recovered job {}: completed={}, failed={}", job.getId(), completed, failed);
                } catch (Exception e) {
                    LOGGER.error("Failed to recover job {}", job.getId(), e);
                }
            }
        }

        Map<String, List<Job>> byGroup = new LinkedHashMap<>();
        for (ActiveJob aj : all) {
            Job job = jobStore.findById(aj.getId());
            if (job == null) {
                activeJobStore.delete(aj.getId());
                continue;
            }
            String k = key(job.getTenantId(), job.getJobType());
            byGroup.computeIfAbsent(k, x -> new ArrayList<>()).add(job);
        }

        for (var entry : byGroup.entrySet()) {
            String[] parts = entry.getKey().split(":", 2);
            String tenantId = parts[0];
            String jobType = parts[1];
            int concurrency = resolveJobConcurrency(jobType);

            SchedulingGroup sg = new SchedulingGroup(this, tenantId, jobType,
                    concurrency, activeJobStore, jobStore, taskStore,
                    jobTypeRegistry, serviceDiscovery, notificationDispatcher, taskInvoker, notificationInvoker);
            groups.put(entry.getKey(), sg);
            sg.start();

            for (Job j : entry.getValue()) {
                sg.tell(new SchedulingGroup.SubmitJob(j.getId(), j.getScheduledAt()));
            }
        }

        LOGGER.info("Recovery complete: {} groups, {} active jobs", byGroup.size(), all.size());
    }

    private int resolveJobConcurrency(String jobType) {
        try {
            return jobTypeRegistry.get(jobType).jobConcurrency();
        } catch (Exception e) {
            return 10;
        }
    }

    private static String key(String tenantId, String jobType) {
        return tenantId + ":" + jobType;
    }

    public void submitJob(String jobId, String tenantId, String jobType, Instant scheduledAt) {
        tell(new SchedulerMsg.JobSubmitted(jobId, tenantId, jobType, scheduledAt));
    }

    public void cancelJob(String jobId, String tenantId, String jobType) {
        tell(new SchedulerMsg.CancelJob(jobId, tenantId, jobType));
    }

    public String getInstanceId() {
        return InstanceId.VALUE;
    }

    public sealed interface SchedulerMsg {

        record JobSubmitted(String jobId, String tenantId, String jobType, Instant scheduledAt)
                implements SchedulerMsg {

        }


        record JobCompleted(String jobId, JobStatus status, String tenantId, String jobType)
                implements SchedulerMsg {

        }


        record CancelJob(String jobId, String tenantId, String jobType)
                implements SchedulerMsg {

        }


        record GroupDead(String tenantId, String jobType)
                implements SchedulerMsg {

        }

    }

}
