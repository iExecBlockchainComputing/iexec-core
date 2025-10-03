/*
 * Copyright 2022-2025 IEXEC BLOCKCHAIN TECH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.iexec.core.registry;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformRegistryConfigurationTests {

    private Validator validator;

    @BeforeEach
    void setUp() {
        try (final ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    @Test
    void shouldAcceptUrlValues() {
        final PlatformRegistryConfiguration config = new PlatformRegistryConfiguration("http://scone-sms", "http://gramine-sms", "http://tdx-sms");
        assertThat(validator.validate(config)).isEmpty();
        assertThat(config.getScone()).isEqualTo("http://scone-sms");
        assertThat(config.getGramine()).isEqualTo("http://gramine-sms");
        assertThat(config.getTdx()).isEqualTo("http://tdx-sms");
    }

    @Test
    void shouldAcceptBlankValues() {
        final PlatformRegistryConfiguration config = new PlatformRegistryConfiguration("", "", "");
        assertThat(validator.validate(config)).isEmpty();
    }

    @Test
    void shouldRejectNullValues() {
        final PlatformRegistryConfiguration config = new PlatformRegistryConfiguration(null, null, null);
        assertThat(validator.validate(config))
                .extracting(ConstraintViolation::getMessage)
                .containsExactly("must not be null", "must not be null", "must not be null");
    }

    @Test
    void shouldRejectNonUrlValues() {
        final PlatformRegistryConfiguration config = new PlatformRegistryConfiguration("a", "b", "c");
        assertThat(validator.validate(config))
                .extracting(ConstraintViolation::getMessage)
                .containsExactly("must be a valid URL", "must be a valid URL", "must be a valid URL");
    }

}
