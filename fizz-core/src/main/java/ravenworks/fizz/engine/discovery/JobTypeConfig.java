package ravenworks.fizz.engine.discovery;

import ravenworks.fizz.engine.enums.BackoffStrategy;

public record JobTypeConfig(
        String serviceName,
        String jobType,
        String taskPath,
        String notifyPath,
        String httpMethod,
        int timeoutMs,
        BackoffStrategy backoffStrategy,
        int backoffInitialMs,
        int backoffMaxMs,
        int jobConcurrency,
        int taskConcurrency
) {}
