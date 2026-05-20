package ravenworks.fizz.engine.invoker;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ravenworks.fizz.engine.discovery.ServiceInstance;
import ravenworks.fizz.engine.discovery.ServiceLoadBalancer;
import ravenworks.fizz.engine.discovery.ServiceUnavailableException;
import ravenworks.fizz.engine.model.ServiceResponse;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CompletableFuture;


/**
 * @author Raven
 */
@Slf4j
@RequiredArgsConstructor
public class JdkHttpServiceClient implements ServiceClient {

    private static final Set<String> VALID_METHODS = Set.of("POST", "PUT");

    @NonNull
    private final HttpClient httpClient;
    @NonNull
    private final ServiceLoadBalancer loadBalancer;

    @Override
    public CompletableFuture<ServiceResponse> request(@NonNull String serviceName,
                                                      @NonNull String method,
                                                      @NonNull String path,
                                                      @NonNull byte[] body,
                                                      int timeoutMs) {
        method = method.toUpperCase();
        if (!VALID_METHODS.contains(method)) {
            throw new IllegalArgumentException("Invalid method: " + method);
        }

        ServiceInstance instance = this.loadBalancer.resolve(serviceName);
        if (instance == null) {
            return CompletableFuture.failedFuture(
                    new ServiceUnavailableException(serviceName));
        }

        URI uri = instance.getUri().resolve(path);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .method(method, HttpRequest.BodyPublishers.ofByteArray(body))
                .timeout(Duration.ofMillis(timeoutMs))
                .header("Content-Type", "application/json")
                .build();

        return this.httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray())
                .thenApply(resp -> {
                    ServiceResponse sr = new ServiceResponse();
                    sr.setStatus(resp.statusCode());
                    sr.setHeaders(resp.headers().map());
                    sr.setBody(resp.body());
                    return sr;
                })
                .whenComplete((resp, error) -> {
                    if (error != null) {
                        this.loadBalancer.recordFailure(instance);
                    } else {
                        this.loadBalancer.recordSuccess(instance);
                    }
                });
    }

}
