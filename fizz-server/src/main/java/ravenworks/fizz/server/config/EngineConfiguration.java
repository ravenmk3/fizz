package ravenworks.fizz.server.config;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ravenworks.fizz.domain.repository.JobNotificationRepository;
import ravenworks.fizz.engine.discovery.ServiceDiscovery;
import ravenworks.fizz.engine.discovery.ServiceHealthIndicator;
import ravenworks.fizz.engine.discovery.ServiceLoadBalancer;
import ravenworks.fizz.engine.invoker.*;
import ravenworks.fizz.engine.lock.SchedulerLock;
import ravenworks.fizz.engine.runtime.Scheduler;
import ravenworks.fizz.engine.store.JobStore;
import ravenworks.fizz.engine.store.JobTypeStore;

import java.net.http.HttpClient;


@Slf4j
@Configuration
public class EngineConfiguration {

    @Bean
    public static ServiceLoadBalancer serviceLoadBalancer(@NonNull ServiceDiscovery serviceDiscovery) {
        return new ServiceLoadBalancer(serviceDiscovery);
    }

    @Bean
    public static JdkHttpServiceClient serviceClient(@NonNull HttpClient httpClient,
                                                     @NonNull ServiceLoadBalancer loadBalancer) {
        return new JdkHttpServiceClient(httpClient, loadBalancer);
    }

    @Bean
    public static TaskInvoker taskInvoker(@NonNull ServiceClient serviceClient) {
        return new TaskInvokerImpl(serviceClient);
    }

    @Bean
    public static NotificationInvoker notificationInvoker(@NonNull ServiceClient serviceClient) {
        return new NotificationInvokerImpl(serviceClient);
    }

    @Bean
    public Scheduler scheduler(@NonNull JobStore jobStore,
                               @NonNull SchedulerLock schedulerLock,
                               @NonNull TaskInvoker taskInvoker,
                               @NonNull JobTypeStore jobTypeRegistry,
                               @NonNull ServiceHealthIndicator healthIndicator,
                               @NonNull JobNotificationRepository notificationRepo,
                               @NonNull NotificationInvoker notificationInvoker) {
        return new Scheduler(jobStore, schedulerLock, taskInvoker,
                jobTypeRegistry, healthIndicator, notificationRepo, notificationInvoker);
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
