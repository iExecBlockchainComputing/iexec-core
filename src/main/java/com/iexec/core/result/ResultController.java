package com.iexec.core.result;

import com.iexec.common.result.ResultModel;
import com.iexec.core.result.eip712.Eip712Challenge;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

import static org.springframework.http.ResponseEntity.ok;

@Slf4j
@RestController
public class ResultController {

    private ResultService resultService;
    private Eip712ChallengeService challengeService;
    private AuthorizationService authorizationService;

    public ResultController(ResultService resultService,
                            Eip712ChallengeService challengeService,
                            AuthorizationService authorizationService) {
        this.resultService = resultService;
        this.challengeService = challengeService;
        this.authorizationService = authorizationService;
    }

    @PostMapping("/results")
    public ResponseEntity addResult(@RequestBody ResultModel model) {
        String filename = resultService.addResult(
                Result.builder()
                        .chainTaskId(model.getChainTaskId())
                        .image(model.getImage())
                        .cmd(model.getCmd())
                        .deterministHash(model.getDeterministHash())
                        .build(),
                model.getZip());
        return ok(filename);
    }

    /*
     * WARNING: This endpoint is for testing purposes only, it has to be removed in production
     */
    @GetMapping(value = "/results/{chainTaskId}/unsafe", produces = "application/zip")
    public ResponseEntity<byte[]> getResultUnsafe(@PathVariable("chainTaskId") String chainTaskId) throws IOException {
        byte[] zip = resultService.getResultByChainTaskId(chainTaskId);
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=" + ResultService.getResultFilename(chainTaskId))
                .body(zip);
    }

    @GetMapping(value = "/results/challenge")
    public ResponseEntity<Eip712Challenge> getChallenge(@RequestParam(name = "chainId") Integer chainId) {
        Eip712Challenge eip712Challenge = challengeService.generateEip712Challenge(chainId);
        return ResponseEntity.ok(eip712Challenge);
    }

    @GetMapping(value = "/results/{chainTaskId}", produces = "application/zip")
    public ResponseEntity<byte[]> getResult(@PathVariable("chainTaskId") String chainTaskId,
                                            @RequestHeader("Authorization") String token,
                                            @RequestParam(name = "chainId") Integer chainId) throws IOException {

        //TODO check split
        String[] parts = token.split("_");
        String eipChallengeString = parts[0];
        String challengeSignature = parts[2];
        String walletAddress = parts[3];

        if (!authorizationService.isAuthorizationValid(eipChallengeString, challengeSignature, walletAddress)) {
            return new ResponseEntity(HttpStatus.UNAUTHORIZED);
        }

        if (!resultService.canGetResult(chainId, chainTaskId, walletAddress)) {
            return new ResponseEntity(HttpStatus.UNAUTHORIZED);
        }

        challengeService.invalidateEip712ChallengeString(eipChallengeString);

        byte[] zip = resultService.getResultByChainTaskId(chainTaskId);
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=" + ResultService.getResultFilename(chainTaskId))
                .body(zip);
    }

}

