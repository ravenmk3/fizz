package ravenworks.fizz.service;

import ravenworks.fizz.service.dto.CancelJobResponse;
import ravenworks.fizz.service.dto.CreateJobRequest;
import ravenworks.fizz.service.dto.CreateJobResponse;
import ravenworks.fizz.service.dto.JobDetailResponse;
import ravenworks.fizz.service.dto.ListJobsRequest;
import ravenworks.fizz.service.dto.ListJobsResponse;

public interface JobService {

    CreateJobResponse create(CreateJobRequest request);

    JobDetailResponse get(String id);

    ListJobsResponse list(ListJobsRequest request);

    CancelJobResponse cancel(String id);

}
