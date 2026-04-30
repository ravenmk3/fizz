package ravenworks.fizz.engine.invoker;

import ravenworks.fizz.engine.discovery.ServiceEndpoint;
import ravenworks.fizz.engine.model.NotificationPayload;


public interface NotificationInvoker {

    boolean send(ServiceEndpoint endpoint, String path,
                 NotificationPayload payload, int timeoutMs);

}
