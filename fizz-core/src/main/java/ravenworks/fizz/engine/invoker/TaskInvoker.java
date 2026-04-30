package ravenworks.fizz.engine.invoker;

import ravenworks.fizz.engine.discovery.ServiceEndpoint;
import ravenworks.fizz.engine.model.TaskResult;


public interface TaskInvoker {

    TaskResult invoke(ServiceEndpoint endpoint, String path, String httpMethod,
                      String jsonBody, int timeoutMs);

}
