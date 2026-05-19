package ravenworks.fizz.engine.invoker;

import com.fasterxml.jackson.core.type.TypeReference;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ravenworks.fizz.common.json.JsonUtils;
import ravenworks.fizz.common.model.ApiResponse;
import ravenworks.fizz.engine.discovery.ServiceDiscovery;
import ravenworks.fizz.engine.discovery.ServiceInstance;
import ravenworks.fizz.engine.discovery.ServiceUnavailableException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;


/**
 * @author Raven
 */
@Slf4j
@RequiredArgsConstructor
public class JdkHttpNotificationInvoker implements NotificationInvoker {

    @NonNull
    private final HttpClient httpClient;
    @NonNull
    private final ServiceDiscovery serviceDiscovery;

    @Override
    public CompletableFuture<ApiResponse<Void>> notify(@NonNull String serviceName,
                                                       @NonNull String path,
                                                       @NonNull String body,
                                                       int timeoutMs) {
        ServiceInstance instance = serviceDiscovery.resolve(serviceName);
        if (instance == null) {
            return CompletableFuture.failedFuture(new ServiceUnavailableException(serviceName));
        }

        URI uri = instance.getUri().resolve(path);
        HttpRequest.BodyPublisher bodyPublisher = HttpRequest.BodyPublishers.ofString(body);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .POST(bodyPublisher)
                .timeout(Duration.ofMillis(timeoutMs))
                .header("Content-Type", "application/json")
                .build();

        return this.httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(this::parseNotificationResult)
                .exceptionally(e -> ApiResponse.error(500, e.getMessage()));
    }

    private ApiResponse<Void> parseNotificationResult(@NonNull HttpResponse<String> response) {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            return ApiResponse.error(response.statusCode(), "HTTP " + response.statusCode());
        }
        String body = response.body();
        if (body == null || body.isBlank()) {
            return ApiResponse.error(500, "Empty response body");
        }
        return JsonUtils.decode(body, new TypeReference<ApiResponse<Void>>() {

        });
    }

}
