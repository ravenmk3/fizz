package ravenworks.fizz.engine.store;

public interface SchedulerLockStore {

    boolean tryAcquire(String instanceId, int heartbeatTimeoutSeconds);

    void updateHeartbeat(String instanceId);

    void release(String instanceId);

}
