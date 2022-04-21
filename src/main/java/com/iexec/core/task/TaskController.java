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

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;
import static org.springframework.http.ResponseEntity.*;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import com.iexec.core.replicate.*;
import com.iexec.core.stdout.ReplicateStdout;
import com.iexec.core.stdout.StdoutService;
import com.iexec.core.stdout.TaskStdout;

import org.springframework.hateoas.Link;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

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
        Optional<Task> optionalTask = taskService.getTaskByChainTaskId(chainTaskId);
        if (optionalTask.isEmpty()) {
            return status(HttpStatus.NOT_FOUND).build();
        }
        Task task = optionalTask.get();
        return replicatesService.getReplicatesList(chainTaskId)
                .map(replicatesList -> {
                    TaskModel taskModel = TaskModel.fromEntity(task);
                    List<Link> replicates = replicatesList.getReplicates().stream()
                            .map(replicate -> linkTo(methodOn(TaskController.class)
                                    .getTaskReplicate(chainTaskId, replicate.getWalletAddress()))
                                    .withSelfRel())
                            .collect(Collectors.toList());
                    taskModel.setReplicates(replicates);
                    return ok(taskModel);
                })
                .orElse(notFound().build());
    }

    @GetMapping("/tasks/{chainTaskId}/replicates/{walletAddress}")
    public ResponseEntity<ReplicateModel> getTaskReplicate(@PathVariable("chainTaskId") String chainTaskId,
                                                           @PathVariable("walletAddress") String walletAddress) {
        return replicatesService.getReplicate(chainTaskId, walletAddress)
                .map(replicate -> {
                    ReplicateModel replicateModel = ReplicateModel.fromEntity(replicate);
                    if (replicate.isAppComputeStdoutPresent()) {
                        Link stdout = linkTo(methodOn(TaskController.class)
                                .getReplicateStdout(replicate.getChainTaskId(),
                                        replicate.getWalletAddress()))
                                .withSelfRel();
                        replicateModel.setAppComputeStdout(stdout);
                    }
                    return ok(replicateModel);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/tasks/{chainTaskId}/stdout")
    public ResponseEntity<TaskStdout> getTaskStdout(@PathVariable("chainTaskId") String chainTaskId) {
        return stdoutService.getTaskStdout(chainTaskId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/tasks/{chainTaskId}/stdout/{walletAddress}")
    public ResponseEntity<ReplicateStdout> getReplicateStdout(
                @PathVariable("chainTaskId") String chainTaskId,
                @PathVariable("walletAddress") String walletAddress) {
        return stdoutService.getReplicateStdout(chainTaskId, walletAddress)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
