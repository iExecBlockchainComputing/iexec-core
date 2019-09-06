package com.iexec.core.replicate;

import com.iexec.common.chain.ContributionAuthorization;
import com.iexec.common.notification.TaskNotification;
import com.iexec.common.notification.TaskNotificationType;
import com.iexec.common.replicate.ReplicateStatus;
import com.iexec.common.replicate.ReplicateStatusModifier;
import com.iexec.common.replicate.ReplicateStatusUpdate;
import com.iexec.core.security.JwtTokenProvider;
import com.iexec.core.worker.WorkerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.springframework.http.ResponseEntity.status;


@Slf4j
@RestController
public class ReplicatesController {

    private ReplicatesService replicatesService;
    private ReplicateSupplyService replicateSupplyService;
    private JwtTokenProvider jwtTokenProvider;
    private WorkerService workerService;

    public ReplicatesController(ReplicatesService replicatesService,
                                ReplicateSupplyService replicateSupplyService,
                                JwtTokenProvider jwtTokenProvider,
                                WorkerService workerService) {
        this.replicatesService = replicatesService;
        this.replicateSupplyService = replicateSupplyService;
        this.jwtTokenProvider = jwtTokenProvider;
        this.workerService = workerService;
    }

    @GetMapping("/replicates/available")
    public ResponseEntity getAvailableReplicate(@RequestParam(name = "blockNumber") long blockNumber,
                                                @RequestHeader("Authorization") String bearerToken) {
        String workerWalletAddress = jwtTokenProvider.getWalletAddressFromBearerToken(bearerToken);
        if (workerWalletAddress.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED.value()).build();
        }

        if (!workerService.isWorkerAllowedToAskReplicate(workerWalletAddress)){
            return ResponseEntity.status(HttpStatus.NO_CONTENT.value()).build();
        }
        workerService.updateLastReplicateDemandDate(workerWalletAddress);

        // get contributionAuthorization if a replicate is available
        Optional<ContributionAuthorization> oAuthorization = replicateSupplyService
                .getAuthOfAvailableReplicate(blockNumber, workerWalletAddress);

        return oAuthorization
                .<ResponseEntity<ContributionAuthorization>>map(ResponseEntity::ok)
                .orElseGet(() -> status(HttpStatus.NO_CONTENT).build());
    }

    @GetMapping("/replicates/interrupted")
    public ResponseEntity<List<TaskNotification>> getMissedTaskNotifications(
            @RequestParam(name = "blockNumber") long blockNumber,
            @RequestHeader("Authorization") String bearerToken) {

        String workerWalletAddress = jwtTokenProvider.getWalletAddressFromBearerToken(bearerToken);
        if (workerWalletAddress.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED.value()).build();
        }

        List<TaskNotification> missedTaskNotifications =
                replicateSupplyService.getMissedTaskNotifications(blockNumber, workerWalletAddress);

        return ResponseEntity.ok(missedTaskNotifications);
    }

    @PostMapping("/replicates/{chainTaskId}/updateStatus")
    public ResponseEntity<TaskNotificationType> updateReplicateStatus(
            @RequestHeader("Authorization") String bearerToken,
            @PathVariable(name = "chainTaskId") String chainTaskId,
            @RequestBody ReplicateStatusUpdate statusUpdate) {

        String walletAddress = jwtTokenProvider.getWalletAddressFromBearerToken(bearerToken);

        if (walletAddress.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED.value()).build();
        }

        log.info("Replicate update request [status:{}, chainTaskId:{}, walletAddress:{}, details:{}]",
                statusUpdate.getStatus(), chainTaskId, walletAddress, statusUpdate.getDetails());

        statusUpdate.setModifier(ReplicateStatusModifier.WORKER);
        statusUpdate.setDate(new Date());
        statusUpdate.setSuccess(ReplicateStatus.isSuccess(statusUpdate.getStatus()));

        Optional<TaskNotificationType> oTaskNotificationType =
                replicatesService.updateReplicateStatus(chainTaskId, walletAddress, statusUpdate);

        if (oTaskNotificationType.isPresent()) {
            log.info("Replicate update response [action:{}, chainTaskId:{}, walletAddress:{}]",
                    oTaskNotificationType.get(), chainTaskId , walletAddress);
        }

        return oTaskNotificationType.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.FORBIDDEN.value()).build());
    }
}
