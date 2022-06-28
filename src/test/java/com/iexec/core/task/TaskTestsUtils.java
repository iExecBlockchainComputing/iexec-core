/*
 * Copyright 2021 IEXEC BLOCKCHAIN TECH
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

package com.iexec.core.task;

import com.iexec.common.tee.TeeUtils;
import com.iexec.common.utils.BytesUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

public class TaskTestsUtils {
    public final static String WALLET_WORKER_1 = "0x1a69b2eb604db8eba185df03ea4f5288dcbbd248";
    public final static String WALLET_WORKER_2 = "0x2a69b2eb604db8eba185df03ea4f5288dcbbd248";

    public final static String CHAIN_DEAL_ID = "0xd82223e5feff6720792ffed1665e980da95e5d32b177332013eaba8edc07f31c";
    public final static String CHAIN_TASK_ID = "0x65bc5e94ed1486b940bd6cc0013c418efad58a0a52a3d08cee89faaa21970426";

    public final static String DAPP_NAME = "dappName";
    public final static String COMMAND_LINE = "commandLine";
    public final static String NO_TEE_TAG = BytesUtils.EMPTY_HEX_STRING_32;
    public final static String TEE_TAG = TeeUtils.TEE_TAG;
    public final static String RESULT_LINK = "/ipfs/the_result_string";

    public static Task getStubTask(long maxExecutionTime) {
        Task task = new Task(CHAIN_DEAL_ID, 0, DAPP_NAME, COMMAND_LINE, 0, maxExecutionTime, NO_TEE_TAG);
        task.setFinalDeadline(Date.from(Instant.now().plus(1, ChronoUnit.MINUTES)));
        return  task;
    }
}
