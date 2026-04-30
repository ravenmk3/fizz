package ravenworks.fizz.engine.config;

public interface SchedulerConfig {

    int getHeartbeatTimeoutSeconds();

    int getNotificationRetryIntervalMs();

    int getNotificationMaxAttempts();

    int getNotificationTimeoutMs();

}
