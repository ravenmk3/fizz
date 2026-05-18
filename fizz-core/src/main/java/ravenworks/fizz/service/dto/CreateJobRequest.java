package ravenworks.fizz.service.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

@Getter
@Setter
public class CreateJobRequest {

    @NotBlank
    private String tenantId;

    @NotBlank
    private String serviceName;

    @NotBlank
    private String jobType;

    private String mutexKey;

    private String bizKey;

    @Positive
    private Integer taskConcurrency = 1;

    private Integer maxAttempts = -1;

    private String scheduledAt;

    @NotEmpty
    @Valid
    private List<TaskParam> tasks;

    @Getter
    @Setter
    public static class TaskParam {
        private Map<String, Object> params;
    }

}
