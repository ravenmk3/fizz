package ravenworks.fizz.engine.store;

import ravenworks.fizz.domain.entity.ActiveJobEntity;
import ravenworks.fizz.domain.entity.JobEntity;

import java.time.Instant;
import java.util.List;


/**
 * @author Raven
 */
public interface JobStore {

    List<JobEntity> claimPendingJobs(Instant now, int limit);

    void recoverActiveJobs();

    ActiveJobEntity findActiveJob(String jobId);

}
