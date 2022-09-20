package com.iexec.core.configuration;

import com.iexec.common.lifecycle.purge.PurgeService;
import com.iexec.common.lifecycle.purge.Purgeable;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class PurgeConfigurationTests {
    final PurgeConfiguration purgeConfiguration = new PurgeConfiguration();

    @Test
    void createPurgeService() {
        final List<Purgeable> purgeables = List.of(
                mock(Purgeable.class),
                mock(Purgeable.class),
                mock(Purgeable.class)
        );
        final PurgeService purgeService = purgeConfiguration.purgeService(purgeables);
        assertNotNull(purgeService);
    }
}