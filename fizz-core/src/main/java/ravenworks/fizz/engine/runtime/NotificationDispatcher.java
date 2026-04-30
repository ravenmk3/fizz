package ravenworks.fizz.engine.runtime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ravenworks.fizz.common.util.UUIDv7;
import ravenworks.fizz.engine.config.SchedulerConfig;
import ravenworks.fizz.engine.discovery.JobTypeConfig;
import ravenworks.fizz.engine.discovery.JobTypeRegistry;
import ravenworks.fizz.engine.discovery.ServiceDiscovery;
import ravenworks.fizz.engine.discovery.ServiceEndpoint;
import ravenworks.fizz.engine.enums.JobStatus;
import ravenworks.fizz.engine.enums.NotificationStatus;
import ravenworks.fizz.engine.invoker.NotificationInvoker;
import ravenworks.fizz.engine.model.Job;
import ravenworks.fizz.engine.model.JobNotification;
import ravenworks.fizz.engine.model.NotificationPayload;
import ravenworks.fizz.engine.store.JobNotificationStore;
import ravenworks.fizz.engine.store.JobStore;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;


public class NotificationDispatcher {

    private final Logger LOGGER = LoggerFactory.getLogger(getClass());

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
                       NotificationInvoker notificationInvoker) {
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

        sendAsync(notification, jobStore, jobTypeRegistry, serviceDiscovery, notificationInvoker);
    }

    public void recoverPending(JobStore jobStore, JobTypeRegistry jobTypeRegistry,
                               ServiceDiscovery serviceDiscovery, NotificationInvoker notificationInvoker) {
        List<JobNotification> pending = notificationStore.findPendingBefore(Instant.now(), 200);
        LOGGER.info("Recovering {} pending notifications", pending.size());
        for (JobNotification n : pending) {
            sendAsync(n, jobStore, jobTypeRegistry, serviceDiscovery, notificationInvoker);
        }
    }

    private void sendAsync(JobNotification notification, JobStore jobStore,
                           JobTypeRegistry jobTypeRegistry, ServiceDiscovery serviceDiscovery,
                           NotificationInvoker notificationInvoker) {
        Thread.ofVirtual().name("notification-" + notification.getId()).start(() -> {
            send(notification, jobStore, jobTypeRegistry, serviceDiscovery, notificationInvoker);
        });
    }

    private void send(JobNotification n, JobStore jobStore,
                      JobTypeRegistry jobTypeRegistry, ServiceDiscovery serviceDiscovery,
                      NotificationInvoker notificationInvoker) {
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
            NotificationPayload payload = new NotificationPayload(
                    job.getId(), n.getJobStatus().name(),
                    job.getTotalCount(), job.getCompletedCount(), job.getFailedCount());

            boolean success = notificationInvoker.send(endpoint, typeConfig.notifyPath(),
                    payload, config.getNotificationTimeoutMs());

            if (success) {
                notificationStore.delete(n.getId());
                return;
            }
            handleFailure(n, jobStore, jobTypeRegistry, serviceDiscovery, notificationInvoker,
                    "Notification send failed");
        } catch (Exception e) {
            handleFailure(n, jobStore, jobTypeRegistry, serviceDiscovery, notificationInvoker, e.getMessage());
        }
    }

    private void handleFailure(JobNotification n, JobStore jobStore,
                               JobTypeRegistry jobTypeRegistry, ServiceDiscovery serviceDiscovery,
                               NotificationInvoker notificationInvoker, String error) {
        n.setAttempts(n.getAttempts() + 1);
        n.setLastError(error);
        if (n.getAttempts() >= n.getMaxAttempts()) {
            n.setStatus(NotificationStatus.FAILED);
            notificationStore.save(n);
        } else {
            n.setAvailableAt(Instant.now().plusMillis(config.getNotificationRetryIntervalMs()));
            notificationStore.save(n);
            retryScheduler.schedule(() -> send(n, jobStore, jobTypeRegistry, serviceDiscovery, notificationInvoker),
                    config.getNotificationRetryIntervalMs(), TimeUnit.MILLISECONDS);
        }
    }

}
