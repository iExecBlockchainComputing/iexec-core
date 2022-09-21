package com.iexec.core.registry;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = PlatformRegistryConfiguration.class)
@TestPropertySource(properties = {
    "sms.scone=http://scone-sms",
    "sms.gramine=http://gramine-sms"
    })
public class PlatformRegistryConfigurationTests {

    @Autowired
    PlatformRegistryConfiguration platformRegistryConfiguration;
    
    @Test
    void shouldGetValues() {
        Assertions.assertThat(platformRegistryConfiguration.getSconeSms())
            .isEqualTo("http://scone-sms");
        Assertions.assertThat(platformRegistryConfiguration.getGramineSms())
            .isEqualTo("http://gramine-sms");
    }

}
