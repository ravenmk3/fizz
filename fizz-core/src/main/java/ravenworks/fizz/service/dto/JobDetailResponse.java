package ravenworks.fizz.service.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;


@Getter
@Setter
public class JobDetailResponse {

    private String id;
    private String tenantId;
    private String serviceName;
    private String jobType;
    private String mutexKey;
    private int taskConcurrency;
    private int maxAttempts;
    private String status;
    private Instant scheduledAt;
    private int totalCount;
    private int succeededCount;
    private int failedCount;
    private int cancelledCount;
    private double progress;
    private Instant createdAt;
    private Instant updatedAt;

}
