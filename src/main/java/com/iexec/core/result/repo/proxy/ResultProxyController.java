package com.iexec.core.result.repo.proxy;

import com.iexec.common.result.ResultModel;
import com.iexec.common.result.eip712.Eip712Challenge;
import com.iexec.core.result.repo.ipfs.IpfsService;
import com.iexec.core.utils.version.VersionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Optional;

import static org.springframework.http.ResponseEntity.ok;

@Slf4j
@RestController
public class ResultProxyController {

    private final ResultProxyService resultProxyService;
    private final Eip712ChallengeService challengeService;
    private final AuthorizationService authorizationService;
    private final VersionService versionService;
    private final IpfsService ipfsService;

    public ResultProxyController(ResultProxyService resultProxyService,
                                 Eip712ChallengeService challengeService,
                                 AuthorizationService authorizationService,
                                 VersionService versionService,
                                 IpfsService ipfsService) {
        this.resultProxyService = resultProxyService;
        this.challengeService = challengeService;
        this.authorizationService = authorizationService;
        this.versionService = versionService;
        this.ipfsService = ipfsService;
    }

    @PostMapping("/results")
    public ResponseEntity<String> addResult(
            @RequestHeader("Authorization") String token,
            @RequestBody ResultModel model) {

        //Authorization auth = authorizationService.getAuthorizationFromToken(token);

        String tokenWalletAddress = authorizationService.getWalletAddressFromJwtString(token);

        boolean authorizedAndCanUploadResult = authorizationService.isValidJwt(token) &&
                //authorizationService.isAuthorizationValid(auth) &&
                resultProxyService.canUploadResult(model.getChainTaskId(),
                        tokenWalletAddress,//auth.getWalletAddress(),
                        model.getZip());

        // TODO check if the result to be added is the correct result for that task

        if (!authorizedAndCanUploadResult) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED.value()).build();
        }

        String resultLink = resultProxyService.addResult(
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
                model.getChainTaskId(), tokenWalletAddress, resultLink);

        challengeService.invalidateEip712ChallengeString(tokenWalletAddress);

        return ok(resultLink);
    }

    @RequestMapping(method = RequestMethod.HEAD, path = "/results/{chainTaskId}")
    public ResponseEntity<String> isResultUploaded(
            @PathVariable(name = "chainTaskId") String chainTaskId,
            @RequestHeader("Authorization") String token) {

        //Authorization auth = authorizationService.getAuthorizationFromToken(token);

        //if (!authorizationService.isAuthorizationValid(auth)) {
        if (!authorizationService.isValidJwt(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED.value()).build();
        }

        boolean isResultInDatabase = resultProxyService.doesResultExist(chainTaskId);
        if (!isResultInDatabase) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND.value()).build();
        }

        //challengeService.invalidateEip712ChallengeString(auth.getChallenge());

        return ResponseEntity.status(HttpStatus.NO_CONTENT.value()).build();
    }

    @GetMapping(value = "/results/{chainTaskId}/snap", produces = "application/zip")
    public ResponseEntity<byte[]> getResultSnap(@PathVariable("chainTaskId") String chainTaskId) throws IOException {
        if (!versionService.isSnapshot()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED.value()).build();
        }
        Optional<byte[]> zip = resultProxyService.getResult(chainTaskId);
        if (!zip.isPresent()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND.value()).build();
        }
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=" + ResultRepo.getResultFilename(chainTaskId) + ".zip")
                .body(zip.get());
    }

    @GetMapping(value = "/results/challenge")
    public ResponseEntity<Eip712Challenge> getChallenge(@RequestParam(name = "chainId") Integer chainId) {
        Eip712Challenge eip712Challenge = challengeService.generateEip712Challenge(chainId);//TODO generate challenge from walletAddress
        return ResponseEntity.ok(eip712Challenge);
    }

    @PostMapping(value = "/results/login")
    public ResponseEntity<String> getToken(@RequestParam(name = "chainId") Integer chainId,
                                           @RequestBody String signedEip712Challenge) {
        String jwtString = authorizationService.getOrCreateJwt(signedEip712Challenge);
        return ResponseEntity.ok(jwtString);
    }

    @GetMapping(value = "/results/{chainTaskId}", produces = "application/zip")
    public ResponseEntity<byte[]> getResult(@PathVariable("chainTaskId") String chainTaskId,
                                            @RequestHeader(name = "Authorization", required = false) String token,
                                            @RequestParam(name = "chainId") Integer chainId) throws IOException {
        //Authorization auth = authorizationService.getAuthorizationFromToken(token);

        boolean isPublicResult = resultProxyService.isPublicResult(chainTaskId);
        boolean isAuthorizedOwnerOfResult =
                //auth != null &&
                resultProxyService.isOwnerOfResult(chainId, chainTaskId, authorizationService.getWalletAddressFromJwtString(token))
                && authorizationService.isValidJwt(token);
                //&& authorizationService.isAuthorizationValid(auth);

        if (isAuthorizedOwnerOfResult || isPublicResult) {//TODO: IPFS fetch from chainTaskId
            if (isAuthorizedOwnerOfResult) {
                //challengeService.invalidateEip712ChallengeString(auth.getChallenge());
            }

            Optional<byte[]> zip = resultProxyService.getResult(chainTaskId);
            if (!zip.isPresent()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND.value()).build();
            }
            return ResponseEntity.ok()
                    .header("Content-Disposition", "attachment; filename=" + ResultRepo.getResultFilename(chainTaskId) + ".zip")
                    .body(zip.get());
        }

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED.value()).build();
    }

    /*
        IPFS Gateway endpoint
     */
    @GetMapping(value = "/results/ipfs/{ipfsHash}", produces = "application/zip")
    public ResponseEntity<byte[]> getResult(@PathVariable("ipfsHash") String ipfsHash) {
        Optional<byte[]> zip = ipfsService.get(ipfsHash);
        if (!zip.isPresent()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND.value()).build();
        }
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=" + ResultRepo.getResultFilename(ipfsHash) + ".zip")
                .body(zip.get());
    }

}

