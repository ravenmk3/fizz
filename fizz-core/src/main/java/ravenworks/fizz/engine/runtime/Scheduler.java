package ravenworks.fizz.engine.runtime;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import ravenworks.fizz.common.runtime.EventLoop;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;


/**
 * @author Raven
 */
@Slf4j
public class Scheduler {

    private static final Object WAKEUP_SIGNAL = new Object();

    private final Map<String, Worker> workers = new ConcurrentHashMap<>();
    private final EventLoop eventLoop;

    public Scheduler() {
        this.eventLoop = new EventLoop("scheduler", 5_000, this::dispatch);
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

    private void onIdle() {
    }

    private void onStarted() {
    }

    private void onPreShutdown() {
        this.shutdownWorkers();
    }

    private void onTerminated() {
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
