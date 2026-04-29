package ravenworks.fizz.engine.component;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

@Slf4j
public abstract class Component {

    protected final BlockingQueue<Object> inbox = new LinkedBlockingQueue<>();
    protected volatile boolean running = false;
    protected Thread thread;

    public void tell(Object message) {
        inbox.add(message);
        Thread t = thread;
        if (t != null) {
            LockSupport.unpark(t);
        }
    }

    public void start() {
        thread = Thread.ofVirtual()
                .name(componentName())
                .start(() -> {
                    beforeLoop();
                    running = true;
                    eventLoop();
                });
        log.info("{} started", componentName());
    }

    public void shutdown() {
        log.info("{} stopping", componentName());
        running = false;
        Thread t = thread;
        if (t != null) {
            LockSupport.unpark(t);
        }
    }

    protected boolean joinSelf(long timeoutMs) {
        Thread t = thread;
        if (t != null) {
            try {
                t.join(timeoutMs);
                return !t.isAlive();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        return true;
    }

    public abstract void awaitTermination(long timeoutMs);

    protected void beforeLoop() {
    }

    private void eventLoop() {
        while (running) {
            try {
                Object msg = inbox.poll(timeoutMs(), TimeUnit.MILLISECONDS);
                if (msg != null) {
                    handle(msg);
                }
                onIdle();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("{} event loop error", componentName(), e);
            }
        }
        log.info("{} event loop exited", componentName());
    }

    protected abstract String componentName();

    protected abstract void handle(Object message);

    protected long timeoutMs() {
        return 60_000;
    }

    protected void onIdle() {
    }
}
