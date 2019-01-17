package com.iexec.core.result;

import com.iexec.common.result.ResultModel;
import com.iexec.core.result.eip712.Eip712AuthenticationModel;
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
    private Eip712ChallengeService eip712ChallengeService;

    public ResultController(ResultService resultService,
                            Eip712ChallengeService eip712ChallengeService) {
        this.resultService = resultService;
        this.eip712ChallengeService = eip712ChallengeService;
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
        Eip712Challenge eip712Challenge = eip712ChallengeService.generateEip712Challenge(chainId);
        return ResponseEntity.ok(eip712Challenge);
    }

    @PostMapping(value = "/results/{chainTaskId}", produces = "application/zip")
    public ResponseEntity<byte[]> getResult(@PathVariable("chainTaskId") String chainTaskId,
                                            @RequestBody Eip712AuthenticationModel auth) throws IOException {
        if (!resultService.isAuthorizedToGetResult(chainTaskId, auth)) {
            return new ResponseEntity(HttpStatus.UNAUTHORIZED);
        }

        String eip712ChallengeString = eip712ChallengeService.getEip712ChallengeString(auth.getEip712Challenge());
        eip712ChallengeService.invalidateEip712ChallengeString(eip712ChallengeString);

        byte[] zip = resultService.getResultByChainTaskId(chainTaskId);
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=" + ResultService.getResultFilename(chainTaskId))
                .body(zip);
    }

}

