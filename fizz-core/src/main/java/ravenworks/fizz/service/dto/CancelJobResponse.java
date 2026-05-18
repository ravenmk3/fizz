package ravenworks.fizz.service.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;


@Getter
@Setter
@AllArgsConstructor
public class CancelJobResponse {

    private String id;
    private String status;
    private int cancelledTasks;

}
