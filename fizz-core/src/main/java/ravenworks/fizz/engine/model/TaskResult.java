package ravenworks.fizz.engine.model;

import lombok.NonNull;
import ravenworks.fizz.domain.enums.TaskResultStatus;

import java.time.LocalDateTime;


public record TaskResult(@NonNull TaskResultStatus status,
                         String message,
                         LocalDateTime retryAfter) {

    public static TaskResult succeeded() {
        return new TaskResult(TaskResultStatus.SUCCEEDED, null, null);
    }

    public static TaskResult succeeded(String message) {
        return new TaskResult(TaskResultStatus.SUCCEEDED, message, null);
    }

    public static TaskResult failed(String message) {
        return new TaskResult(TaskResultStatus.FAILED, message, null);
    }

    public static TaskResult inProgress(LocalDateTime retryAfter) {
        return new TaskResult(TaskResultStatus.IN_PROGRESS, null, retryAfter);
    }

    public static TaskResult inProgress(String message, LocalDateTime retryAfter) {
        return new TaskResult(TaskResultStatus.IN_PROGRESS, message, retryAfter);
    }

}
