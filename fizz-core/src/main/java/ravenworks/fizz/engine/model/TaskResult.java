package ravenworks.fizz.engine.model;

import ravenworks.fizz.engine.enums.TaskResultStatus;
import java.time.Instant;

public record TaskResult(TaskResultStatus status, String message, Instant retryAfter) {

    public static TaskResult success() {
        return new TaskResult(TaskResultStatus.SUCCESS, null, null);
    }

    public static TaskResult success(String message) {
        return new TaskResult(TaskResultStatus.SUCCESS, message, null);
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
