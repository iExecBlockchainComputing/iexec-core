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

package com.iexec.core.task;

import com.iexec.common.replicate.ComputeLogs;
import com.iexec.core.replicate.Replicate;
import com.iexec.core.replicate.ReplicateModel;
import com.iexec.core.replicate.ReplicatesService;
import com.iexec.core.logs.TaskLogsService;
import com.iexec.core.logs.TaskLogs;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.stream.Collectors;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;
import static org.springframework.http.ResponseEntity.notFound;
import static org.springframework.http.ResponseEntity.ok;

@RestController
public class TaskController {

    private final TaskService taskService;
    private final ReplicatesService replicatesService;
    private final TaskLogsService taskLogsService;

    public TaskController(TaskService taskService,
                          ReplicatesService replicatesService,
                          TaskLogsService taskLogsService) {
        this.taskService = taskService;
        this.replicatesService = replicatesService;
        this.taskLogsService = taskLogsService;
    }

    // TODO: add auth

    @GetMapping("/tasks/{chainTaskId}")
    public ResponseEntity<TaskModel> getTask(@PathVariable("chainTaskId") String chainTaskId) {
        return taskService.getTaskByChainTaskId(chainTaskId).map(task -> {
            TaskModel taskModel = TaskModel.fromEntity(task);
            if (replicatesService.hasReplicatesList(chainTaskId)) {
                taskModel.setReplicates(replicatesService.getReplicates(chainTaskId)
                        .stream()
                        .map(this::buildReplicateModel)
                        .collect(Collectors.toList()));
            }
            return ok(taskModel);
        }).orElse(notFound().build());
    }

    @GetMapping("/tasks/{chainTaskId}/replicates/{walletAddress}")
    public ResponseEntity<ReplicateModel> getTaskReplicate(@PathVariable("chainTaskId") String chainTaskId,
                                                           @PathVariable("walletAddress") String walletAddress) {
        return replicatesService.getReplicate(chainTaskId, walletAddress)
                .map(replicate -> ok(buildReplicateModel(replicate)))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Converts a replicate to a replicate model with links.
     * <p>
     * Note: Currently using links as string to avoid spring hateoas
     * dependencies in client.
     *
     * @param replicate replicate entity
     * @return replicate model
     */
    ReplicateModel buildReplicateModel(Replicate replicate) {
        ReplicateModel replicateModel = ReplicateModel.fromEntity(replicate);
        if (replicate.isAppComputeLogsPresent()) {
            String logs = linkTo(methodOn(TaskController.class)
                    .getComputeLogs(replicate.getChainTaskId(),
                            replicate.getWalletAddress()))
                    .withRel("logs")//useless, but helps understandability
                    .getHref();
            replicateModel.setAppLogs(logs);
        }
        String self = linkTo(methodOn(TaskController.class)
                .getTaskReplicate(replicate.getChainTaskId(),
                        replicate.getWalletAddress()))
                .withSelfRel().getHref();
        replicateModel.setSelf(self);
        return replicateModel;
    }

    @GetMapping(path = {
            "/tasks/{chainTaskId}/stdout",  // @Deprecated
            "/tasks/{chainTaskId}/logs"
    })
    public ResponseEntity<TaskLogs> getTaskLogs(@PathVariable("chainTaskId") String chainTaskId) {
        return taskLogsService.getTaskLogs(chainTaskId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping(path = {
            "/tasks/{chainTaskId}/replicates/{walletAddress}/stdout",   // @Deprecated
            "/tasks/{chainTaskId}/replicates/{walletAddress}/logs"
    })
    public ResponseEntity<ComputeLogs> getComputeLogs(
            @PathVariable("chainTaskId") String chainTaskId,
            @PathVariable("walletAddress") String walletAddress) {
        return taskLogsService.getComputeLogs(chainTaskId, walletAddress)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
