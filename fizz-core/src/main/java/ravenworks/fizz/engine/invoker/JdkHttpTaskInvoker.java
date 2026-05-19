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
import ravenworks.fizz.engine.model.TaskResult;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CompletableFuture;


@Slf4j
@RequiredArgsConstructor
public class JdkHttpTaskInvoker implements TaskInvoker {

    private static final Set<String> VALID_METHODS = Set.of("POST", "PUT");

    @NonNull
    private final HttpClient httpClient;
    @NonNull
    private final ServiceDiscovery serviceDiscovery;

    @Override
    public CompletableFuture<TaskResult> invoke(@NonNull String serviceName,
                                                @NonNull String method,
                                                @NonNull String path,
                                                @NonNull String body,
                                                int timeoutMs) {
        method = method.toUpperCase();
        if (!VALID_METHODS.contains(method)) {
            throw new IllegalArgumentException(String.format("Invalid method: %s", method));
        }

        ServiceInstance instance = serviceDiscovery.resolve(serviceName);
        if (instance == null) {
            return CompletableFuture.failedFuture(new ServiceUnavailableException(serviceName));
        }

        URI uri = instance.getUri().resolve(path);
        HttpRequest.BodyPublisher bodyPublisher = HttpRequest.BodyPublishers.ofString(body);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .method(method, bodyPublisher)
                .timeout(Duration.ofMillis(timeoutMs))
                .header("Content-Type", "application/json")
                .build();

        return this.httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(this::parseTaskResult)
                .exceptionally(e -> TaskResult.failed(e.getMessage()));
    }

    private TaskResult parseTaskResult(@NonNull HttpResponse<String> response) {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            return TaskResult.failed("HTTP " + response.statusCode());
        }
        String body = response.body();
        if (body == null || body.isBlank()) {
            return TaskResult.failed("Empty response body");
        }
        var resp = JsonUtils.decode(body, new TypeReference<ApiResponse<TaskResult>>() {

        });
        if (resp.getCode() != 0) {
            var message = String.format("Error:%d, %s", resp.getCode(), resp.getMessage());
            return TaskResult.failed(message);
        }
        var result = resp.getData();
        if (result == null) {
            return TaskResult.failed("Empty response data");
        }
        return resp.getData();
    }

}
