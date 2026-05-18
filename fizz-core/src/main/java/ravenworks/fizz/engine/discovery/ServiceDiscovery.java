package ravenworks.fizz.engine.discovery;

public interface ServiceDiscovery {

    ServiceInstance resolve(String name);

}
