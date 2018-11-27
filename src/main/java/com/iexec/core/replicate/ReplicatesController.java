package com.iexec.core.replicate;

import com.iexec.common.replicate.ReplicateStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
                                                @RequestParam(name = "replicateStatus") ReplicateStatus replicateStatus) {
        log.info("UpdateReplicateStatus requested [chainTaskId:{}, replicateStatus:{}, walletAddress:{}]",
                chainTaskId, replicateStatus, walletAddress);
        replicatesService.updateReplicateStatus(chainTaskId, walletAddress, replicateStatus);

        return ResponseEntity.ok().build();
    }
}
