/*
 * Copyright 2024-2024 IEXEC BLOCKCHAIN TECH
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

package com.iexec.core.worker;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class WorkerUtilsTests {

    @Test
    void testEmitOnErrorWithWorkerAddress(CapturedOutput output) {
        final String workerAddress = "0x9d693a8fd049c607a6";
        WorkerUtils.emitWarnOnUnAuthorizedAccess(workerAddress);
        assertThat(output.getOut()).contains("Worker is not allowed to join this workerpool [workerAddress:0x9d693a8fd049c607a6]");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {""})
    void testEmitOnErrorWithEmptyWorkerAddress(String value, CapturedOutput output) {
        WorkerUtils.emitWarnOnUnAuthorizedAccess(value);
        assertThat(output.getOut()).contains("Worker is not allowed to join this workerpool [workerAddress:NotAvailable]");
    }
}
