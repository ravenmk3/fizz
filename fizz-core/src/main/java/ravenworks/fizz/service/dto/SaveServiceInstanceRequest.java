package ravenworks.fizz.service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;


@Getter
@Setter
public class SaveServiceInstanceRequest {

    @NotBlank
    private String serviceName;

    private String scheme = "http";

    @NotBlank
    private String host;

    @NotNull
    @Positive
    private Integer port;

}
