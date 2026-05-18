package ravenworks.fizz.service;

import ravenworks.fizz.domain.entity.JobTypeEntity;
import ravenworks.fizz.service.dto.SaveJobTypeRequest;
import ravenworks.fizz.service.dto.UpdateJobTypeRequest;

import java.util.List;


public interface JobTypeService {

    JobTypeEntity save(SaveJobTypeRequest request);

    JobTypeEntity update(UpdateJobTypeRequest request);

    List<JobTypeEntity> list(String serviceName);

    void delete(String jobType);

}
