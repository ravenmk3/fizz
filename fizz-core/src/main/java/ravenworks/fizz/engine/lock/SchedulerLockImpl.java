package ravenworks.fizz.engine.lock;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ravenworks.fizz.common.runtime.InstanceId;
import ravenworks.fizz.domain.entity.SchedulerLockEntity;
import ravenworks.fizz.domain.repository.SchedulerLockRepository;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicBoolean;


/**
 * @author Raven
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SchedulerLockImpl implements SchedulerLock {

    private static final Duration LOCK_EXPIRY = Duration.ofSeconds(60);

    private final SchedulerLockRepository lockRepository;
    private final AtomicBoolean acquired = new AtomicBoolean(false);

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void init() {
        try {
            boolean ok = acquireInternal();
            if (ok) {
                log.info("Coordinator lock initialized and acquired by {}", InstanceId.VALUE);
            }
        } catch (Exception e) {
            log.error("Coordinator lock init failed", e);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PulseResult pulse() {
        try {
            if (acquired.get()) {
                boolean renewed = renewInternal();
                if (renewed) {
                    return PulseResult.RENEWED;
                }
                acquired.set(false);
                log.error("Coordinator lock renew failed, lock lost");
                return PulseResult.LOST;
            }
            boolean ok = acquireInternal();
            if (ok) {
                acquired.set(true);
                log.info("Coordinator lock acquired by {}", InstanceId.VALUE);
                return PulseResult.ACQUIRED;
            }
            return PulseResult.FAILED;
        } catch (Exception e) {
            log.error("Coordinator lock pulse failed", e);
            return acquired.get() ? PulseResult.LOST : PulseResult.FAILED;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void release() {
        try {
            lockRepository.releaseLock(InstanceId.VALUE);
            acquired.set(false);
            log.info("Coordinator lock released by {}", InstanceId.VALUE);
        } catch (Exception e) {
            log.error("Coordinator lock release failed", e);
        }
    }

    private boolean acquireInternal() {
        var opt = lockRepository.findById(1);
        LocalDateTime now = LocalDateTime.now();
        if (opt.isEmpty()) {
            SchedulerLockEntity lock = new SchedulerLockEntity();
            lock.setId(1);
            lock.setInstanceId(InstanceId.VALUE);
            lock.setAcquiredAt(now);
            lock.setHeartbeatAt(now);
            lockRepository.save(lock);
            return true;
        }
        SchedulerLockEntity lock = opt.get();
        if (InstanceId.VALUE.equals(lock.getInstanceId())) {
            return true;
        }
        LocalDateTime expiry = lock.getHeartbeatAt().plus(LOCK_EXPIRY);
        if (now.isAfter(expiry)) {
            lock.setInstanceId(InstanceId.VALUE);
            lock.setAcquiredAt(now);
            lock.setHeartbeatAt(now);
            lockRepository.save(lock);
            return true;
        }
        return false;
    }

    private boolean renewInternal() {
        int rows = lockRepository.renewHeartbeat(InstanceId.VALUE, LocalDateTime.now());
        return rows > 0;
    }

}
