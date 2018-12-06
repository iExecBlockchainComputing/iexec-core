package com.iexec.core.replicate;

import com.iexec.common.replicate.ReplicateStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.AccessDeniedException;

@Slf4j
@RestController
public class ReplicatesController {

    private ReplicatesService replicatesService;

    public ReplicatesController(ReplicatesService replicatesService) {
        this.replicatesService = replicatesService;
    }

    @RequestMapping(method = RequestMethod.POST, path = "/replicates/{chainTaskId}/updateStatus")
    public ResponseEntity updateReplicateStatus(@PathVariable(name = "chainTaskId") String chainTaskId,
                                                @RequestParam(name = "walletAddress") String walletAddress,
                                                @RequestParam(name = "replicateStatus") ReplicateStatus replicateStatus,
                                                @RequestHeader("Authorization") String bearerToken) {
        log.info("UpdateReplicateStatus requested [chainTaskId:{}, replicateStatus:{}, walletAddress:{}]",
                chainTaskId, replicateStatus, walletAddress);
        replicatesService.updateReplicateStatus(chainTaskId, walletAddress, replicateStatus);

        return ResponseEntity.ok().build();
    }

    @ExceptionHandler({AccessDeniedException.class})
    public ResponseEntity<String> handleAccessDeniedException(AccessDeniedException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(e.getMessage());
    }
}
