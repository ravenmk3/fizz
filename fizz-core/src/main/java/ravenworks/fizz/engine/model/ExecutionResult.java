package ravenworks.fizz.engine.model;

public class ExecutionResult {

    private String status;
    private String message;
    private String retryAfter;

    public ExecutionResult() {
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getRetryAfter() {
        return retryAfter;
    }

    public void setRetryAfter(String retryAfter) {
        this.retryAfter = retryAfter;
    }
}
