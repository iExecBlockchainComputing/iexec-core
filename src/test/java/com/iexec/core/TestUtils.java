/*
 * Copyright 2021-2025 IEXEC BLOCKCHAIN TECH
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

package com.iexec.core;

import com.iexec.commons.poco.tee.TeeUtils;
import com.iexec.commons.poco.utils.BytesUtils;
import com.iexec.core.task.Task;
import com.iexec.core.task.TaskStatus;
import com.iexec.core.task.TaskStatusChange;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

public class TestUtils {
    public static final String CHAIN_DEAL_ID = "0xd82223e5feff6720792ffed1665e980da95e5d32b177332013eaba8edc07f31c";
    public static final String CHAIN_TASK_ID = "0x65bc5e94ed1486b940bd6cc0013c418efad58a0a52a3d08cee89faaa21970426";

    public static final String WORKER_ADDRESS = "0x87ae2b87b5db23830572988fb1f51242fbc471ce";
    public static final String WALLET_WORKER_1 = "0x1a69b2eb604db8eba185df03ea4f5288dcbbd248";
    public static final String WALLET_WORKER_2 = "0x2ab2674aa374fe6415d11f0a8fcbd8027fc1e6a9";
    public static final String WALLET_WORKER_3 = "0x3a3406e69adf886c442ff1791cbf67cea679275d";
    public static final String WALLET_WORKER_4 = "0x4aef50214110fdad4e8b9128347f2ba1ec72f614";

    public static final String DAPP_NAME = "dappName";
    public static final String COMMAND_LINE = "commandLine";
    public static final String NO_TEE_TAG = BytesUtils.EMPTY_HEX_STRING_32;
    public static final String TEE_TAG = TeeUtils.TEE_SCONE_ONLY_TAG; //any supported TEE tag
    public static final String RESULT_LINK = "/ipfs/the_result_string";

    public static Task getStubTask() {
        final Task task = new Task(CHAIN_DEAL_ID, 0, DAPP_NAME, COMMAND_LINE, 1, 60000, NO_TEE_TAG);
        task.setContributionDeadline(Date.from(Instant.now().plus(1, ChronoUnit.MINUTES)));
        task.setFinalDeadline(Date.from(Instant.now().plus(1, ChronoUnit.MINUTES)));
        return task;
    }

    public static Task getStubTask(TaskStatus status) {
        final Task task = getStubTask();
        task.setCurrentStatus(status);
        task.getDateStatusList().add(TaskStatusChange.builder().status(status).build());
        return task;
    }
}
