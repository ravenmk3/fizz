package ravenworks.fizz.service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.Setter;


@Getter
@Setter
public class UpdateJobTypeRequest {

    @NotBlank
    private String jobType;

    private String taskPath;

    private String httpMethod;

    @Positive
    private Integer timeoutMs;

    private String backoffStrategy;

    @PositiveOrZero
    private Integer backoffInitialMs;

    @PositiveOrZero
    private Integer backoffMaxMs;

    @Positive
    private Integer jobConcurrency;

    @Positive
    private Integer taskConcurrency;

    private String notifyPath;

}
