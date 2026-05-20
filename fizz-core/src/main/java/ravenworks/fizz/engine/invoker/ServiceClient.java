package ravenworks.fizz.engine.invoker;

import ravenworks.fizz.engine.model.ServiceResponse;

import java.util.concurrent.CompletableFuture;


/**
 * @author Raven
 */
public interface ServiceClient {

    CompletableFuture<ServiceResponse> request(
            String serviceName,
            String method,
            String path,
            byte[] body,
            int timeoutMs);

}