package ravenworks.fizz.web.dto;

public record SaveServiceInstanceRequest(
        String serviceName,
        String scheme,
        String host,
        Integer port
) {}
