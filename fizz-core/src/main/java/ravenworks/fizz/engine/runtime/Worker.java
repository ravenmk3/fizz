package ravenworks.fizz.engine.runtime;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import ravenworks.fizz.common.runtime.EventLoop;
import ravenworks.fizz.domain.entity.ActiveJobEntity;
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
        this.eventLoop.enqueue(new JobAssigned(job));
    }

    public CompletableFuture<Void> cancel(@NonNull ActiveJobEntity job) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        this.eventLoop.enqueue(new CancelJobRequest(job, future));
        return future;
    }

    private void dispatch(Object event) {
        switch (event) {
            case EventLoop.Idle _ -> this.onIdle();
            case EventLoop.Started _ -> this.onStarted();
            case EventLoop.PreShutdown _ -> this.onPreShutdown();
            case EventLoop.Terminated _ -> this.onTerminated();
            case JobAssigned(JobEntity job) -> this.onJobAssigned(job);
            case CancelJobRequest req -> this.onCancelJob(req);
            default -> log.warn("Unhandled event: {}", event);
        }
    }

    private void onJobAssigned(JobEntity job) {
        log.info("Worker [{}] assigned job: id={}, type={}, tasks={}",
                this.eventLoop.getName(), job.getId(), job.getJobType(), job.getTotalCount());
    }

    private void onCancelJob(CancelJobRequest req) {
        ActiveJobEntity job = req.job();
        log.info("Worker [{}] cancel job: id={}, type={}",
                this.eventLoop.getName(), job.getId(), job.getJobType());
        req.future().complete(null);
    }

    private void onIdle() {
    }

    private void onStarted() {
    }

    private void onPreShutdown() {
    }

    private void onTerminated() {
    }


    public record JobAssigned(JobEntity job) {

    }


    record CancelJobRequest(ActiveJobEntity job, CompletableFuture<Void> future) {

    }

}
