package ravenworks.fizz.service;

import ravenworks.fizz.service.dto.*;


public interface JobService {

    CreateJobResponse create(CreateJobRequest request);

    JobDetailResponse get(String id);

    ListJobsResponse list(ListJobsRequest request);

    CancelJobResponse cancel(String id);

}
