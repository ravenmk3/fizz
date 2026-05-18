package ravenworks.fizz.service.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;


@Getter
@Setter
@AllArgsConstructor
public class CreateJobResponse {

    private String id;
    private String status;
    private int totalCount;
    private Instant createdAt;
    private boolean created;

}
