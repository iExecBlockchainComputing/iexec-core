package com.iexec.core.replicate;

import com.iexec.common.replicate.ReplicateStatus;
import com.iexec.core.security.JwtTokenProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
public class ReplicatesController {

    private ReplicatesService replicatesService;
    private JwtTokenProvider jwtTokenProvider;

    public ReplicatesController(ReplicatesService replicatesService,
                                JwtTokenProvider jwtTokenProvider) {
        this.replicatesService = replicatesService;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @RequestMapping(method = RequestMethod.POST, path = "/replicates/{chainTaskId}/updateStatus")
    public ResponseEntity updateReplicateStatus(@PathVariable(name = "chainTaskId") String chainTaskId,
                                                @RequestParam(name = "replicateStatus") ReplicateStatus replicateStatus,
                                                @RequestHeader("Authorization") String bearerToken) throws Exception {

        String token = jwtTokenProvider.resolveToken(bearerToken);
        if (token != null && jwtTokenProvider.validateToken(token)) {
            String walletAddress = jwtTokenProvider.getWalletAddress(token);

            log.info("UpdateReplicateStatus requested [chainTaskId:{}, replicateStatus:{}, walletAddress:{}]",
                    chainTaskId, replicateStatus, walletAddress);
            replicatesService.updateReplicateStatus(chainTaskId, walletAddress, replicateStatus);
            return ResponseEntity.ok().build();
        } else {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED.value()).build();
        }
    }
}
