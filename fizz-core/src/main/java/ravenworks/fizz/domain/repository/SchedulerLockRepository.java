package ravenworks.fizz.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import ravenworks.fizz.domain.entity.SchedulerLockEntity;

public interface SchedulerLockRepository extends JpaRepository<SchedulerLockEntity, Integer> {

    @Modifying
    @Query(value = "INSERT IGNORE INTO fizz_scheduler_lock (id, instance_id, heartbeat_at, acquired_at) VALUES (1, :instanceId, NOW(3), NOW(3))", nativeQuery = true)
    int tryInsert(String instanceId);

    @Modifying
    @Query(value = "UPDATE fizz_scheduler_lock SET instance_id = :instanceId, heartbeat_at = NOW(3), acquired_at = NOW(3) WHERE id = 1 AND heartbeat_at < NOW(3) - INTERVAL :timeoutSeconds SECOND", nativeQuery = true)
    int tryAcquireByTimeout(String instanceId, int timeoutSeconds);

    @Modifying
    @Query(value = "UPDATE fizz_scheduler_lock SET heartbeat_at = NOW(3) WHERE id = 1 AND instance_id = :instanceId", nativeQuery = true)
    int updateHeartbeat(String instanceId);

    @Modifying
    @Query(value = "UPDATE fizz_scheduler_lock SET instance_id = '', heartbeat_at = '1970-01-01' WHERE id = 1 AND instance_id = :instanceId", nativeQuery = true)
    int release(String instanceId);
}
