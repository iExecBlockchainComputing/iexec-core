package com.iexec.core.configuration;

import com.iexec.common.utils.purge.PurgeService;
import com.iexec.common.utils.purge.Purgeable;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class PurgeConfiguration {
    @Bean
    PurgeService purgeService(List<Purgeable> purgeableServices) {
        return new PurgeService(purgeableServices);
    }
}
