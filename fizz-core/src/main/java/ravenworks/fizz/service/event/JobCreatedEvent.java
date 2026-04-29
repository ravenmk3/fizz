package ravenworks.fizz.service.event;

import java.time.Instant;

public record JobCreatedEvent(String jobId, String tenantId, String jobType, Instant scheduledAt) {}
