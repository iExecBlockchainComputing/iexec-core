package com.iexec.core.task;

import com.iexec.common.chain.ContributionAuthorization;
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

import java.util.Optional;

import static org.springframework.http.ResponseEntity.status;

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

        ReplicatesList replicates = replicatesService.getReplicatesList(chainTaskId).orElseGet(ReplicatesList::new);

        return createTaskModel(task, replicates).
                <ResponseEntity>map(ResponseEntity::ok).
                orElseGet(() -> status(HttpStatus.NO_CONTENT).build());
    }

    @RequestMapping(method = RequestMethod.GET, path = "/tasks/available")
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
        ContributionAuthorization authorization = signatureService.createAuthorization(
                workerWalletAddress, task.getChainTaskId(), TeeUtils.isTrustedExecutionTag(task.getTag()));

        return Optional.of(authorization).
                <ResponseEntity>map(ResponseEntity::ok)
                .orElseGet(() -> status(HttpStatus.NO_CONTENT).build());
    }

    private Optional<TaskModel> createTaskModel(Task task,
                                                ReplicatesList replicatesList) {
        return Optional.of(TaskModel.builder()
                .id(task.getId())
                .version(task.getVersion())
                .chainTaskId(task.getChainTaskId())
                .dappType(task.getDappType())
                .dappName(task.getDappName())
                .commandLine(task.getCommandLine())
                .currentStatus(task.getCurrentStatus())
                .dateStatusList(task.getDateStatusList())
                .replicates(replicatesList.getReplicates())
                .trust(task.getTrust())
                .numWorkersNeeded(task.getNumWorkersNeeded())
                .uploadingWorkerWalletAddress(task.getUploadingWorkerWalletAddress())
                .consensus(task.getConsensus())
                .build()
        );
    }

}

