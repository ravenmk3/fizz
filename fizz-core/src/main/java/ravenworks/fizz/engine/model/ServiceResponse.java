package ravenworks.fizz.engine.model;

import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;


@Getter
@Setter
public class ServiceResponse {

    private int status;
    private Map<String, List<String>> headers;
    private byte[] body;

}