/*
 * Copyright 2020 IEXEC BLOCKCHAIN TECH
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

package com.iexec.core.workflow;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class WorkflowTests {



    @Test
    public void shouldAllBeValidTransitions(){
        // test simple case
        assertThat(DummyWorkflow.getInstance().isValidTransition("STATUS_1", "STATUS_2")).isTrue();

        // test multiple targets
        assertThat(DummyWorkflow.getInstance().isValidTransition("STATUS_2", "STATUS_3")).isTrue();
        assertThat(DummyWorkflow.getInstance().isValidTransition("STATUS_2", "STATUS_4")).isTrue();

        // test same 'to' state
        assertThat(DummyWorkflow.getInstance().isValidTransition("STATUS_3", "STATUS_5")).isTrue();
        assertThat(DummyWorkflow.getInstance().isValidTransition("STATUS_4", "STATUS_5")).isTrue();
    }

    @Test
    public void shouldAllBeUnvalidTransitions(){
        // test non existing transition
        assertThat(DummyWorkflow.getInstance().isValidTransition("STATUS_1", "STATUS_3")).isFalse();
        assertThat(DummyWorkflow.getInstance().isValidTransition("STATUS_1", "STATUS_5")).isFalse();

        // test reverse transition
        assertThat(DummyWorkflow.getInstance().isValidTransition("STATUS_2", "STATUS_1")).isFalse();

        // test non existing state
        assertThat(DummyWorkflow.getInstance().isValidTransition("STATUS_1", "DUMMY")).isFalse();
        assertThat(DummyWorkflow.getInstance().isValidTransition("DUMMY", "STATUS_2")).isFalse();
    }

    @Test
    public void shouldAllBorderCasesBeUnvalid(){
        assertThat(DummyWorkflow.getInstance().isValidTransition("STATUS_1", "")).isFalse();
        assertThat(DummyWorkflow.getInstance().isValidTransition("", "STATUS_2")).isFalse();

        assertThat(DummyWorkflow.getInstance().isValidTransition("STATUS_2", null)).isFalse();
        assertThat(DummyWorkflow.getInstance().isValidTransition(null, "STATUS_2")).isFalse();

        assertThat(DummyWorkflow.getInstance().isValidTransition(null, null)).isFalse();
    }
}
