package ravenworks.fizz.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;


@SpringBootApplication(scanBasePackages = "ravenworks.fizz")
@EntityScan(basePackages = "ravenworks.fizz.domain.entity")
@EnableJpaRepositories(basePackages = "ravenworks.fizz.domain.repository")
public class FizzApplication {

    static void main(String[] args) {
        SpringApplication.run(FizzApplication.class, args);
    }

}
