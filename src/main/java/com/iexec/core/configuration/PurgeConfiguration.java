package com.iexec.core.configuration;

import com.iexec.common.lifecycle.purge.PurgeService;
import com.iexec.common.lifecycle.purge.Purgeable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
@Slf4j
public class PurgeConfiguration {
    /**
     * Creates a {@link PurgeService} bean, with a list of all {@link Purgeable} beans as a parameter.
     * <p>
     * If no {@link Purgeable} bean is known, then an empty list is passed as a parameter.
     * This is a special case of Spring IoC, please see
     * <a href="https://docs.spring.io/spring-framework/docs/current/reference/html/core.html#beans-autowired-annotation">Spring documentation</a>.
     * @param purgeableServices List of services that can be purged on a task completion
     * @return An instance of {@link PurgeService} containing a list of all {@link Purgeable} beans.
     */
    @Bean
    PurgeService purgeService(List<Purgeable> purgeableServices) {
        return new PurgeService(purgeableServices);
    }
}
