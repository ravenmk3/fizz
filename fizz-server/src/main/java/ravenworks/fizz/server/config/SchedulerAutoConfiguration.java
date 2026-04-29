package ravenworks.fizz.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ravenworks.fizz.engine.component.NotificationDispatcher;
import ravenworks.fizz.engine.component.Scheduler;
import ravenworks.fizz.engine.discovery.JobTypeRegistry;
import ravenworks.fizz.engine.discovery.ServiceDiscovery;
import ravenworks.fizz.engine.store.*;
import ravenworks.fizz.service.config.FizzSchedulerProperties;

import java.net.http.HttpClient;

@Configuration
public class SchedulerAutoConfiguration {

    @Bean
    @ConfigurationProperties("fizz")
    public FizzSchedulerProperties fizzSchedulerProperties() {
        return new FizzSchedulerProperties();
    }

    @Bean
    public HttpClient schedulerHttpClient() {
        return HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    @Bean
    public NotificationDispatcher notificationDispatcher(JobNotificationStore notificationStore,
                                                         FizzSchedulerProperties properties) {
        return new NotificationDispatcher(notificationStore, properties);
    }

    @Bean
    public Scheduler scheduler(SchedulerLockStore lockStore, ActiveJobStore activeJobStore,
                                JobStore jobStore, TaskStore taskStore,
                                FizzSchedulerProperties properties,
                                JobTypeRegistry jobTypeRegistry, ServiceDiscovery serviceDiscovery,
                                NotificationDispatcher notificationDispatcher,
                                HttpClient schedulerHttpClient) {
        return new Scheduler(lockStore, activeJobStore, jobStore, taskStore, properties,
                jobTypeRegistry, serviceDiscovery, notificationDispatcher, schedulerHttpClient);
    }

    @Bean
    public SmartLifecycle schedulerLifecycle(Scheduler scheduler) {
        return new SmartLifecycle() {
            private volatile boolean running = false;

            @Override
            public void start() {
                scheduler.start();
                running = true;
            }

            @Override
            public void stop() {
                scheduler.shutdown();
                scheduler.awaitTermination(30_000);
                running = false;
            }

            @Override
            public boolean isRunning() {
                return running;
            }

            @Override
            public int getPhase() {
                return Integer.MAX_VALUE;
            }
        };
    }
}
