package ravenworks.fizz.server.config;

import lombok.NonNull;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;


/**
 * @author Raven
 */
@Configuration
public class TransactionConfiguration {

    @Bean
    public TransactionTemplate transactionTemplate(@NonNull PlatformTransactionManager transactionManager) {
        return new TransactionTemplate(transactionManager);
    }

}
