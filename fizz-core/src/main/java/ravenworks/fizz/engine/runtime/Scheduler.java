package ravenworks.fizz.engine.runtime;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import ravenworks.fizz.common.runtime.EventLoop;
import ravenworks.fizz.domain.entity.ActiveJobEntity;
import ravenworks.fizz.domain.entity.JobEntity;
import ravenworks.fizz.engine.lock.SchedulerLock;
import ravenworks.fizz.engine.store.JobStore;

import java.time.Instant;
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

    public Scheduler(@NonNull JobStore jobStore,
                     @NonNull SchedulerLock schedulerLock) {
        this.jobStore = jobStore;
        this.schedulerLock = schedulerLock;
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
        this.shutdownWorkers();
    }

    private void onTerminated() {
        this.schedulerLock.release();
    }

    private void schedule() {
        SchedulerLock.PulseResult pr = this.schedulerLock.pulse();
        switch (pr) {
            case ACQUIRED -> this.jobStore.recoverActiveJobs();
            case LOST -> {
                this.shutdownWorkers();
                return;
            }
            case FAILED -> {
                return;
            }
        }
        var jobs = jobStore.claimPendingJobs(Instant.now(), CLAIM_BATCH_SIZE);
        if (jobs.isEmpty()) {
            return;
        }
        for (JobEntity job : jobs) {
            String workerName = job.getTenantId() + ":" + job.getJobType();
            Worker worker = this.workers.computeIfAbsent(workerName, name -> {
                log.info("Create worker: {}", name);
                Worker w = new Worker(name);
                w.start();
                return w;
            });
            worker.assign(job);
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

}
