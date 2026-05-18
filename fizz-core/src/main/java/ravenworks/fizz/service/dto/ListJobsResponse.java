package ravenworks.fizz.service.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.List;


@Getter
@Setter
@AllArgsConstructor
public class ListJobsResponse {

    private List<JobDetailResponse> items;
    private long total;
    private int page;
    private int size;

}
