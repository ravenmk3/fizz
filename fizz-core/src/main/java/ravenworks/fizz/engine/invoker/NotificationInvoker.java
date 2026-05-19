package ravenworks.fizz.engine.invoker;

import ravenworks.fizz.common.model.ApiResponse;

import java.util.concurrent.CompletableFuture;


/**
 * @author Raven
 */
public interface NotificationInvoker {

    CompletableFuture<ApiResponse<Void>> notify(
            String serviceName,
            String path,
            String body,
            int timeoutMs);

}