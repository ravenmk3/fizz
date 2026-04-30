package ravenworks.fizz.web.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;


public record CreateJobRequest(
        String tenantId,
        String serviceName,
        String jobType,
        String queueingKey,
        String bizKey,
        Integer taskConcurrency,
        Integer maxAttempts,
        Instant scheduledAt,
        List<TaskParam> tasks
) {

    public record TaskParam(Map<String, Object> params) {

    }

}
