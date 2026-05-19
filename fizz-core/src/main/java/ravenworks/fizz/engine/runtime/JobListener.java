package ravenworks.fizz.engine.runtime;

import ravenworks.fizz.domain.entity.JobEntity;
import ravenworks.fizz.domain.enums.JobStatus;


/**
 * @author Raven
 */
public interface JobListener {

    void onStatusChanged(JobEntity job, JobStatus newStatus);

    void onProgressChanged(JobEntity job, int terminal, int total);

    JobListener NOOP = new JobListener() {

        @Override
        public void onStatusChanged(JobEntity job, JobStatus newStatus) {
        }

        @Override
        public void onProgressChanged(JobEntity job, int terminal, int total) {
        }
    };

}