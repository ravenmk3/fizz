package ravenworks.fizz.engine.discovery;

public interface ServiceDiscovery {

    ServiceEndpoint resolve(String serviceName);
}
