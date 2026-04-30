package ravenworks.fizz.web.dto;

public record SaveJobTypeRequest(
        String serviceName,
        String jobType,
        String taskPath,
        String notifyPath,
        String httpMethod,
        Integer timeoutMs,
        String backoffStrategy,
        Integer backoffInitialMs,
        Integer backoffMaxMs,
        Integer jobConcurrency,
        Integer taskConcurrency
) {

}
