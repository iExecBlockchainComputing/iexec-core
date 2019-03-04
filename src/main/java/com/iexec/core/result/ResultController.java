package com.iexec.core.result;

import com.iexec.common.result.ResultModel;
import com.iexec.common.result.eip712.Eip712Challenge;
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
    public ResponseEntity<String> addResult(
            @RequestHeader("Authorization") String token,
            @RequestBody ResultModel model) {

        Authorization auth = authorizationService.getAuthorizationFromToken(token);

        boolean authorizedAndCanUploadResult = authorizationService.isAuthorizationValid(auth) &&
                resultService.canUploadResult(model.getChainTaskId(), auth.getWalletAddress(), model.getZip());

        // TODO check if the result to be added is the correct result for that task

        if (!authorizedAndCanUploadResult) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED.value()).build();
        }

        String ipfsHash = resultService.addResult(
                Result.builder()
                        .chainTaskId(model.getChainTaskId())
                        .image(model.getImage())
                        .cmd(model.getCmd())
                        .deterministHash(model.getDeterministHash())
                        .build(),
                model.getZip());

        if (ipfsHash.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST.value()).build();
        }

        log.info("Result uploaded successfully [chainTaskId:{}, uploadRequester:{}]",
                model.getChainTaskId(), auth.getWalletAddress());

        challengeService.invalidateEip712ChallengeString(auth.getChallenge());

        return ok(ipfsHash);
    }

    @RequestMapping(method = RequestMethod.HEAD, path = "/results/{chainTaskId}")
    public ResponseEntity<String> checkIfResultHasBeenUploaded(
            @PathVariable(name = "chainTaskId") String chainTaskId,
            @RequestHeader("Authorization") String token) {

        Authorization auth = authorizationService.getAuthorizationFromToken(token);

        if (!authorizationService.isAuthorizationValid(auth)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED.value()).build();
        }

        boolean isResultInDatabase = resultService.isResultInDatabase(chainTaskId);
        if (!isResultInDatabase) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND.value()).build();
        }

        challengeService.invalidateEip712ChallengeString(auth.getChallenge());

        return ResponseEntity.status(HttpStatus.NO_CONTENT.value()).build();
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
                                            @RequestHeader(name = "Authorization", required = false) String token,
                                            @RequestParam(name = "chainId") Integer chainId) throws IOException {
        Authorization auth = authorizationService.getAuthorizationFromToken(token);

        boolean isPublicResult = resultService.isPublicResult(chainTaskId, chainId);
        boolean isAuthorizedOwnerOfResult = auth != null
                && resultService.isOwnerOfResult(chainId, chainTaskId, auth.getWalletAddress())
                && authorizationService.isAuthorizationValid(auth);

        if (isAuthorizedOwnerOfResult || isPublicResult) {
            if (isAuthorizedOwnerOfResult) {
                challengeService.invalidateEip712ChallengeString(auth.getChallenge());
            }

            byte[] zip = resultService.getResultByChainTaskId(chainTaskId);
            return ResponseEntity.ok()
                    .header("Content-Disposition", "attachment; filename=" + ResultService.getResultFilename(chainTaskId))
                    .body(zip);
        }

        return new ResponseEntity(HttpStatus.UNAUTHORIZED);
    }

}

