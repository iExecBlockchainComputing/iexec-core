/*
 * Copyright 2020-2024 IEXEC BLOCKCHAIN TECH
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

package com.iexec.core.result;

import com.iexec.commons.poco.chain.WorkerpoolAuthorization;
import com.iexec.commons.poco.task.TaskDescription;
import com.iexec.core.chain.SignatureService;
import com.iexec.core.configuration.ResultRepositoryConfiguration;
import com.iexec.core.task.Task;
import com.iexec.core.task.TaskService;
import com.iexec.resultproxy.api.ResultProxyClient;
import feign.FeignException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import static com.iexec.commons.poco.utils.BytesUtils.EMPTY_ADDRESS;

@Slf4j
@Service
public class ResultService {

    private final ResultRepositoryConfiguration resultRepositoryConfiguration;
    private final SignatureService signatureService;
    private final TaskService taskService;

    public ResultService(final ResultRepositoryConfiguration resultRepositoryConfiguration, final SignatureService signatureService, final TaskService taskService) {
        this.resultRepositoryConfiguration = resultRepositoryConfiguration;
        this.signatureService = signatureService;
        this.taskService = taskService;
    }

    @Retryable(value = FeignException.class)
    public boolean isResultUploaded(final TaskDescription taskDescription) {
        final String chainTaskId = taskDescription.getChainTaskId();
        final ResultProxyClient resultProxyClient = resultRepositoryConfiguration.createResultProxyClientFromURL(taskDescription.getDealParams().getIexecResultStorageProxy());
        final String enclaveChallenge = taskService.getTaskByChainTaskId(chainTaskId).map(Task::getEnclaveChallenge).orElse(EMPTY_ADDRESS);
        final WorkerpoolAuthorization workerpoolAuthorization = signatureService.createAuthorization(signatureService.getAddress(), chainTaskId, enclaveChallenge);
        final String resultProxyToken = resultProxyClient.getJwt(workerpoolAuthorization.getSignature().getValue(), workerpoolAuthorization);
        if (resultProxyToken.isEmpty()) {
            log.error("isResultUploaded failed (getResultProxyToken) [chainTaskId:{}]", chainTaskId);
            return false;
        }

        resultProxyClient.isResultUploaded(resultProxyToken, chainTaskId);
        return true;
    }

    @Recover
    private boolean isResultUploaded(final FeignException e, final TaskDescription taskDescription) {
        log.error("Cannot check isResultUploaded after multiple retries [chainTaskId:{}]", taskDescription.getChainTaskId(), e);
        return false;
    }
}
