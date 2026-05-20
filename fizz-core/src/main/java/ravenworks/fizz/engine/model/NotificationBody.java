package ravenworks.fizz.engine.model;

import lombok.Getter;
import lombok.Setter;


@Getter
@Setter
public class NotificationBody {

    private String jobId;
    private String status;
    private String bizKey;
    private int succeededCount;
    private int failedCount;
    private int cancelledCount;
    private int totalCount;

}