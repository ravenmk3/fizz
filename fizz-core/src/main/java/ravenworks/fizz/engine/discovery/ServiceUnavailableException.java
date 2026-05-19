package ravenworks.fizz.engine.discovery;

import lombok.NonNull;

import java.io.Serial;


public class ServiceUnavailableException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String serviceName;

    public ServiceUnavailableException(@NonNull String serviceName) {
        super("No available instance for service: " + serviceName);
        this.serviceName = serviceName;
    }

    public String getServiceName() {
        return serviceName;
    }

}
