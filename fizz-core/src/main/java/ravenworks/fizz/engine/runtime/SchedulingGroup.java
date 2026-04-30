package ravenworks.fizz.engine.runtime;

import ravenworks.fizz.engine.discovery.JobTypeRegistry;
import ravenworks.fizz.engine.discovery.ServiceDiscovery;
import ravenworks.fizz.engine.enums.JobStatus;
import ravenworks.fizz.engine.invoker.NotificationInvoker;
import ravenworks.fizz.engine.invoker.TaskInvoker;
import ravenworks.fizz.engine.model.Job;
import ravenworks.fizz.engine.store.ActiveJobStore;
import ravenworks.fizz.engine.store.JobStore;
import ravenworks.fizz.engine.store.TaskStore;

import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;


public class SchedulingGroup extends Actor {

    private static final int MAX_IDLE_COUNT = 3;

    private final Actor parent;
    private final String tenantId;
    private final String jobType;
    private final int jobConcurrency;
    private final ActiveJobStore activeJobStore;
    private final JobStore jobStore;
    private final TaskStore taskStore;
    private final JobTypeRegistry jobTypeRegistry;
    private final ServiceDiscovery serviceDiscovery;
    private final NotificationDispatcher notificationDispatcher;
    private final TaskInvoker taskInvoker;
    private final NotificationInvoker notificationInvoker;

    private final Semaphore jobSlots;
    private final ConcurrentLinkedQueue<PendingEntry> pendingFifo = new ConcurrentLinkedQueue<>();
    private final Map<String, JobRunner> runningJobs = new ConcurrentHashMap<>();
    private final Set<String> runningQueueingKeys = ConcurrentHashMap.newKeySet();
    private int idleCount;
    private volatile boolean selfTerminated;

    public SchedulingGroup(Actor parent, String tenantId, String jobType,
                           int jobConcurrency,
                           ActiveJobStore activeJobStore, JobStore jobStore,
                           TaskStore taskStore, JobTypeRegistry jobTypeRegistry,
                           ServiceDiscovery serviceDiscovery,
                           NotificationDispatcher notificationDispatcher,
                           TaskInvoker taskInvoker, NotificationInvoker notificationInvoker) {
        this.parent = parent;
        this.tenantId = tenantId;
        this.jobType = jobType;
        this.jobConcurrency = jobConcurrency;
        this.activeJobStore = activeJobStore;
        this.jobStore = jobStore;
        this.taskStore = taskStore;
        this.jobTypeRegistry = jobTypeRegistry;
        this.serviceDiscovery = serviceDiscovery;
        this.notificationDispatcher = notificationDispatcher;
        this.taskInvoker = taskInvoker;
        this.notificationInvoker = notificationInvoker;
        this.jobSlots = new Semaphore(jobConcurrency);
    }

    @Override
    protected String componentName() {
        return "group-" + tenantId + "-" + jobType;
    }

    @Override
    protected void handle(Object message) {
        switch (message) {
            case SubmitJob m -> handleSubmit(m);
            case JobSucceeded m -> handleJobCompleted(m.jobId(), JobStatus.SUCCESS);
            case JobFailed m -> handleJobCompleted(m.jobId(), JobStatus.FAILED);
            case JobCancelled m -> handleJobCompleted(m.jobId(), JobStatus.CANCELLED);
            case CancelJob m -> handleCancel(m);
            case Exit m -> handleExit();
            default -> {
            }
        }
    }

    private void handleSubmit(SubmitJob m) {
        pendingFifo.add(new PendingEntry(m.jobId(), m.scheduledAt()));
        LOGGER.debug("{}: job submitted: jobId={}", componentName(), m.jobId());
        idleCount = 0;
        trySchedule();
    }

    private void handleJobCompleted(String jobId, JobStatus status) {
        JobRunner jr = runningJobs.remove(jobId);
        if (jr != null) {
            Job job = jobStore.findById(jobId);
            if (job != null && job.getQueueingKey() != null) {
                runningQueueingKeys.remove(job.getQueueingKey());
            }
            jr.shutdown();
        }
        activeJobStore.delete(jobId);
        jobSlots.release();
        LOGGER.info("{}: job {} completed with status={}", componentName(),
                jobId, status);

        parent.tell(new Scheduler.SchedulerMsg.JobCompleted(
                jobId, status, tenantId, jobType));
        idleCount = 0;
        trySchedule();
    }

    private void handleCancel(CancelJob m) {
        JobRunner jr = runningJobs.get(m.jobId());
        if (jr != null) {
            jr.tell(new JobRunner.Cancel());
        } else {
            pendingFifo.removeIf(e -> e.jobId.equals(m.jobId()));
        }
    }

    private void handleExit() {
        selfTerminated = true;
        parent.tell(new Scheduler.SchedulerMsg.GroupDead(tenantId, jobType));
        running = false;
        LOGGER.info("{} exiting due to idle", componentName());
    }

    @Override
    public void shutdown() {
        super.shutdown();
        runningJobs.values().forEach(JobRunner::shutdown);
    }

    @Override
    public void awaitTermination(long timeoutMs) {
        for (JobRunner jr : runningJobs.values()) {
            jr.awaitTermination(timeoutMs);
        }
        joinSelf(30_000);
        if (!selfTerminated) {
            parent.tell(new Scheduler.SchedulerMsg.GroupDead(tenantId, jobType));
        }
        LOGGER.info("{} terminated", componentName());
    }

    @Override
    protected void onIdle() {
        if (!pendingFifo.isEmpty() || !runningJobs.isEmpty()) {
            trySchedule();
            return;
        }

        idleCount++;
        LOGGER.debug("{} idle count: {}/{}", componentName(), idleCount, MAX_IDLE_COUNT);
        if (idleCount >= MAX_IDLE_COUNT) {
            tell(new Exit());
        }
    }

    private void trySchedule() {
        while (jobSlots.tryAcquire()) {
            PendingEntry next = pollEligible();
            if (next == null) {
                jobSlots.release();
                return;
            }
            activateJob(next);
        }
    }

    private PendingEntry pollEligible() {
        Iterator<PendingEntry> it = pendingFifo.iterator();
        while (it.hasNext()) {
            PendingEntry entry = it.next();

            if (entry.scheduledAt != null && entry.scheduledAt.isAfter(Instant.now())) {
                continue;
            }

            Job job = jobStore.findById(entry.jobId);
            if (job == null) {
                continue;
            }

            String qk = job.getQueueingKey();
            if (qk != null && runningQueueingKeys.contains(qk)) {
                continue;
            }

            it.remove();
            return entry;
        }
        return null;
    }

    private void activateJob(PendingEntry entry) {
        Job job = jobStore.findById(entry.jobId);
        if (job == null) return;
        if (job.getStatus() != JobStatus.PENDING) {
            jobSlots.release();
            return;
        }
        launchJob(job);
    }

    private void launchJob(Job job) {
        jobStore.updateStatusAndInstanceId(job.getId(), JobStatus.RUNNING, InstanceId.VALUE);
        activeJobStore.updateStatus(job.getId(), JobStatus.RUNNING);
        notifyJobStatus(job.getId(), JobStatus.RUNNING);

        String qk = job.getQueueingKey();
        if (qk != null) {
            runningQueueingKeys.add(qk);
        }

        JobRunner jr = new JobRunner(this, taskStore, jobStore,
                serviceDiscovery, jobTypeRegistry, taskInvoker, job);
        runningJobs.put(job.getId(), jr);
        jr.start();

        LOGGER.info("{}: job activated: jobId={}", componentName(), job.getId());
    }

    private void notifyJobStatus(String jobId, JobStatus status) {
        try {
            notificationDispatcher.notify(jobId, status, jobStore,
                    jobTypeRegistry, serviceDiscovery, notificationInvoker);
        } catch (Exception e) {
            LOGGER.error("{}: failed to notify job status for {}", componentName(), jobId, e);
        }
    }

    public void tryActivate() {
        trySchedule();
    }

    public record SubmitJob(String jobId, Instant scheduledAt) {

    }


    public record CancelJob(String jobId) {

    }


    public record JobSucceeded(String jobId) {

    }


    public record JobFailed(String jobId) {

    }


    public record JobCancelled(String jobId) {

    }


    public record Exit() {

    }


    private static class PendingEntry {

        final String jobId;
        final Instant scheduledAt;

        PendingEntry(String jobId, Instant scheduledAt) {
            this.jobId = jobId;
            this.scheduledAt = scheduledAt;
        }

    }

}
