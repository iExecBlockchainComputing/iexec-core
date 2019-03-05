package com.iexec.core.task;

import com.iexec.common.chain.ContributionAuthorization;
import com.iexec.common.disconnection.InterruptedReplicateModel;
import com.iexec.common.disconnection.RecoveredReplicateModel;
import com.iexec.common.tee.TeeUtils;
import com.iexec.core.chain.SignatureService;
import com.iexec.core.replicate.Replicate;
import com.iexec.core.replicate.ReplicatesList;
import com.iexec.core.replicate.ReplicatesService;
import com.iexec.core.security.JwtTokenProvider;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

import static org.springframework.http.ResponseEntity.status;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;


@Slf4j
@RestController
public class TaskController {

    private TaskService taskService;
    private SignatureService signatureService;
    private ReplicatesService replicatesService;
    private JwtTokenProvider jwtTokenProvider;


    public TaskController(TaskService taskService,
                          SignatureService signatureService,
                          ReplicatesService replicatesService,
                          JwtTokenProvider jwtTokenProvider) {
        this.taskService = taskService;
        this.signatureService = signatureService;
        this.replicatesService = replicatesService;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @GetMapping("/tasks/{chainTaskId}")
    public ResponseEntity getTask(@PathVariable("chainTaskId") String chainTaskId) {
        Optional<Task> optionalTask = taskService.getTaskByChainTaskId(chainTaskId);
        if (!optionalTask.isPresent()) {
            return status(HttpStatus.NO_CONTENT).build();
        }
        Task task = optionalTask.get();

        Optional<ReplicatesList> optionalReplicates = replicatesService.getReplicatesList(chainTaskId);

        TaskModel taskModel;
        if (!optionalReplicates.isPresent()) {
            taskModel = new TaskModel(task, new ReplicatesList().getReplicates());
        } else {
            taskModel = new TaskModel(task, optionalReplicates.get().getReplicates());
        }

        return ResponseEntity.ok(taskModel);
    }

    @GetMapping("/tasks/available")
    public ResponseEntity getAvailableReplicate(@RequestParam(name = "blockNumber") long blockNumber,
                                                @RequestHeader("Authorization") String bearerToken) {
        String workerWalletAddress = jwtTokenProvider.getWalletAddressFromBearerToken(bearerToken);
        if (workerWalletAddress.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED.value()).build();
        }

        // get available replicate
        Optional<Replicate> optional = taskService.getAvailableReplicate(blockNumber, workerWalletAddress);
        if (!optional.isPresent()) {
            return status(HttpStatus.NO_CONTENT).build();
        }
        Replicate replicate = optional.get();

        // get associated task
        Optional<Task> taskOptional = taskService.getTaskByChainTaskId(replicate.getChainTaskId());
        if (!taskOptional.isPresent()) {
            return status(HttpStatus.NO_CONTENT).build();
        }
        Task task = taskOptional.get();

        // generate contribution authorization
        Optional<ContributionAuthorization> authorization = signatureService.createAuthorization(
                workerWalletAddress, task.getChainTaskId(), TeeUtils.isTrustedExecutionTag(task.getTag()));

        return authorization
                .<ResponseEntity>map(ResponseEntity::ok)
                .orElseGet(() -> status(HttpStatus.NO_CONTENT).build());
    }

    @GetMapping("/tasks/interrupted")
    public ResponseEntity<List<InterruptedReplicateModel>> getInterruptedReplicates(
    @RequestHeader("Authorization") String bearerToken) {
        String workerWalletAddress = jwtTokenProvider.getWalletAddressFromBearerToken(bearerToken);
        if (workerWalletAddress.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED.value()).build();
        }

        List<InterruptedReplicateModel> interruptedReplicateList =
                taskService.getInterruptedButStillActiveReplicates(workerWalletAddress);

        return ResponseEntity.ok(interruptedReplicateList);
    }

    @PostMapping(value="/tasks/recovered")
    public void recoverReplicates(@RequestBody() List<RecoveredReplicateModel> recoveredReplicates,
                                  @RequestHeader("Authorization") String bearerToken) {

        String workerWalletAddress = jwtTokenProvider.getWalletAddressFromBearerToken(bearerToken);
        if (workerWalletAddress.isEmpty()) {
            return;
        }

        taskService.recoverInterruptedReplicates(workerWalletAddress, recoveredReplicates);
    }
}

