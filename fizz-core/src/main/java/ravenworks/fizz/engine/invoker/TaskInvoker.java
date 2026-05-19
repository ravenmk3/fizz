package ravenworks.fizz.engine.invoker;

import ravenworks.fizz.engine.model.TaskResult;

import java.util.concurrent.CompletableFuture;


public interface TaskInvoker {

    CompletableFuture<TaskResult> invoke(
            String serviceName,
            String method,
            String path,
            String body,
            int timeoutMs
    );

}
