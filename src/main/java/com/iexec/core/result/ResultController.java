package com.iexec.core.result;

import com.iexec.common.result.ResultModel;
import com.iexec.common.result.eip712.Eip712Challenge;
import com.iexec.core.utils.version.VersionService;
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
    private VersionService versionService;

    public ResultController(ResultService resultService,
                            Eip712ChallengeService challengeService,
                            AuthorizationService authorizationService,
                            VersionService versionService) {
        this.resultService = resultService;
        this.challengeService = challengeService;
        this.authorizationService = authorizationService;
        this.versionService = versionService;
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

        String resultLink = resultService.addResult(
                Result.builder()
                        .chainTaskId(model.getChainTaskId())
                        .image(model.getImage())
                        .cmd(model.getCmd())
                        .deterministHash(model.getDeterministHash())
                        .build(),
                model.getZip());

        if (resultLink.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST.value()).build();
        }

        log.info("Result uploaded successfully [chainTaskId:{}, uploadRequester:{}, resultLink:{}]",
                model.getChainTaskId(), auth.getWalletAddress(), resultLink);

        challengeService.invalidateEip712ChallengeString(auth.getChallenge());

        return ok(resultLink);
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

    @GetMapping(value = "/results/{chainTaskId}/snap", produces = "application/zip")
    public ResponseEntity<byte[]> getResultSnap(@PathVariable("chainTaskId") String chainTaskId) throws IOException {
        if (!versionService.isSnapshot()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED.value()).build();
        }
        byte[] zip = resultService.getResultByChainTaskId(chainTaskId);
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=" + ResultService.getResultFilename(chainTaskId) + ".zip")
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

        boolean isPublicResult = resultService.isPublicResult(chainTaskId);
        boolean isAuthorizedOwnerOfResult = auth != null
                && resultService.isOwnerOfResult(chainId, chainTaskId, auth.getWalletAddress())
                && authorizationService.isAuthorizationValid(auth);

        if (isAuthorizedOwnerOfResult || isPublicResult) {
            if (isAuthorizedOwnerOfResult) {
                challengeService.invalidateEip712ChallengeString(auth.getChallenge());
            }

            byte[] zip = resultService.getResultByChainTaskId(chainTaskId);
            return ResponseEntity.ok()
                    .header("Content-Disposition", "attachment; filename=" + ResultService.getResultFilename(chainTaskId) + ".zip")
                    .body(zip);
        }

        return new ResponseEntity(HttpStatus.UNAUTHORIZED);
    }

}

