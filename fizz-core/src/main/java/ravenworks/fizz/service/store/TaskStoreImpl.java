package ravenworks.fizz.service.store;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ravenworks.fizz.domain.entity.TaskEntity;
import ravenworks.fizz.domain.repository.TaskRepository;
import ravenworks.fizz.engine.enums.TaskStatus;
import ravenworks.fizz.engine.model.Task;
import ravenworks.fizz.engine.store.TaskStore;

import java.time.Instant;
import java.util.List;
import java.util.Optional;


@Component
public class TaskStoreImpl implements TaskStore {

    private final TaskRepository taskRepository;

    public TaskStoreImpl(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    @Override
    public List<Task> findDispatchable(String jobId, int limit, Instant now) {
        return taskRepository.findDispatchable(jobId, limit, now)
                .stream().map(this::toModel).toList();
    }

    @Override
    public Task findById(String taskId) {
        return taskRepository.findById(taskId).map(this::toModel).orElse(null);
    }

    @Override
    public int countByJobIdAndStatus(String jobId, TaskStatus status) {
        return taskRepository.countByJobIdAndStatus(jobId, status);
    }

    @Override
    @Transactional(rollbackFor = Throwable.class)
    public void save(Task task) {
        TaskEntity entity = taskRepository.findById(task.getId()).orElse(new TaskEntity());
        entity.setId(task.getId());
        entity.setJobId(task.getJobId());
        entity.setParams(task.getParams());
        entity.setStatus(task.getStatus());
        entity.setAttempts(task.getAttempts());
        entity.setAvailableAt(task.getAvailableAt());
        entity.setLastResult(task.getLastResult());
        entity.setLastError(task.getLastError());
        entity.setInstanceId(task.getInstanceId());
        taskRepository.save(entity);
    }

    @Override
    @Transactional(rollbackFor = Throwable.class)
    public int cancelPendingByJobId(String jobId) {
        return taskRepository.cancelPendingByJobId(jobId);
    }

    @Override
    @Transactional(rollbackFor = Throwable.class)
    public int recoverDanglingTasks(String currentInstanceId) {
        return taskRepository.recoverDanglingTasks(currentInstanceId);
    }

    @Override
    @Transactional(rollbackFor = Throwable.class)
    public int resetRunningToPendingByJobId(String jobId) {
        return taskRepository.resetRunningToPendingByJobId(jobId);
    }

    @Override
    public Optional<Instant> findNearestAvailableAt() {
        return taskRepository.findNearestAvailableAt();
    }

    private Task toModel(TaskEntity e) {
        Task t = new Task();
        t.setId(e.getId());
        t.setJobId(e.getJobId());
        t.setParams(e.getParams());
        t.setStatus(e.getStatus());
        t.setAttempts(e.getAttempts());
        t.setAvailableAt(e.getAvailableAt());
        t.setLastResult(e.getLastResult());
        t.setLastError(e.getLastError());
        t.setInstanceId(e.getInstanceId());
        t.setVersion(e.getVersion());
        return t;
    }

}
