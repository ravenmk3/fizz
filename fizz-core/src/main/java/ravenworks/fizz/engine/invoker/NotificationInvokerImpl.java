package ravenworks.fizz.engine.invoker;

import com.fasterxml.jackson.core.type.TypeReference;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ravenworks.fizz.common.json.JsonUtils;
import ravenworks.fizz.common.model.ApiResponse;
import ravenworks.fizz.engine.model.NotificationBody;
import ravenworks.fizz.engine.model.ServiceResponse;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;


/**
 * @author Raven
 */
@Slf4j
@RequiredArgsConstructor
public class NotificationInvokerImpl implements NotificationInvoker {

    @NonNull
    private final ServiceClient serviceClient;

    @Override
    public CompletableFuture<ApiResponse<Void>> notify(@NonNull String serviceName,
                                                       @NonNull String path,
                                                       @NonNull NotificationBody body,
                                                       int timeoutMs) {
        String bodyJson = JsonUtils.encode(body);
        byte[] bodyBytes = bodyJson.getBytes(StandardCharsets.UTF_8);

        return this.serviceClient.request(serviceName, "POST", path, bodyBytes, timeoutMs)
                .thenApply(this::parseNotificationResult)
                .exceptionally(e -> ApiResponse.error(500, e.getMessage()));
    }

    private ApiResponse<Void> parseNotificationResult(@NonNull ServiceResponse response) {
        if (response.getStatus() < 200 || response.getStatus() >= 300) {
            return ApiResponse.error(response.getStatus(), "HTTP " + response.getStatus());
        }
        String body = new String(response.getBody(), StandardCharsets.UTF_8);
        if (body.isBlank()) {
            return ApiResponse.error(500, "Empty response body");
        }
        return JsonUtils.decode(body, new TypeReference<ApiResponse<Void>>() {

        });
    }

}
