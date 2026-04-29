package ravenworks.fizz.engine.component;

import lombok.extern.slf4j.Slf4j;
import ravenworks.fizz.common.util.UUIDv7;
import ravenworks.fizz.engine.config.SchedulerConfig;
import ravenworks.fizz.engine.discovery.JobTypeRegistry;
import ravenworks.fizz.engine.discovery.ServiceDiscovery;
import ravenworks.fizz.engine.enums.JobStatus;
import ravenworks.fizz.engine.model.ActiveJob;
import ravenworks.fizz.engine.model.Job;
import ravenworks.fizz.engine.store.ActiveJobStore;
import ravenworks.fizz.engine.store.JobStore;
import ravenworks.fizz.engine.store.SchedulerLockStore;
import ravenworks.fizz.engine.store.TaskStore;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.LockSupport;

@Slf4j
public class Scheduler extends Component {

    private final String instanceId = UUIDv7.generate();
    private final SchedulerLockStore lockStore;
    private final ActiveJobStore activeJobStore;
    private final JobStore jobStore;
    private final TaskStore taskStore;
    private final SchedulerConfig config;
    private final JobTypeRegistry jobTypeRegistry;
    private final ServiceDiscovery serviceDiscovery;
    private final NotificationDispatcher notificationDispatcher;
    private final HttpClient httpClient;

    private final Map<String, TenantJobScheduler> tenants = new ConcurrentHashMap<>();
    private volatile boolean isLeader;

    public Scheduler(SchedulerLockStore lockStore, ActiveJobStore activeJobStore,
                     JobStore jobStore, TaskStore taskStore, SchedulerConfig config,
                     JobTypeRegistry jobTypeRegistry, ServiceDiscovery serviceDiscovery,
                     NotificationDispatcher notificationDispatcher, HttpClient httpClient) {
        this.lockStore = lockStore;
        this.activeJobStore = activeJobStore;
        this.jobStore = jobStore;
        this.taskStore = taskStore;
        this.config = config;
        this.jobTypeRegistry = jobTypeRegistry;
        this.serviceDiscovery = serviceDiscovery;
        this.notificationDispatcher = notificationDispatcher;
        this.httpClient = httpClient;
    }

    @Override
    protected String componentName() {
        return "fizz-scheduler";
    }

    @Override
    protected long timeoutMs() {
        return 2000;
    }

    @Override
    protected void beforeLoop() {
        recover();
    }

    @Override
    protected void handle(Object message) {
        switch (message) {
            case SchedulerMsg.JobSubmitted m -> handleJobSubmitted(m);
            case SchedulerMsg.JobCompleted m -> handleJobCompleted(m);
            case SchedulerMsg.CancelJob m -> handleCancel(m);
            default -> {}
        }
    }

    @Override
    protected void onIdle() {
        if (!isLeader) {
            tryAcquireLeader();
            return;
        }
        heartbeat();
    }

    // ──────── leader election / recovery ────────

    private void recover() {
        while (!isLeader) {
            if (tryAcquireLeaderLock()) {
                isLeader = true;
                break;
            }
            LockSupport.parkNanos(Duration.ofSeconds(2).toNanos());
        }

        log.info("Leader acquired, recovering...");
        taskStore.recoverDanglingTasks(instanceId);
        notificationDispatcher.recoverPending(jobStore, jobTypeRegistry, serviceDiscovery, httpClient);

        List<ActiveJob> all = activeJobStore.findByStatusIn(
                List.of(JobStatus.PENDING, JobStatus.RUNNING));

        Map<String, List<ActiveJob>> byGroup = new LinkedHashMap<>();
        for (ActiveJob aj : all) {
            Job job = jobStore.findById(aj.getId());
            if (job == null) {
                activeJobStore.delete(aj.getId());
                continue;
            }
            String key = job.getTenantId() + ":" + job.getJobType();
            byGroup.computeIfAbsent(key, k -> new ArrayList<>()).add(aj);
        }

        for (var entry : byGroup.entrySet()) {
            String[] parts = entry.getKey().split(":", 2);
            String tenantId = parts[0];
            String jobType = parts[1];

            Job job = jobStore.findById(entry.getValue().get(0).getId());
            if (job == null) continue;

            int concurrency = resolveJobConcurrency(jobType);
            TenantJobScheduler ts = new TenantJobScheduler(this, tenantId, jobType,
                    concurrency, instanceId, activeJobStore, jobStore, taskStore,
                    jobTypeRegistry, serviceDiscovery, notificationDispatcher, httpClient);
            tenants.put(entry.getKey(), ts);
            ts.start();

            for (ActiveJob aj : entry.getValue()) {
                Job j = jobStore.findById(aj.getId());
                if (j == null) continue;
                if (aj.getStatus() == JobStatus.RUNNING) {
                    ts.addRecoveredRunningJob(j);
                } else {
                    ts.addRecoveredPendingJob(j);
                }
            }
            ts.tryActivate();
        }

        log.info("Recovery complete: {} tenant groups, {} active jobs", byGroup.size(), all.size());
    }

    private void tryAcquireLeader() {
        if (tryAcquireLeaderLock()) {
            isLeader = true;
            log.info("Acquired leader lock, instanceId={}", instanceId);
            recover();
        }
    }

    private boolean tryAcquireLeaderLock() {
        try {
            return lockStore.tryAcquire(instanceId, config.getHeartbeatTimeoutSeconds());
        } catch (Exception e) {
            log.error("Failed to acquire leader lock", e);
            return false;
        }
    }

    private void heartbeat() {
        try {
            lockStore.updateHeartbeat(instanceId);
        } catch (Exception e) {
            log.error("Heartbeat failed, losing leader", e);
            isLeader = false;
        }
    }

    // ──────── message handlers ────────

    private void handleJobSubmitted(SchedulerMsg.JobSubmitted m) {
        String key = key(m.tenantId(), m.jobType());
        TenantJobScheduler ts = tenants.computeIfAbsent(key, k -> {
            int concurrency = resolveJobConcurrency(m.jobType());
            TenantJobScheduler t = new TenantJobScheduler(this, m.tenantId(), m.jobType(),
                    concurrency, instanceId, activeJobStore, jobStore, taskStore,
                    jobTypeRegistry, serviceDiscovery, notificationDispatcher, httpClient);
            t.start();
            log.info("Created tenant scheduler: {}", key);
            return t;
        });
        ts.tell(new TenantJobScheduler.SubmitJob(m.jobId(), m.scheduledAt()));
    }

    private void handleJobCompleted(SchedulerMsg.JobCompleted m) {
        notificationDispatcher.notify(m.jobId(), m.status(), jobStore,
                jobTypeRegistry, serviceDiscovery, httpClient);
    }

    private void handleCancel(SchedulerMsg.CancelJob m) {
        String key = key(m.tenantId(), m.jobType());
        TenantJobScheduler ts = tenants.get(key);
        if (ts != null) {
            ts.tell(new TenantJobScheduler.CancelJob(m.jobId()));
        }
    }

    @Override
    public void shutdown() {
        running = false;
        LockSupport.unpark(thread);
        tenants.values().forEach(TenantJobScheduler::shutdown);
    }

    @Override
    public void awaitTermination(long timeoutMs) {
        for (TenantJobScheduler ts : tenants.values()) {
            ts.awaitTermination(timeoutMs);
        }
        joinSelf(30_000);
        taskStore.recoverDanglingTasks(instanceId);
        lockStore.release(instanceId);
        isLeader = false;
        log.info("Scheduler stopped");
    }

    // ──────── helpers ────────

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

    // ──────── public API ────────

    public void submitJob(String jobId, String tenantId, String jobType, Instant scheduledAt) {
        tell(new SchedulerMsg.JobSubmitted(jobId, tenantId, jobType, scheduledAt));
    }

    public void cancelJob(String jobId, String tenantId, String jobType) {
        tell(new SchedulerMsg.CancelJob(jobId, tenantId, jobType));
    }

    public String getInstanceId() {
        return instanceId;
    }

    // ──────── messages ────────

    public sealed interface SchedulerMsg {
        record JobSubmitted(String jobId, String tenantId, String jobType, Instant scheduledAt)
                implements SchedulerMsg {}
        record JobCompleted(String jobId, JobStatus status, String tenantId, String jobType)
                implements SchedulerMsg {}
        record CancelJob(String jobId, String tenantId, String jobType)
                implements SchedulerMsg {}
    }
}
