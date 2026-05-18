package ravenworks.fizz.service.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;


@Getter
@Setter
public class DeleteJobTypeRequest {

    @NotBlank
    private String jobType;

}
