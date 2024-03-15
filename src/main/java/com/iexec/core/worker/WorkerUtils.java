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

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Slf4j
public class WorkerUtils {

    /**
     * Utility function to log a message in the event of unauthorized access
     * Either because the worker is not whitelisted or because the worker address was not found in the JWT token
     *
     * @param workerWalletAddress Address of the worker who attempted access
     */
    public static void emitWarnOnUnAuthorizedAccess(String workerWalletAddress) {
        final String workerAddress = StringUtils.isEmpty(workerWalletAddress) ? "NotAvailable" : workerWalletAddress;
        log.warn("Worker is not allowed to join this workerpool [workerAddress:{}]", workerAddress);
    }
}
