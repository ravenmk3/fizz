package ravenworks.fizz.engine.runtime;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import ravenworks.fizz.common.runtime.EventLoop;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;


/**
 * @author Raven
 */
@Slf4j
public class Scheduler {

    private static final Object WAKEUP_SIGNAL = new Object();
    private static final Duration LOCK_EXPIRY = Duration.ofSeconds(60);

    private final AtomicBoolean lockAcquired = new AtomicBoolean(false);
    private final Map<String, Worker> workers = new ConcurrentHashMap<>();
    private final EventLoop eventLoop;

    public Scheduler() {
        this.eventLoop = new EventLoop("Scheduler", 5_000, this::dispatch);
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
        return CompletableFuture.completedFuture(null);
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
    }

    private void onPreShutdown() {
        this.shutdownWorkers();
    }

    private void onTerminated() {
        this.releaseLock();
    }

    private void onLockAcquired() {
        this.recoverJobs();
    }

    private void onLockLost() {
        this.shutdownWorkers();
    }

    private boolean acquireLock() {
        // TODO impl
        return false;
    }

    private boolean renewLock() {
        // TODO impl
        return false;
    }

    private void releaseLock() {
        // TODO impl
    }

    private void schedule() {
        // TODO impl
    }

    private void recoverJobs() {
        // TODO impl
    }

    private void shutdownWorkers() {
        var futures = this.workers.values()
                .stream()
                .map(Worker::shutdown)
                .toArray(CompletableFuture[]::new);
        CompletableFuture.allOf(futures).join();
        this.workers.clear();
    }

}
