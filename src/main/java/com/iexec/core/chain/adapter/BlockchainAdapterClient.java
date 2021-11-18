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

package com.iexec.core.chain.adapter;


import com.iexec.common.chain.ChainTask;
import com.iexec.common.chain.adapter.CommandStatus;
import com.iexec.common.chain.adapter.args.TaskFinalizeArgs;
import com.iexec.common.config.PublicChainConfig;
import feign.FeignException;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@FeignClient(
        name = "BlockchainAdapterClient",
        url = "#{blockchainAdapterClientConfig.url}",
        configuration = BlockchainAdapterClientFeignConfig.class
)
public interface BlockchainAdapterClient {

    @GetMapping("/tasks/{chainTaskId}")
    ResponseEntity<ChainTask> getTask(
            @PathVariable String chainTaskId) throws FeignException;

    @PostMapping("/tasks/initialize")
    ResponseEntity<String> requestInitializeTask(
            @RequestParam String chainDealId,
            @RequestParam int taskIndex) throws FeignException;

    @GetMapping("/tasks/initialize/{chainTaskId}/status")
    ResponseEntity<CommandStatus> getStatusForInitializeTaskRequest(
            @PathVariable String chainTaskId) throws FeignException;

    @PostMapping("/tasks/finalize/{chainTaskId}")
    ResponseEntity<String> requestFinalizeTask(
            @PathVariable String chainTaskId,
            @RequestBody TaskFinalizeArgs args)throws FeignException ;

    @GetMapping("/tasks/finalize/{chainTaskId}/status")
    ResponseEntity<CommandStatus> getStatusForFinalizeTaskRequest(
            @PathVariable String chainTaskId) throws FeignException;

    @GetMapping("/config/chain")
    ResponseEntity<PublicChainConfig> getPublicChainConfig() throws FeignException;
}