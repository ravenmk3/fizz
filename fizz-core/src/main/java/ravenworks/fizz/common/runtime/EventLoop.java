package ravenworks.fizz.common.runtime;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;


/**
 * @author Raven
 */
@Slf4j
public class EventLoop {

    private static final Object SHUTDOWN_SIGNAL = new Object();

    private final AtomicReference<EventLoopState> state = new AtomicReference<>(EventLoopState.NEW);
    private final BlockingQueue<Object> events = new LinkedBlockingQueue<>();
    private final CompletableFuture<Void> termination = new CompletableFuture<>();

    private final String name;
    private final int idleTimeout;
    private final Consumer<Object> handler;

    private volatile Thread thread;

    public EventLoop(@NonNull String name,
                     int idleTimeout,
                     @NonNull Consumer<Object> handler) {
        this.name = name;
        this.idleTimeout = Math.max(10, idleTimeout);
        this.handler = handler;
    }

    public EventLoopState getState() {
        return this.state.get();
    }

    public void start() {
        if (this.state.compareAndSet(EventLoopState.NEW, EventLoopState.RUNNING)) {
            this.thread = Thread.ofVirtual()
                    .name(this.name)
                    .start(this::run);
        }
    }

    public CompletableFuture<Void> shutdown() {
        if (this.state.compareAndSet(EventLoopState.NEW, EventLoopState.TERMINATED)) {
            this.termination.complete(null);
            return this.termination;
        }
        var prev = this.state.getAndUpdate(s ->
                s == EventLoopState.RUNNING ? EventLoopState.SHUTTING_DOWN : s);
        if (prev == EventLoopState.TERMINATED || prev == EventLoopState.SHUTTING_DOWN) {
            return this.termination;
        }
        if (prev != EventLoopState.RUNNING) {
            throw new IllegalStateException("Event loop is not running");
        }
        log.info("{} - Event loop shutdown requested", this.name);
        this.events.add(SHUTDOWN_SIGNAL);
        return this.termination;
    }

    public void enqueue(@NonNull Object event) {
        this.events.add(event);
    }

    private void run() {
        log.info("{} - Event loop started", this.name);
        this.dispatch(new Started());
        while (this.state.get() == EventLoopState.RUNNING) {
            Object msg = this.poll(this.idleTimeout);
            if (msg == null) {
                msg = new Idle();
            }
            this.dispatch(msg);
        }

        log.info("{} - Event loop shutdown initiated", this.name);
        this.dispatch(new PreShutdown());
        while (!this.events.isEmpty()) {
            Object msg = this.poll(1);
            if (msg != null) {
                this.dispatch(msg);
            }
        }

        this.dispatch(new Terminated());
        this.state.set(EventLoopState.TERMINATED);
        log.info("{} - Event loop exited", this.name);
        this.termination.complete(null);
    }

    private Object poll(int timeout) {
        try {
            return this.events.poll(timeout, TimeUnit.MILLISECONDS);
        } catch (Throwable e) {
            log.error(e.getMessage(), e);
        }
        return null;
    }

    private void dispatch(@NonNull Object msg) {
        if (msg == SHUTDOWN_SIGNAL) {
            return;
        }
        try {
            this.handler.accept(msg);
        } catch (Throwable e) {
            log.error(e.getMessage(), e);
        }
    }


    public record Idle() {

    }


    public record Started() {

    }


    public record PreShutdown() {

    }


    public record Terminated() {

    }

}
