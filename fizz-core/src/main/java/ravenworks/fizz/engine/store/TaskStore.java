package ravenworks.fizz.engine.store;

import ravenworks.fizz.engine.enums.TaskStatus;
import ravenworks.fizz.engine.model.Task;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface TaskStore {

    List<Task> findDispatchable(String jobId, int limit, Instant now);

    int countByJobIdAndStatus(String jobId, TaskStatus status);

    void save(Task task);

    int cancelPendingByJobId(String jobId);

    int recoverDanglingTasks(String currentInstanceId);

    Optional<Instant> findNearestAvailableAt();
}
