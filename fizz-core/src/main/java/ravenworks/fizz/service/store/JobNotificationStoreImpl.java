package ravenworks.fizz.service.store;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ravenworks.fizz.domain.entity.JobNotificationEntity;
import ravenworks.fizz.domain.repository.JobNotificationRepository;
import ravenworks.fizz.engine.model.JobNotification;
import ravenworks.fizz.engine.store.JobNotificationStore;

import java.time.Instant;
import java.util.List;
import java.util.Optional;


@Component
public class JobNotificationStoreImpl implements JobNotificationStore {

    private final JobNotificationRepository notificationRepository;

    public JobNotificationStoreImpl(JobNotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @Override
    @Transactional(rollbackFor = Throwable.class)
    public void insert(JobNotification notification) {
        JobNotificationEntity entity = new JobNotificationEntity();
        entity.setId(notification.getId());
        entity.setJobId(notification.getJobId());
        entity.setJobStatus(notification.getJobStatus());
        entity.setStatus(notification.getStatus());
        entity.setAttempts(notification.getAttempts());
        entity.setMaxAttempts(notification.getMaxAttempts());
        entity.setAvailableAt(notification.getAvailableAt());
        notificationRepository.save(entity);
    }

    @Override
    public List<JobNotification> findPendingBefore(Instant now, int limit) {
        return notificationRepository.findPendingBefore(now, limit)
                .stream().map(this::toModel).toList();
    }

    @Override
    @Transactional(rollbackFor = Throwable.class)
    public void save(JobNotification notification) {
        JobNotificationEntity entity = notificationRepository.findById(notification.getId())
                .orElse(new JobNotificationEntity());
        entity.setId(notification.getId());
        entity.setJobId(notification.getJobId());
        entity.setJobStatus(notification.getJobStatus());
        entity.setStatus(notification.getStatus());
        entity.setAttempts(notification.getAttempts());
        entity.setMaxAttempts(notification.getMaxAttempts());
        entity.setAvailableAt(notification.getAvailableAt());
        entity.setLastError(notification.getLastError());
        notificationRepository.save(entity);
    }

    @Override
    @Transactional(rollbackFor = Throwable.class)
    public void delete(String id) {
        notificationRepository.deleteById(id);
    }

    @Override
    public Optional<Instant> findNearestAvailableAt() {
        return notificationRepository.findNearestAvailableAt();
    }

    private JobNotification toModel(JobNotificationEntity e) {
        JobNotification n = new JobNotification();
        n.setId(e.getId());
        n.setJobId(e.getJobId());
        n.setJobStatus(e.getJobStatus());
        n.setStatus(e.getStatus());
        n.setAttempts(e.getAttempts());
        n.setMaxAttempts(e.getMaxAttempts());
        n.setAvailableAt(e.getAvailableAt());
        n.setLastError(e.getLastError());
        n.setVersion(e.getVersion());
        return n;
    }

}
