package ravenworks.fizz.engine.invoker;

import ravenworks.fizz.engine.discovery.ServiceInstance;
import ravenworks.fizz.engine.model.TaskResult;

import java.util.concurrent.CompletableFuture;


public interface TaskInvoker {

    CompletableFuture<TaskResult> invoke(
            ServiceInstance instance,
            String method,
            String path,
            String body,
            int timeoutMs
    );

}
