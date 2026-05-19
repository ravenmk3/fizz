package ravenworks.fizz.service.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;


@Getter
@Setter
@AllArgsConstructor
public class CreateJobResponse {

    private String id;
    private String status;
    private int totalCount;
    private LocalDateTime createdAt;
    private boolean created;

}
