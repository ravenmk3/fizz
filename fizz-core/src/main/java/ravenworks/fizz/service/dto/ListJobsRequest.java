package ravenworks.fizz.service.dto;

import lombok.Getter;
import lombok.Setter;


@Getter
@Setter
public class ListJobsRequest {

    private String tenantId;

    private String status;

    private String serviceName;

    private String jobType;

    private Integer page = 1;

    private Integer size = 20;

}
