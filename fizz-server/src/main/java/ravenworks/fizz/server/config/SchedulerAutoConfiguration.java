package ravenworks.fizz.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ravenworks.fizz.engine.discovery.JobTypeRegistry;
import ravenworks.fizz.engine.discovery.ServiceDiscovery;
import ravenworks.fizz.engine.invoker.JdkHttpServiceInvoker;
import ravenworks.fizz.engine.runtime.NotificationDispatcher;
import ravenworks.fizz.engine.runtime.Scheduler;
import ravenworks.fizz.engine.runtime.SchedulerCoordinator;
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
    public JdkHttpServiceInvoker jdkHttpServiceInvoker() {
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .build();
        return new JdkHttpServiceInvoker(httpClient);
    }

    @Bean
    public NotificationDispatcher notificationDispatcher(JobNotificationStore notificationStore,
                                                         FizzSchedulerProperties properties) {
        return new NotificationDispatcher(notificationStore, properties);
    }

    @Bean
    public SchedulerCoordinator schedulerCoordinator(SchedulerLockStore lockStore,
                                                     FizzSchedulerProperties properties) {
        return new SchedulerCoordinator(lockStore, properties);
    }

    @Bean
    public Scheduler scheduler(SchedulerLockStore lockStore, ActiveJobStore activeJobStore,
                               JobStore jobStore, TaskStore taskStore,
                               FizzSchedulerProperties properties,
                               JobTypeRegistry jobTypeRegistry, ServiceDiscovery serviceDiscovery,
                               NotificationDispatcher notificationDispatcher,
                               JdkHttpServiceInvoker jdkHttpServiceInvoker) {
        return new Scheduler(lockStore, activeJobStore, jobStore, taskStore, properties,
                jobTypeRegistry, serviceDiscovery, notificationDispatcher,
                jdkHttpServiceInvoker, jdkHttpServiceInvoker);
    }

    @Bean
    public SmartLifecycle schedulerLifecycle(SchedulerCoordinator coordinator, Scheduler scheduler) {
        coordinator.setScheduler(scheduler);

        return new SmartLifecycle() {

            private volatile boolean running = false;

            @Override
            public void start() {
                coordinator.start();
                scheduler.start();
                running = true;
            }

            @Override
            public void stop() {
                scheduler.shutdown();
                scheduler.awaitTermination(30_000);
                coordinator.shutdown();
                coordinator.awaitTermination(5_000);
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
