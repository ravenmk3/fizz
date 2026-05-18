package ravenworks.fizz.server.config;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ravenworks.fizz.engine.invoker.JdkHttpTaskInvoker;
import ravenworks.fizz.engine.invoker.TaskInvoker;

import java.net.http.HttpClient;


@Slf4j
@Configuration
public class EngineConfiguration {

    @Bean
    public static TaskInvoker taskInvoker(@NonNull HttpClient httpClient) {
        return new JdkHttpTaskInvoker(httpClient);
    }

}
