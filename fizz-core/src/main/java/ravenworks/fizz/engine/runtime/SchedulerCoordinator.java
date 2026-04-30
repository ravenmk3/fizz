package ravenworks.fizz.engine.runtime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ravenworks.fizz.engine.config.SchedulerConfig;
import ravenworks.fizz.engine.store.SchedulerLockStore;

import java.time.Duration;
import java.util.concurrent.locks.LockSupport;


public class SchedulerCoordinator {

    private final Logger LOGGER = LoggerFactory.getLogger(getClass());

    private static final Duration ACQUIRE_WAIT = Duration.ofSeconds(5);
    private static final Duration RENEW_WAIT = Duration.ofSeconds(50);

    private final SchedulerLockStore lockStore;
    private final SchedulerConfig config;

    private volatile boolean running;
    private volatile boolean holdsLock;
    private Thread coordinatorThread;
    private Scheduler scheduler;

    public SchedulerCoordinator(SchedulerLockStore lockStore, SchedulerConfig config) {
        this.lockStore = lockStore;
        this.config = config;
    }

    public void setScheduler(Scheduler scheduler) {
        this.scheduler = scheduler;
    }

    public void start() {
        coordinatorThread = Thread.ofVirtual()
                .name("fizz-coordinator")
                .start(this::runLoop);
        LOGGER.info("SchedulerCoordinator started, instanceId={}", InstanceId.VALUE);
    }

    public void shutdown() {
        LOGGER.info("SchedulerCoordinator stopping");
        running = false;
        Thread t = coordinatorThread;
        if (t != null) {
            LockSupport.unpark(t);
        }
    }

    public void awaitTermination(long timeoutMs) {
        Thread t = coordinatorThread;
        if (t != null) {
            try {
                t.join(timeoutMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        LOGGER.info("SchedulerCoordinator stopped");
    }

    private void runLoop() {
        running = true;
        while (running) {
            try {
                if (!holdsLock) {
                    LockSupport.parkNanos(ACQUIRE_WAIT.toNanos());
                    if (!running) break;
                    if (tryAcquireLock()) {
                        holdsLock = true;
                        scheduler.tell(new CoordinatorMsg.LockAcquired());
                        LOGGER.info("Lock acquired, instanceId={}", InstanceId.VALUE);
                    }
                } else {
                    LockSupport.parkNanos(RENEW_WAIT.toNanos());
                    if (!running) break;
                    if (!renewLock()) {
                        holdsLock = false;
                        scheduler.tell(new CoordinatorMsg.LockLost());
                        LOGGER.warn("Lock renew failed, instanceId={}", InstanceId.VALUE);
                    }
                }
            } catch (Exception e) {
                LOGGER.error("Coordinator loop error", e);
                holdsLock = false;
            }
        }
        LOGGER.info("SchedulerCoordinator loop exited");
    }

    private boolean tryAcquireLock() {
        try {
            return lockStore.tryAcquire(InstanceId.VALUE, config.getHeartbeatTimeoutSeconds());
        } catch (Exception e) {
            LOGGER.error("Failed to acquire leader lock", e);
            return false;
        }
    }

    private boolean renewLock() {
        try {
            lockStore.updateHeartbeat(InstanceId.VALUE);
            return true;
        } catch (Exception e) {
            LOGGER.error("Failed to renew leader lock", e);
            return false;
        }
    }

    public sealed interface CoordinatorMsg {

        record LockAcquired() implements CoordinatorMsg {

        }


        record LockLost() implements CoordinatorMsg {

        }

    }

}
