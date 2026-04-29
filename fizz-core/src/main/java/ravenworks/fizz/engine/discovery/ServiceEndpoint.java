package ravenworks.fizz.engine.discovery;

public record ServiceEndpoint(String scheme, String host, int port) {

    public String baseUrl() {
        return scheme + "://" + host + ":" + port;
    }
}
