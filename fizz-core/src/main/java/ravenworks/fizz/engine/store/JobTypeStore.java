package ravenworks.fizz.engine.store;

import lombok.NonNull;
import ravenworks.fizz.domain.entity.JobTypeEntity;


/**
 * @author Raven
 */
public interface JobTypeStore {

    JobTypeEntity get(@NonNull String jobType);

}