package ravenworks.fizz.service.config;

import ravenworks.fizz.engine.config.SchedulerConfig;

public class FizzSchedulerProperties implements SchedulerConfig {

    private Scheduler scheduler = new Scheduler();
    private Notification notification = new Notification();

    @Override
    public int getHeartbeatTimeoutSeconds() { return scheduler.heartbeatTimeoutSeconds; }

    @Override
    public int getNotificationRetryIntervalMs() { return notification.retryIntervalMs; }

    @Override
    public int getNotificationMaxAttempts() { return notification.maxAttempts; }

    @Override
    public int getNotificationTimeoutMs() { return notification.timeoutMs; }

    public Scheduler getScheduler() { return scheduler; }
    public void setScheduler(Scheduler scheduler) { this.scheduler = scheduler; }

    public Notification getNotification() { return notification; }
    public void setNotification(Notification notification) { this.notification = notification; }

    public static class Scheduler {
        private int heartbeatTimeoutSeconds = 60;

        public int getHeartbeatTimeoutSeconds() { return heartbeatTimeoutSeconds; }
        public void setHeartbeatTimeoutSeconds(int v) { this.heartbeatTimeoutSeconds = v; }
    }

    public static class Notification {
        private int retryIntervalMs = 5000;
        private int maxAttempts = 10;
        private int timeoutMs = 10000;

        public int getRetryIntervalMs() { return retryIntervalMs; }
        public void setRetryIntervalMs(int v) { this.retryIntervalMs = v; }

        public int getMaxAttempts() { return maxAttempts; }
        public void setMaxAttempts(int v) { this.maxAttempts = v; }

        public int getTimeoutMs() { return timeoutMs; }
        public void setTimeoutMs(int v) { this.timeoutMs = v; }
    }
}
