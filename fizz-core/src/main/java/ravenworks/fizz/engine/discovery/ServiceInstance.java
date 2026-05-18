package ravenworks.fizz.engine.discovery;

import lombok.NonNull;

import java.net.URI;


public record ServiceInstance(@NonNull String scheme,
                              @NonNull String host,
                              int port) {

    public URI getUri() {
        return URI.create(scheme + "://" + host + ":" + port);
    }

}
