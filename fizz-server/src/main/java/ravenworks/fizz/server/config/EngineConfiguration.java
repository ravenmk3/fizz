package ravenworks.fizz.server.config;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ravenworks.fizz.engine.discovery.ServiceDiscovery;
import ravenworks.fizz.engine.invoker.JdkHttpTaskInvoker;
import ravenworks.fizz.engine.invoker.TaskInvoker;
import ravenworks.fizz.engine.runtime.Scheduler;
import ravenworks.fizz.engine.lock.SchedulerLock;
import ravenworks.fizz.engine.store.JobStore;

import java.net.http.HttpClient;


@Slf4j
@Configuration
public class EngineConfiguration {

    @Bean
    public static TaskInvoker taskInvoker(@NonNull HttpClient httpClient,
                                          @NonNull ServiceDiscovery serviceDiscovery) {
        return new JdkHttpTaskInvoker(httpClient, serviceDiscovery);
    }

    @Bean
    public Scheduler scheduler(@NonNull JobStore jobStore,
                               @NonNull SchedulerLock schedulerLock) {
        return new Scheduler(jobStore, schedulerLock);
    }

    @Bean
    public static SmartLifecycle schedulerLifecycle(@NonNull Scheduler scheduler) {
        return new SmartLifecycle() {

            @Override
            public void start() {
                scheduler.start();
            }

            @Override
            public void stop() {
                scheduler.shutdown().join();
            }

            @Override
            public boolean isRunning() {
                return false;
            }
        };
    }

}
