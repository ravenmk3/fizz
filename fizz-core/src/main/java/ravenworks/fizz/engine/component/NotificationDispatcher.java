package ravenworks.fizz.engine.component;

import lombok.extern.slf4j.Slf4j;
import ravenworks.fizz.common.json.JsonUtils;
import ravenworks.fizz.common.util.UUIDv7;
import ravenworks.fizz.engine.config.SchedulerConfig;
import ravenworks.fizz.engine.discovery.JobTypeConfig;
import ravenworks.fizz.engine.discovery.JobTypeRegistry;
import ravenworks.fizz.engine.discovery.ServiceDiscovery;
import ravenworks.fizz.engine.discovery.ServiceEndpoint;
import ravenworks.fizz.engine.enums.JobStatus;
import ravenworks.fizz.engine.enums.NotificationStatus;
import ravenworks.fizz.engine.model.Job;
import ravenworks.fizz.engine.model.JobNotification;
import ravenworks.fizz.engine.store.JobNotificationStore;
import ravenworks.fizz.engine.store.JobStore;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
public class NotificationDispatcher {

    private final JobNotificationStore notificationStore;
    private final SchedulerConfig config;
    private final ScheduledExecutorService retryScheduler = Executors.newSingleThreadScheduledExecutor(
            Thread.ofVirtual().factory());

    public NotificationDispatcher(JobNotificationStore notificationStore, SchedulerConfig config) {
        this.notificationStore = notificationStore;
        this.config = config;
    }

    public void notify(String jobId, JobStatus status, JobStore jobStore,
                       JobTypeRegistry jobTypeRegistry, ServiceDiscovery serviceDiscovery,
                       HttpClient httpClient) {
        try {
            Job job = jobStore.findById(jobId);
            if (job == null) return;
            JobTypeConfig typeConfig = jobTypeRegistry.get(job.getJobType());
            if (typeConfig.notifyPath() == null) return;
        } catch (Exception e) {
            return;
        }

        JobNotification notification = new JobNotification();
        notification.setId(UUIDv7.generate());
        notification.setJobId(jobId);
        notification.setJobStatus(status);
        notification.setStatus(NotificationStatus.PENDING);
        notification.setAvailableAt(Instant.now());
        notification.setMaxAttempts(config.getNotificationMaxAttempts());
        notificationStore.insert(notification);

        sendAsync(notification, jobStore, jobTypeRegistry, serviceDiscovery, httpClient);
    }

    public void recoverPending(JobStore jobStore, JobTypeRegistry jobTypeRegistry,
                               ServiceDiscovery serviceDiscovery, HttpClient httpClient) {
        List<JobNotification> pending = notificationStore.findPendingBefore(Instant.now(), 200);
        log.info("Recovering {} pending notifications", pending.size());
        for (JobNotification n : pending) {
            sendAsync(n, jobStore, jobTypeRegistry, serviceDiscovery, httpClient);
        }
    }

    private void sendAsync(JobNotification notification, JobStore jobStore,
                           JobTypeRegistry jobTypeRegistry, ServiceDiscovery serviceDiscovery,
                           HttpClient httpClient) {
        Thread.ofVirtual().name("notification-" + notification.getId()).start(() -> {
            send(notification, jobStore, jobTypeRegistry, serviceDiscovery, httpClient);
        });
    }

    private void send(JobNotification n, JobStore jobStore,
                      JobTypeRegistry jobTypeRegistry, ServiceDiscovery serviceDiscovery,
                      HttpClient httpClient) {
        try {
            Job job = jobStore.findById(n.getJobId());
            if (job == null) {
                notificationStore.delete(n.getId());
                return;
            }

            JobTypeConfig typeConfig = jobTypeRegistry.get(job.getJobType());
            if (typeConfig.notifyPath() == null) {
                notificationStore.delete(n.getId());
                return;
            }

            ServiceEndpoint endpoint = serviceDiscovery.resolve(job.getServiceName());
            URI uri = URI.create(endpoint.baseUrl() + typeConfig.notifyPath());
            String body = buildBody(job, n.getJobStatus());

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .timeout(Duration.ofMillis(config.getNotificationTimeoutMs()))
                    .header("Content-Type", "application/json")
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                notificationStore.delete(n.getId());
                return;
            }
            handleFailure(n, jobStore, jobTypeRegistry, serviceDiscovery, httpClient,
                    "HTTP " + response.statusCode());
        } catch (Exception e) {
            handleFailure(n, jobStore, jobTypeRegistry, serviceDiscovery, httpClient, e.getMessage());
        }
    }

    private void handleFailure(JobNotification n, JobStore jobStore,
                               JobTypeRegistry jobTypeRegistry, ServiceDiscovery serviceDiscovery,
                               HttpClient httpClient, String error) {
        n.setAttempts(n.getAttempts() + 1);
        n.setLastError(error);
        if (n.getAttempts() >= n.getMaxAttempts()) {
            n.setStatus(NotificationStatus.FAILED);
            notificationStore.save(n);
        } else {
            n.setAvailableAt(Instant.now().plusMillis(config.getNotificationRetryIntervalMs()));
            notificationStore.save(n);
            retryScheduler.schedule(() -> send(n, jobStore, jobTypeRegistry, serviceDiscovery, httpClient),
                    config.getNotificationRetryIntervalMs(), TimeUnit.MILLISECONDS);
        }
    }

    private String buildBody(Job job, JobStatus status) {
        return JsonUtils.toJson(Map.of(
                "jobId", job.getId(),
                "status", status.name(),
                "totalCount", job.getTotalCount(),
                "completedCount", job.getCompletedCount(),
                "failedCount", job.getFailedCount()
        ));
    }
}
