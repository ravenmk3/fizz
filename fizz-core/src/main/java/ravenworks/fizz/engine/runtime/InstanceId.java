package ravenworks.fizz.engine.runtime;

import java.util.UUID;


public final class InstanceId {

    public static final String VALUE = UUID.randomUUID().toString().replace("-", "");

    private InstanceId() {
    }

}
