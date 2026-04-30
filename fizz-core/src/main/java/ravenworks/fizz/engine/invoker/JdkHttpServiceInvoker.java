package ravenworks.fizz.engine.invoker;

import com.fasterxml.jackson.core.type.TypeReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ravenworks.fizz.common.json.JsonUtils;
import ravenworks.fizz.common.model.ApiResponse;
import ravenworks.fizz.engine.discovery.ServiceEndpoint;
import ravenworks.fizz.engine.enums.TaskResultStatus;
import ravenworks.fizz.engine.model.ExecutionResult;
import ravenworks.fizz.engine.model.NotificationPayload;
import ravenworks.fizz.engine.model.TaskResult;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;


public class JdkHttpServiceInvoker implements TaskInvoker, NotificationInvoker {

    private final Logger LOGGER = LoggerFactory.getLogger(getClass());

    private final HttpClient httpClient;

    public JdkHttpServiceInvoker(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public TaskResult invoke(ServiceEndpoint endpoint, String path, String httpMethod,
                             String jsonBody, int timeoutMs) {
        try {
            URI uri = URI.create(endpoint.baseUrl() + path);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .method(httpMethod, HttpRequest.BodyPublishers.ofString(jsonBody))
                    .timeout(Duration.ofMillis(timeoutMs))
                    .header("Content-Type", "application/json")
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return parseTaskResponse(response);
        } catch (Exception e) {
            LOGGER.error("Task HTTP failed: path={}, error={}", path, e.getMessage());
            return TaskResult.failed(e.getMessage());
        }
    }

    @Override
    public boolean send(ServiceEndpoint endpoint, String path,
                        NotificationPayload payload, int timeoutMs) {
        try {
            URI uri = URI.create(endpoint.baseUrl() + path);
            String body = JsonUtils.toJson(payload);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .timeout(Duration.ofMillis(timeoutMs))
                    .header("Content-Type", "application/json")
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (Exception e) {
            LOGGER.error("Notification HTTP failed: path={}, error={}", path, e.getMessage());
            return false;
        }
    }

    private TaskResult parseTaskResponse(HttpResponse<String> response) {
        String body = response.body();
        if (body == null || body.isBlank()) {
            return TaskResult.failed("Empty response body");
        }
        try {
            ApiResponse<ExecutionResult> resp = JsonUtils.fromJson(body,
                    new TypeReference<ApiResponse<ExecutionResult>>() {

                    });
            if (resp.getCode() != 0) {
                String message = resp.getMessage();
                return TaskResult.failed(message != null ? message : "Remote error code: " + resp.getCode());
            }
            ExecutionResult data = resp.getData();
            if (data == null) {
                return TaskResult.success();
            }
            TaskResultStatus status = TaskResultStatus.valueOf(data.getStatus());
            Instant retryAfter = (data.getRetryAfter() != null && !data.getRetryAfter().equals("null"))
                    ? Instant.parse(data.getRetryAfter()) : null;
            return new TaskResult(status, data.getMessage(), retryAfter);
        } catch (Exception e) {
            return TaskResult.failed("Failed to parse response: " + e.getMessage());
        }
    }

}
