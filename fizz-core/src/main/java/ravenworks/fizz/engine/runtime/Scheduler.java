package ravenworks.fizz.engine.runtime;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import ravenworks.fizz.common.runtime.EventLoop;
import ravenworks.fizz.common.util.Uuids;
import ravenworks.fizz.domain.entity.ActiveJobEntity;
import ravenworks.fizz.domain.entity.JobEntity;
import ravenworks.fizz.domain.entity.JobNotificationEntity;
import ravenworks.fizz.domain.entity.JobTypeEntity;
import ravenworks.fizz.domain.enums.JobStatus;
import ravenworks.fizz.domain.repository.JobNotificationRepository;
import ravenworks.fizz.domain.repository.JobTypeRepository;
import ravenworks.fizz.engine.discovery.ServiceHealthTracker;
import ravenworks.fizz.engine.invoker.NotificationInvoker;
import ravenworks.fizz.engine.invoker.TaskInvoker;
import ravenworks.fizz.engine.lock.SchedulerLock;
import ravenworks.fizz.engine.store.JobStore;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;


/**
 * @author Raven
 */
@Slf4j
public class Scheduler {

    private static final Object WAKEUP_SIGNAL = new Object();
    private static final int CLAIM_BATCH_SIZE = 1_000;

    private final Map<String, Worker> workers = new ConcurrentHashMap<>();
    private final EventLoop eventLoop = new EventLoop("Scheduler", 5_000, this::dispatch);
    private final JobStore jobStore;
    private final SchedulerLock schedulerLock;
    private final TaskInvoker taskInvoker;
    private final JobTypeRepository jobTypeRepository;
    private final ServiceHealthTracker healthTracker;
    private final JobNotificationRepository notificationRepo;
    private final NotificationInvoker notificationInvoker;

    private Notifier notifier;

    public Scheduler(@NonNull JobStore jobStore,
                     @NonNull SchedulerLock schedulerLock,
                     @NonNull TaskInvoker taskInvoker,
                     @NonNull JobTypeRepository jobTypeRepository,
                     @NonNull ServiceHealthTracker healthTracker,
                     @NonNull JobNotificationRepository notificationRepo,
                     @NonNull NotificationInvoker notificationInvoker) {
        this.jobStore = jobStore;
        this.schedulerLock = schedulerLock;
        this.taskInvoker = taskInvoker;
        this.jobTypeRepository = jobTypeRepository;
        this.healthTracker = healthTracker;
        this.notificationRepo = notificationRepo;
        this.notificationInvoker = notificationInvoker;
    }

    public void start() {
        this.eventLoop.start();
    }

    public CompletableFuture<Void> shutdown() {
        return this.eventLoop.shutdown();
    }

    public void wake() {
        this.eventLoop.enqueue(WAKEUP_SIGNAL);
    }

    public CompletableFuture<Void> cancel(@NonNull String jobId) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        this.eventLoop.enqueue(new CancelJobRequest(jobId, future));
        return future;
    }

    private void dispatch(Object event) {
        if (event == WAKEUP_SIGNAL) {
            this.onWakeup();
            return;
        }
        switch (event) {
            case EventLoop.Idle _ -> this.onIdle();
            case EventLoop.Started _ -> this.onStarted();
            case EventLoop.PreShutdown _ -> this.onPreShutdown();
            case EventLoop.Terminated _ -> this.onTerminated();
            case CancelJobRequest req -> this.onCancelJob(req);
            case AddJobNotification an -> this.onAddJobNotification(an);
            default -> log.warn("Unhandled event: {}", event);
        }
    }

    private void onWakeup() {
        this.schedule();
    }

    private void onIdle() {
        this.schedule();
    }

    private void onStarted() {
        this.schedulerLock.init();
    }

    private void onPreShutdown() {
        this.shutdownNotifier();
        this.shutdownWorkers();
    }

    private void onTerminated() {
        this.schedulerLock.release();
    }

    private void schedule() {
        SchedulerLock.PulseResult pr = this.schedulerLock.pulse();
        switch (pr) {
            case ACQUIRED -> {
                this.jobStore.recoverActiveJobs();
                this.startNotifier();
            }
            case LOST -> {
                this.shutdownWorkers();
                this.shutdownNotifier();
                return;
            }
            case FAILED -> {
                return;
            }
        }
        var jobs = jobStore.claimPendingJobs(LocalDateTime.now(), CLAIM_BATCH_SIZE);
        if (jobs.isEmpty()) {
            return;
        }
        for (JobEntity job : jobs) {
            String workerName = job.getTenantId() + ":" + job.getJobType();
            Worker worker = this.workers.computeIfAbsent(workerName, name -> {
                JobTypeEntity jobType = this.jobTypeRepository.findByJobType(job.getJobType())
                        .orElse(null);
                if (jobType == null) {
                    log.warn("JobType {} not found, skip job {}", job.getJobType(), job.getId());
                    return null;
                }
                log.info("Create worker: {}", name);
                Worker w = new Worker(name, this.jobStore, this.taskInvoker, jobType, this.healthTracker);
                w.setListener(this.newWorkerListener());
                w.start();
                return w;
            });
            if (worker != null) {
                worker.assign(job);
            }
        }
        log.info("Scheduled {} jobs across {} workers", jobs.size(), this.workers.size());
    }

    private void shutdownWorkers() {
        var futures = this.workers.values()
                .stream()
                .map(Worker::shutdown)
                .toArray(CompletableFuture[]::new);
        CompletableFuture.allOf(futures).join();
        this.workers.clear();
    }

    private void startNotifier() {
        if (this.notifier == null) {
            this.notifier = new Notifier(this.notificationRepo,
                    this.jobStore, this.jobTypeRepository,
                    this.notificationInvoker, this.healthTracker);
            this.notifier.start();
            log.info("Notifier started");
        }
    }

    private void shutdownNotifier() {
        if (this.notifier != null) {
            this.notifier.shutdown().join();
            this.notifier = null;
            log.info("Notifier shutdown");
        }
    }

    private JobListener newWorkerListener() {
        return new JobListener() {

            @Override
            public void onStatusChanged(JobEntity job, JobStatus newStatus) {
                eventLoop.enqueue(new AddJobNotification(job));
            }

            @Override
            public void onProgressChanged(JobEntity job, int terminal, int total) {
                eventLoop.enqueue(new AddJobNotification(job));
            }
        };
    }

    private void onAddJobNotification(AddJobNotification event) {
        JobEntity job = event.job();
        JobTypeEntity jobType = this.jobTypeRepository.findByJobType(job.getJobType())
                .orElse(null);
        if (jobType == null || jobType.getNotifyPath() == null) {
            return;
        }

        JobNotificationEntity record = new JobNotificationEntity();
        record.setId(Uuids.uuid7Hex());
        record.setJobId(job.getId());
        record.setServiceName(jobType.getServiceName());
        record.setAvailableAt(LocalDateTime.now());
        this.notificationRepo.save(record);

        if (this.notifier != null) {
            this.notifier.wake();
        }
    }

    private void onCancelJob(CancelJobRequest req) {
        ActiveJobEntity active = this.jobStore.findActiveJob(req.jobId());
        if (active == null) {
            log.warn("Cancel failed: job {} not found in active jobs", req.jobId());
            req.future().complete(null);
            return;
        }
        String workerName = active.getTenantId() + ":" + active.getJobType();
        Worker worker = this.workers.get(workerName);
        if (worker == null) {
            log.warn("Cancel failed: no worker found for {}", workerName);
            req.future().complete(null);
            return;
        }
        log.info("Cancel job {} routed to worker {}", req.jobId(), workerName);
        worker.cancel(active).whenComplete((v, ex) -> {
            if (ex != null) {
                req.future().completeExceptionally(ex);
            } else {
                req.future().complete(null);
            }
        });
    }


    record CancelJobRequest(String jobId, CompletableFuture<Void> future) {

    }


    record AddJobNotification(JobEntity job) {

    }

}
