package ravenworks.fizz.engine.discovery;

import lombok.NonNull;


/**
 * @author Raven
 */
public interface ServiceHealthIndicator {

    boolean isAvailable(@NonNull String serviceName);

}