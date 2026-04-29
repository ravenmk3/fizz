package ravenworks.fizz.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = "ravenworks.fizz")
@EnableJpaRepositories(basePackages = "ravenworks.fizz.domain.repository")
@EntityScan(basePackages = "ravenworks.fizz.domain.entity")
public class FizzApplication {

    public static void main(String[] args) {
        SpringApplication.run(FizzApplication.class, args);
    }
}
