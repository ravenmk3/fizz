package ravenworks.fizz.web.dto;

public record ListJobsRequest(
        String tenantId,
        String status,
        String serviceName,
        String jobType,
        Integer page,
        Integer size
) {
    public int pageOrDefault() { return page != null ? page : 1; }
    public int sizeOrDefault() { return size != null ? size : 20; }
}
