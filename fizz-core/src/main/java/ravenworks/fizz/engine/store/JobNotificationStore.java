package ravenworks.fizz.engine.store;

import ravenworks.fizz.engine.model.JobNotification;

import java.time.Instant;
import java.util.List;
import java.util.Optional;


public interface JobNotificationStore {

    void insert(JobNotification notification);

    List<JobNotification> findPendingBefore(Instant now, int limit);

    void save(JobNotification notification);

    void delete(String id);

    Optional<Instant> findNearestAvailableAt();

}
