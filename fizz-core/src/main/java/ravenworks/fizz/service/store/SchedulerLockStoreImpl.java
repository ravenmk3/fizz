package ravenworks.fizz.service.store;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ravenworks.fizz.domain.repository.SchedulerLockRepository;
import ravenworks.fizz.engine.store.SchedulerLockStore;


@Component
public class SchedulerLockStoreImpl implements SchedulerLockStore {

    private final SchedulerLockRepository lockRepository;

    public SchedulerLockStoreImpl(SchedulerLockRepository lockRepository) {
        this.lockRepository = lockRepository;
    }

    @Override
    @Transactional(rollbackFor = Throwable.class)
    public boolean tryAcquire(String instanceId, int heartbeatTimeoutSeconds) {
        int inserted = lockRepository.tryInsert(instanceId);
        if (inserted > 0) return true;
        int updated = lockRepository.tryAcquireByTimeout(instanceId, heartbeatTimeoutSeconds);
        return updated > 0;
    }

    @Override
    @Transactional(rollbackFor = Throwable.class)
    public void updateHeartbeat(String instanceId) {
        lockRepository.updateHeartbeat(instanceId);
    }

    @Override
    @Transactional(rollbackFor = Throwable.class)
    public void release(String instanceId) {
        lockRepository.release(instanceId);
    }

}
