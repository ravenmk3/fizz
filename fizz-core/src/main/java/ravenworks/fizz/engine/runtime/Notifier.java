package ravenworks.fizz.engine.runtime;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import ravenworks.fizz.common.model.ApiResponse;
import ravenworks.fizz.common.runtime.EventLoop;
import ravenworks.fizz.domain.entity.JobEntity;
import ravenworks.fizz.domain.entity.JobNotificationEntity;
import ravenworks.fizz.domain.entity.JobTypeEntity;
import ravenworks.fizz.domain.repository.JobNotificationRepository;
import ravenworks.fizz.domain.repository.JobTypeRepository;
import ravenworks.fizz.engine.discovery.ServiceHealthTracker;
import ravenworks.fizz.engine.invoker.NotificationInvoker;
import ravenworks.fizz.engine.model.NotificationBody;
import ravenworks.fizz.engine.store.JobStore;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;


/**
 * @author Raven
 */
@Slf4j
public class Notifier {

    private static final int BATCH_SIZE = 100;
    private static final int NOTIFY_TIMEOUT_MS = 30_000;

    private final EventLoop eventLoop = new EventLoop("Notifier", 10_000, this::dispatch);
    private final Map<String, JobTypeEntity> jobTypeCache = new ConcurrentHashMap<>();
    private final JobNotificationRepository notificationRepo;
    private final JobStore jobStore;
    private final JobTypeRepository jobTypeRepository;
    private final NotificationInvoker invoker;
    private final ServiceHealthTracker healthTracker;

    public Notifier(@NonNull JobNotificationRepository notificationRepo,
                    @NonNull JobStore jobStore,
                    @NonNull JobTypeRepository jobTypeRepository,
                    @NonNull NotificationInvoker invoker,
                    @NonNull ServiceHealthTracker healthTracker) {
        this.notificationRepo = notificationRepo;
        this.jobStore = jobStore;
        this.jobTypeRepository = jobTypeRepository;
        this.invoker = invoker;
        this.healthTracker = healthTracker;
    }

    public void start() {
        this.eventLoop.start();
    }

    public CompletableFuture<Void> shutdown() {
        return this.eventLoop.shutdown();
    }

    public void wake() {
        this.eventLoop.enqueue(new Wakeup());
    }

    private void dispatch(Object event) {
        switch (event) {
            case EventLoop.Idle _ -> this.processBatch();
            case EventLoop.Started _, EventLoop.PreShutdown _, EventLoop.Terminated _ -> this.noop();
            case Wakeup _ -> this.processBatch();
            default -> log.warn("Unhandled event: {}", event);
        }
    }

    private void noop() {
    }

    private void processBatch() {
        while (true) {
            List<JobNotificationEntity> records = this.notificationRepo
                    .findByAvailableAtBefore(LocalDateTime.now(),
                            PageRequest.of(0, BATCH_SIZE));
            if (records.isEmpty()) {
                break;
            }

            for (JobNotificationEntity record : records) {
                if (!this.healthTracker.isAvailable(record.getServiceName())) {
                    record.setAvailableAt(LocalDateTime.now().plusMinutes(5));
                    this.notificationRepo.save(record);
                    continue;
                }
                processRecord(record);
            }
        }
    }

    private void processRecord(JobNotificationEntity record) {
        JobEntity job = this.jobStore.findJob(record.getJobId());
        if (job == null) {
            log.warn("Notification: job {} not found, delete record", record.getJobId());
            this.notificationRepo.delete(record);
            return;
        }

        JobTypeEntity jobType = this.jobTypeCache.computeIfAbsent(job.getJobType(),
                type -> this.jobTypeRepository.findByJobType(type).orElse(null));
        if (jobType == null || jobType.getNotifyPath() == null) {
            log.warn("Notification: job type {} not found or no notifyPath, delete record",
                    job.getJobType());
            this.notificationRepo.delete(record);
            return;
        }

        NotificationBody body = new NotificationBody();
        body.setJobId(job.getId());
        body.setStatus(job.getStatus().name());
        body.setBizKey(job.getBizKey());
        body.setSucceededCount(job.getSucceededCount());
        body.setFailedCount(job.getFailedCount());
        body.setCancelledCount(job.getCancelledCount());
        body.setTotalCount(job.getTotalCount());

        try {
            ApiResponse<Void> resp = this.invoker.notify(
                    record.getServiceName(),
                    jobType.getNotifyPath(),
                    body,
                    NOTIFY_TIMEOUT_MS).join();

            if (resp.getCode() == 0) {
                this.notificationRepo.delete(record);
                log.debug("Notification sent for job {}", record.getJobId());
            } else {
                record.setLastMessage(resp.getMessage());
                record.setAttempts(record.getAttempts() + 1);
                record.setAvailableAt(LocalDateTime.now().plusMinutes(1));
                this.notificationRepo.save(record);
                log.warn("Notification failed for job {}: code={}, message={}",
                        record.getJobId(), resp.getCode(), resp.getMessage());
            }
        } catch (Exception e) {
            record.setLastMessage(e.getMessage());
            record.setAttempts(record.getAttempts() + 1);
            record.setAvailableAt(LocalDateTime.now().plusMinutes(1));
            this.notificationRepo.save(record);
            log.warn("Notification error for job {}: {}", record.getJobId(), e.getMessage());
        }
    }

    record Wakeup() {

    }

}
