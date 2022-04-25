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

import com.iexec.core.replicate.Replicate;
import com.iexec.core.replicate.ReplicateModel;
import com.iexec.core.replicate.ReplicatesService;
import com.iexec.core.stdout.ReplicateStdout;
import com.iexec.core.stdout.StdoutService;
import com.iexec.core.stdout.TaskStdout;
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

    private TaskService taskService;
    private ReplicatesService replicatesService;
    private StdoutService stdoutService;

    public TaskController(TaskService taskService,
                          ReplicatesService replicatesService,
                          StdoutService stdoutService) {
        this.taskService = taskService;
        this.replicatesService = replicatesService;
        this.stdoutService = stdoutService;
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
    private ReplicateModel buildReplicateModel(Replicate replicate) {
        ReplicateModel replicateModel = ReplicateModel.fromEntity(replicate);
        if (replicate.isAppComputeStdoutPresent()) {
            String stdout = linkTo(methodOn(TaskController.class)
                    .getReplicateStdout(replicate.getChainTaskId(),
                            replicate.getWalletAddress()))
                    .withRel("stdout")//useless, but helps understandability
                    .getHref();
            replicateModel.setAppStdout(stdout);
        }
        String self = linkTo(methodOn(TaskController.class)
                .getTaskReplicate(replicate.getChainTaskId(),
                        replicate.getWalletAddress()))
                .withSelfRel().getHref();
        replicateModel.setSelf(self);
        return replicateModel;
    }

    @GetMapping("/tasks/{chainTaskId}/stdout")
    public ResponseEntity<TaskStdout> getTaskStdout(@PathVariable("chainTaskId") String chainTaskId) {
        return stdoutService.getTaskStdout(chainTaskId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/tasks/{chainTaskId}/replicates/{walletAddress}/stdout")
    public ResponseEntity<ReplicateStdout> getReplicateStdout(
            @PathVariable("chainTaskId") String chainTaskId,
            @PathVariable("walletAddress") String walletAddress) {
        return stdoutService.getReplicateStdout(chainTaskId, walletAddress)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
