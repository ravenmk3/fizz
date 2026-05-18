package ravenworks.fizz.service.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GetJobRequest {

    @NotBlank
    private String id;

}
