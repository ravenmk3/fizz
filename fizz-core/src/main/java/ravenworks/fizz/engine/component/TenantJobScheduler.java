package ravenworks.fizz.engine.component;

import lombok.extern.slf4j.Slf4j;
import ravenworks.fizz.engine.discovery.JobTypeRegistry;
import ravenworks.fizz.engine.discovery.ServiceDiscovery;
import ravenworks.fizz.engine.enums.JobStatus;
import ravenworks.fizz.engine.model.Job;
import ravenworks.fizz.engine.store.ActiveJobStore;
import ravenworks.fizz.engine.store.JobStore;
import ravenworks.fizz.engine.store.TaskStore;

import java.net.http.HttpClient;
import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;

@Slf4j
public class TenantJobScheduler extends Component {

    private final Component parent;
    private final String tenantId;
    private final String jobType;
    private final int jobConcurrency;
    private final String instanceId;
    private final ActiveJobStore activeJobStore;
    private final JobStore jobStore;
    private final TaskStore taskStore;
    private final JobTypeRegistry jobTypeRegistry;
    private final ServiceDiscovery serviceDiscovery;
    private final NotificationDispatcher notificationDispatcher;
    private final HttpClient httpClient;

    private final Semaphore jobSlots;
    private final ConcurrentLinkedQueue<PendingEntry> pendingFifo = new ConcurrentLinkedQueue<>();
    private final Map<String, JobComponent> runningJobs = new ConcurrentHashMap<>();

    public TenantJobScheduler(Component parent, String tenantId, String jobType,
                              int jobConcurrency, String instanceId,
                              ActiveJobStore activeJobStore, JobStore jobStore,
                              TaskStore taskStore, JobTypeRegistry jobTypeRegistry,
                              ServiceDiscovery serviceDiscovery,
                              NotificationDispatcher notificationDispatcher,
                              HttpClient httpClient) {
        this.parent = parent;
        this.tenantId = tenantId;
        this.jobType = jobType;
        this.jobConcurrency = jobConcurrency;
        this.instanceId = instanceId;
        this.activeJobStore = activeJobStore;
        this.jobStore = jobStore;
        this.taskStore = taskStore;
        this.jobTypeRegistry = jobTypeRegistry;
        this.serviceDiscovery = serviceDiscovery;
        this.notificationDispatcher = notificationDispatcher;
        this.httpClient = httpClient;
        this.jobSlots = new Semaphore(jobConcurrency);
    }

    @Override
    protected String componentName() {
        return "tenant-" + tenantId + "-" + jobType;
    }

    @Override
    protected void handle(Object message) {
        switch (message) {
            case SubmitJob m -> handleSubmit(m);
            case JobCompleted m -> handleJobCompleted(m);
            case CancelJob m -> handleCancel(m);
            default -> {}
        }
    }

    private void handleSubmit(SubmitJob m) {
        pendingFifo.add(new PendingEntry(m.jobId(), m.scheduledAt()));
        log.debug("{}: job submitted: jobId={}", componentName(), m.jobId());
        tryActivate();
    }

    private void handleJobCompleted(JobCompleted m) {
        runningJobs.remove(m.jobId());
        activeJobStore.delete(m.jobId());
        jobSlots.release();
        log.info("{}: job {} completed with status={}", componentName(),
                m.jobId(), m.status());

        parent.tell(new Scheduler.SchedulerMsg.JobCompleted(
                m.jobId(), m.status(), tenantId, jobType));
        tryActivate();
    }

    private void handleCancel(CancelJob m) {
        JobComponent job = runningJobs.get(m.jobId());
        if (job != null) {
            job.tell(new JobComponent.Cancel());
        } else {
            pendingFifo.removeIf(e -> e.jobId.equals(m.jobId()));
        }
    }

    @Override
    public void shutdown() {
        super.shutdown();
        runningJobs.values().forEach(JobComponent::shutdown);
    }

    @Override
    public void awaitTermination(long timeoutMs) {
        for (JobComponent jc : runningJobs.values()) {
            jc.awaitTermination(timeoutMs);
        }
        joinSelf(30_000);
    }

    public void tryActivate() {
        while (jobSlots.tryAcquire()) {
            PendingEntry next = pollEligible();
            if (next == null) {
                jobSlots.release();
                return;
            }
            activateJob(next);
        }
    }

    @Override
    protected void onIdle() {
        if (!pendingFifo.isEmpty()) {
            tryActivate();
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
            if (qk != null && isQueueingKeyRunning(qk)) {
                continue;
            }

            it.remove();
            return entry;
        }
        return null;
    }

    private boolean isQueueingKeyRunning(String queueingKey) {
        for (String runningJobId : runningJobs.keySet()) {
            Job rj = jobStore.findById(runningJobId);
            if (rj != null && queueingKey.equals(rj.getQueueingKey())) {
                return true;
            }
        }
        return false;
    }

    private void activateJob(PendingEntry entry) {
        Job job = jobStore.findById(entry.jobId);
        if (job == null) return;
        if (job.getStatus() != JobStatus.PENDING) {
            jobSlots.release();
            return;
        }

        jobStore.updateStatusAndInstanceId(job.getId(), JobStatus.RUNNING, instanceId);
        activeJobStore.updateStatus(job.getId(), JobStatus.RUNNING);
        notifyJobStatus(job.getId(), JobStatus.RUNNING);

        JobComponent jc = new JobComponent(this, taskStore, jobStore,
                serviceDiscovery, jobTypeRegistry, httpClient,
                instanceId);
        runningJobs.put(job.getId(), jc);
        jc.start();
        jc.tell(new JobComponent.Activate(
                job.getId(), job.getJobType(), job.getServiceName(),
                job.getTaskConcurrency(), job.getTotalCount(), job.getMaxAttempts(),
                job.getCompletedCount(), job.getFailedCount()));

        log.info("{}: job activated: jobId={}", componentName(), job.getId());
    }

    private void notifyJobStatus(String jobId, JobStatus status) {
        try {
            notificationDispatcher.notify(jobId, status, jobStore,
                    jobTypeRegistry, serviceDiscovery, httpClient);
        } catch (Exception e) {
            log.error("{}: failed to notify job status for {}", componentName(), jobId, e);
        }
    }

    // ──────── recovery support ────────

    public void addRecoveredRunningJob(Job job) {
        JobComponent jc = new JobComponent(this, taskStore, jobStore,
                serviceDiscovery, jobTypeRegistry, httpClient,
                instanceId);
        runningJobs.put(job.getId(), jc);
        jc.start();
        jc.tell(new JobComponent.Activate(
                job.getId(), job.getJobType(), job.getServiceName(),
                job.getTaskConcurrency(), job.getTotalCount(), job.getMaxAttempts(),
                job.getCompletedCount(), job.getFailedCount()));
    }

    public void addRecoveredPendingJob(Job job) {
        pendingFifo.add(new PendingEntry(job.getId(), job.getScheduledAt()));
    }

    // ──────── messages ────────

    public record SubmitJob(String jobId, Instant scheduledAt) {}
    public record JobCompleted(String jobId, JobStatus status, int completedCount, int failedCount) {}
    public record CancelJob(String jobId) {}

    private static class PendingEntry {
        final String jobId;
        final Instant scheduledAt;
        PendingEntry(String jobId, Instant scheduledAt) {
            this.jobId = jobId;
            this.scheduledAt = scheduledAt;
        }
    }
}
