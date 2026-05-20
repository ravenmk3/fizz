package ravenworks.fizz.engine.discovery;

import lombok.NonNull;

import java.util.List;


public interface ServiceDiscovery {

    List<ServiceInstance> getInstances(@NonNull String serviceName);

}
