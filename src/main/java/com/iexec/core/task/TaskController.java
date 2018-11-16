package com.iexec.core.task;

import com.iexec.common.chain.ContributionAuthorization;
import com.iexec.common.replicate.AvailableReplicateModel;
import com.iexec.common.replicate.ReplicateModel;
import com.iexec.common.replicate.ReplicateStatus;
import com.iexec.core.chain.SignatureService;
import com.iexec.core.replicate.Replicate;
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

    public TaskController(TaskService taskService,
                          SignatureService signatureService) {
        this.taskService = taskService;
        this.signatureService = signatureService;
    }


    // /!\ This creates a task off-chain without chainTaskId
    @PostMapping("/tasks")
    public ResponseEntity postTask(@RequestParam(name = "dappName") String dappName,
                                   @RequestParam(name = "commandLine") String commandLine,
                                   @RequestParam(name = "nbContributionNeeded") int nbContributionNeeded) {
        //TODO change hardcoded trust
        Task task = taskService.addTask(dappName, commandLine, nbContributionNeeded, "", 1);
        log.info("New task created [taskId:{}]", task.getId());
        return ok(task.getId());
    }


    @GetMapping("/tasks/{chainTaskId}")
    public ResponseEntity getTask(@PathVariable("chainTaskId") String chainTaskId) {
        Optional<Task> optional = taskService.getTaskByChainTaskId(chainTaskId);
        return optional.
                <ResponseEntity>map(ResponseEntity::ok).
                orElseGet(() -> status(HttpStatus.NO_CONTENT).build());
    }


    @RequestMapping(method = RequestMethod.POST, path = "/tasks/{chainTaskId}/replicates/updateStatus")
    public ResponseEntity updateReplicateStatus(@PathVariable(name = "chainTaskId") String chainTaskId,
                                                @RequestParam(name = "walletAddress") String walletAddress,
                                                @RequestParam(name = "replicateStatus") ReplicateStatus replicateStatus) {
        log.info("Update replicate status [chainTaskId:{}, replicateStatus:{}, walletAddress:{}]", chainTaskId, replicateStatus, walletAddress);
        taskService.updateReplicateStatus(chainTaskId, walletAddress, replicateStatus);

        return ResponseEntity.ok().build();

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
        ContributionAuthorization authorization = signatureService.createAuthorization(workerWalletAddress, task.getChainTaskId(), workerEnclaveAddress);

        return createAvailableReplicateModel(replicate, task, authorization).
                <ResponseEntity>map(ResponseEntity::ok)
                .orElseGet(() -> status(HttpStatus.NO_CONTENT).build());
    }

    private Optional<AvailableReplicateModel> createAvailableReplicateModel(Replicate replicate,
                                                                            Task task,
                                                                            ContributionAuthorization contribAuth) {
        return Optional.of(AvailableReplicateModel.builder()
                .chainTaskId(task.getChainTaskId())
                .workerAddress(replicate.getWalletAddress())
                .dappType(task.getDappType())
                .dappName(task.getDappName())
                .cmd(task.getCommandLine())
                .replicateStatus(replicate.getStatusChangeList().get(replicate.getStatusChangeList().size() - 1).getStatus())
                .contributionAuthorization(contribAuth)
                .build()
        );
    }

    private Optional<ReplicateModel> convertReplicateToModel(Replicate replicate) {
        Optional<Task> optional = taskService.getTaskByChainTaskId(replicate.getChainTaskId());
        if (!optional.isPresent()) {
            return Optional.empty();
        }
        Task task = optional.get();

        return Optional.of(ReplicateModel.builder()
                .chainTaskId(task.getChainTaskId())
                .workerAddress(replicate.getWalletAddress())
                .dappType(task.getDappType())
                .dappName(task.getDappName())
                .cmd(task.getCommandLine())
                .replicateStatus(replicate.getStatusChangeList().get(replicate.getStatusChangeList().size() - 1).getStatus())
                .build()
        );
    }
}

