package ravenworks.fizz.engine.runtime;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import ravenworks.fizz.common.runtime.EventLoop;
import ravenworks.fizz.domain.entity.JobEntity;

import java.util.concurrent.CompletableFuture;


/**
 * @author Raven
 */
@Slf4j
public class Worker {

    private final EventLoop eventLoop;

    public Worker(@NonNull String name) {
        this.eventLoop = new EventLoop(name, 10_000, this::dispatch);
    }

    public void start() {
        this.eventLoop.start();
    }

    public CompletableFuture<Void> shutdown() {
        return this.eventLoop.shutdown();
    }

    public void assign(@NonNull JobEntity job) {

    }

    public CompletableFuture<Void> cancel(@NonNull String jobId) {
        return CompletableFuture.completedFuture(null);
    }

    private void dispatch(Object event) {
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
    }

    private void onTerminated() {
    }

}
