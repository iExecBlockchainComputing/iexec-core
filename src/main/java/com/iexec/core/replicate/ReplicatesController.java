package com.iexec.core.replicate;

import java.util.List;
import java.util.Optional;

import com.iexec.common.chain.ChainReceipt;
import com.iexec.common.chain.ContributionAuthorization;
import com.iexec.common.disconnection.InterruptedReplicateModel;
import com.iexec.common.replicate.ReplicateDetails;
import com.iexec.common.replicate.ReplicateStatus;
import com.iexec.common.replicate.ReplicateStatusModifier;
import com.iexec.common.tee.TeeUtils;
import com.iexec.core.chain.SignatureService;
import com.iexec.core.security.JwtTokenProvider;
import com.iexec.core.task.Task;
import com.iexec.core.task.TaskService;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import static org.springframework.http.ResponseEntity.status;


@Slf4j
@RestController
public class ReplicatesController {

    private ReplicatesService replicatesService;
    private ReplicateSupplyService replicateSupplyService;
    private JwtTokenProvider jwtTokenProvider;
    private SignatureService signatureService;
    private TaskService taskService;


    public ReplicatesController(ReplicatesService replicatesService,
                                ReplicateSupplyService replicateSupplyService,
                                JwtTokenProvider jwtTokenProvider,
                                SignatureService signatureService,
                                TaskService taskService) {
        this.replicatesService = replicatesService;
        this.replicateSupplyService = replicateSupplyService;
        this.jwtTokenProvider = jwtTokenProvider;
        this.signatureService = signatureService;
        this.taskService = taskService;
    }

    @GetMapping("/replicates/available")
    public ResponseEntity getAvailableReplicate(@RequestParam(name = "blockNumber") long blockNumber,
                                                @RequestHeader("Authorization") String bearerToken) {
        String workerWalletAddress = jwtTokenProvider.getWalletAddressFromBearerToken(bearerToken);
        if (workerWalletAddress.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED.value()).build();
        }

        // get available replicate
        Optional<Replicate> optional = replicateSupplyService.getAvailableReplicate(blockNumber, workerWalletAddress);
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

        return ResponseEntity.ok(authorization);
    }

    @GetMapping("/replicates/interrupted")
    public ResponseEntity<List<InterruptedReplicateModel>> getInterruptedReplicates(
            @RequestParam(name = "blockNumber") long blockNumber,
            @RequestHeader("Authorization") String bearerToken) {

        String workerWalletAddress = jwtTokenProvider.getWalletAddressFromBearerToken(bearerToken);
        if (workerWalletAddress.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED.value()).build();
        }

        List<InterruptedReplicateModel> interruptedReplicateList =
                replicateSupplyService.getInterruptedReplicates(blockNumber, workerWalletAddress);

        return ResponseEntity.ok(interruptedReplicateList);
    }

    @PostMapping("/replicates/{chainTaskId}/updateStatus")
    public ResponseEntity<String> updateReplicateStatus(
            @PathVariable(name = "chainTaskId") String chainTaskId,
            @RequestParam(name = "replicateStatus") ReplicateStatus replicateStatus,
            @RequestHeader("Authorization") String bearerToken,
            @RequestBody ReplicateDetails details) {

        String walletAddress = jwtTokenProvider.getWalletAddressFromBearerToken(bearerToken);

        if (walletAddress.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED.value()).build();
        }

        log.info("UpdateReplicateStatus requested [chainTaskId:{}, replicateStatus:{}, walletAddress:{}]",
                chainTaskId, replicateStatus, walletAddress);

        replicatesService.updateReplicateStatus(chainTaskId, walletAddress, replicateStatus, ReplicateStatusModifier.WORKER,
                details.getChainReceipt(), details.getResultLink());
        return ResponseEntity.ok().build();
    }
}
