package ravenworks.fizz.service.event;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import ravenworks.fizz.engine.component.Scheduler;

@Component
public class JobEventListener {

    private final Scheduler scheduler;

    public JobEventListener(Scheduler scheduler) {
        this.scheduler = scheduler;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onJobCreated(JobCreatedEvent event) {
        scheduler.submitJob(event.jobId(), event.tenantId(), event.jobType(), event.scheduledAt());
    }
}
