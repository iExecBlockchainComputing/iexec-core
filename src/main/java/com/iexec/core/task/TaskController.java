package com.iexec.core.task;

import com.iexec.common.chain.ContributionAuthorization;
import com.iexec.common.replicate.AvailableReplicateModel;
import com.iexec.core.chain.SignatureService;
import com.iexec.core.replicate.Replicate;
import com.iexec.core.replicate.ReplicatesList;
import com.iexec.core.replicate.ReplicatesService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

import static org.springframework.http.ResponseEntity.ok;
import static org.springframework.http.ResponseEntity.status;

@Slf4j
@RestController
public class TaskController {

    private TaskService taskService;
    private SignatureService signatureService;
    private ReplicatesService replicatesService;

    public TaskController(TaskService taskService,
                          SignatureService signatureService,
                          ReplicatesService replicatesService) {
        this.taskService = taskService;
        this.signatureService = signatureService;
        this.replicatesService = replicatesService;
    }

    @GetMapping("/tasks/{chainTaskId}")
    public ResponseEntity getTask(@PathVariable("chainTaskId") String chainTaskId) {
        Optional<Task> optionalTask = taskService.getTaskByChainTaskId(chainTaskId);
        if (!optionalTask.isPresent()) {
            return status(HttpStatus.NO_CONTENT).build();
        }
        Task task = optionalTask.get();

        Optional<ReplicatesList> optionalReplicates = replicatesService.getReplicatesList(chainTaskId);
        if(!optionalReplicates.isPresent()){
            return createTaskModel(task, new ReplicatesList()).
                    <ResponseEntity>map(ResponseEntity::ok).
                    orElseGet(() -> status(HttpStatus.NO_CONTENT).build());
        }

        return createTaskModel(task, optionalReplicates.get()).
                <ResponseEntity>map(ResponseEntity::ok).
                orElseGet(() -> status(HttpStatus.NO_CONTENT).build());
    }

    @RequestMapping(method = RequestMethod.GET, path = "/tasks/available")
    public ResponseEntity getAvailableReplicate(@RequestParam(name = "workerWalletAddress") String workerWalletAddress,
                                                @RequestParam(name = "workerEnclaveAddress") String workerEnclaveAddress) {
        // get available replicate
        Optional<Replicate> optional = taskService.getAvailableReplicate(workerWalletAddress);
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
                workerWalletAddress, task.getChainTaskId(), workerEnclaveAddress);

        return Optional.of(authorization).
                <ResponseEntity>map(ResponseEntity::ok)
                .orElseGet(() -> status(HttpStatus.NO_CONTENT).build());
    }

    private Optional<TaskModel> createTaskModel(Task task,
                                                ReplicatesList replicatesList) {
        return Optional.of(TaskModel.builder()
                .chainTaskId(task.getChainTaskId())
                .dappType(task.getDappType())
                .commandLine(task.getCommandLine())
                .currentStatus(task.getCurrentStatus())
                .dateStatusList(task.getDateStatusList())
                .replicates(replicatesList.getReplicates())
                .trust(task.getNumWorkersNeeded())
                .uploadingWorkerWalletAddress(task.getUploadingWorkerWalletAddress())
                .consensus(task.getConsensus())
                .build()
        );
    }

    private Optional<AvailableReplicateModel> createAvailableReplicateModel(Task task,
                                                                            ContributionAuthorization contribAuth) {
        return Optional.of(AvailableReplicateModel.builder()
                .contributionAuthorization(contribAuth)
                .build()
        );
    }
}

