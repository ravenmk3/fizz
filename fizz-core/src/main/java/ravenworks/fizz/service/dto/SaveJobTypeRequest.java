package ravenworks.fizz.service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SaveJobTypeRequest {

    @NotBlank
    private String serviceName;

    @NotBlank
    private String jobType;

    @NotBlank
    private String taskPath;

    private String httpMethod = "POST";

    @NotNull
    @Positive
    private Integer timeoutMs = 30000;

    private String backoffStrategy = "FIXED";

    @NotNull
    @PositiveOrZero
    private Integer backoffInitialMs = 10000;

    @NotNull
    @PositiveOrZero
    private Integer backoffMaxMs = 300000;

    @NotNull
    @Positive
    private Integer jobConcurrency = 10;

    @NotNull
    @Positive
    private Integer taskConcurrency = 1;

    private String notifyPath;

}
