package ravenworks.fizz.engine.model;

import lombok.NonNull;
import ravenworks.fizz.domain.enums.TaskResultStatus;

import java.time.Instant;


public record TaskResult(@NonNull TaskResultStatus status,
                         String message,
                         Instant retryAfter) {

    public static TaskResult succeeded() {
        return new TaskResult(TaskResultStatus.SUCCEEDED, null, null);
    }

    public static TaskResult succeeded(String message) {
        return new TaskResult(TaskResultStatus.SUCCEEDED, message, null);
    }

    public static TaskResult failed(String message) {
        return new TaskResult(TaskResultStatus.FAILED, message, null);
    }

    public static TaskResult inProgress(Instant retryAfter) {
        return new TaskResult(TaskResultStatus.IN_PROGRESS, null, retryAfter);
    }

    public static TaskResult inProgress(String message, Instant retryAfter) {
        return new TaskResult(TaskResultStatus.IN_PROGRESS, message, retryAfter);
    }

}
