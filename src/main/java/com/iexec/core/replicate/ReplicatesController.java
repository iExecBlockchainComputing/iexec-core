package com.iexec.core.replicate;

import java.util.List;
import java.util.Optional;

import com.iexec.common.chain.ContributionAuthorization;
import com.iexec.common.disconnection.InterruptedReplicateModel;
import com.iexec.common.replicate.ReplicateDetails;
import com.iexec.common.replicate.ReplicateStatus;
import com.iexec.common.replicate.ReplicateStatusModifier;
import com.iexec.core.security.JwtTokenProvider;
import com.iexec.core.task.Task;
import com.iexec.core.task.TaskService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

import static org.springframework.http.ResponseEntity.status;


@Slf4j
@RestController
public class ReplicatesController {

    private ReplicatesService replicatesService;
    private ReplicateSupplyService replicateSupplyService;
    private JwtTokenProvider jwtTokenProvider;

    public ReplicatesController(ReplicatesService replicatesService,
                                ReplicateSupplyService replicateSupplyService,
                                JwtTokenProvider jwtTokenProvider) {
        this.replicatesService = replicatesService;
        this.replicateSupplyService = replicateSupplyService;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @GetMapping("/replicates/available")
    public ResponseEntity getAvailableReplicate(@RequestParam(name = "blockNumber") long blockNumber,
                                                @RequestHeader("Authorization") String bearerToken) {
        String workerWalletAddress = jwtTokenProvider.getWalletAddressFromBearerToken(bearerToken);
        if (workerWalletAddress.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED.value()).build();
        }

        // get contributionAuthorization if a replicate is available
        Optional<ContributionAuthorization> oAuthorization = replicateSupplyService
                .getAuthOfAvailableReplicate(blockNumber, workerWalletAddress);

        return oAuthorization
                .<ResponseEntity<ContributionAuthorization>>map(ResponseEntity::ok)
                .orElseGet(() -> status(HttpStatus.NO_CONTENT).build());
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

        replicatesService.updateReplicateStatus(chainTaskId, walletAddress, replicateStatus, ReplicateStatusModifier.WORKER, details);
        return ResponseEntity.ok().build();
    }
}
