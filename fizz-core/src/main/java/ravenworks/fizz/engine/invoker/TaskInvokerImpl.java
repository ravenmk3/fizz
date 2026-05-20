package ravenworks.fizz.engine.invoker;

import com.fasterxml.jackson.core.type.TypeReference;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ravenworks.fizz.common.json.JsonUtils;
import ravenworks.fizz.common.model.ApiResponse;
import ravenworks.fizz.engine.model.ServiceResponse;
import ravenworks.fizz.engine.model.TaskResult;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;


/**
 * @author Raven
 */
@Slf4j
@RequiredArgsConstructor
public class TaskInvokerImpl implements TaskInvoker {

    @NonNull
    private final ServiceClient serviceClient;

    @Override
    public CompletableFuture<TaskResult> invoke(@NonNull String serviceName,
                                                @NonNull String method,
                                                @NonNull String path,
                                                @NonNull String body,
                                                int timeoutMs) {
        byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);

        return this.serviceClient.request(serviceName, method, path, bodyBytes, timeoutMs)
                .thenApply(this::parseTaskResult)
                .exceptionally(e -> TaskResult.failed(e.getMessage()));
    }

    private TaskResult parseTaskResult(@NonNull ServiceResponse response) {
        if (response.getStatus() < 200 || response.getStatus() >= 300) {
            return TaskResult.failed("HTTP " + response.getStatus());
        }
        String body = new String(response.getBody(), StandardCharsets.UTF_8);
        if (body.isBlank()) {
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
