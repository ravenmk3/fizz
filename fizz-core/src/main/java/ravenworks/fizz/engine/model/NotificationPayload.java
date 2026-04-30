package ravenworks.fizz.engine.model;

public record NotificationPayload(String jobId, String status,
                                  int totalCount, int completedCount, int failedCount) {

}
