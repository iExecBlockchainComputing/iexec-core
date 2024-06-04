/*
 * Copyright 2020-2024 IEXEC BLOCKCHAIN TECH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.iexec.core.chain;

import com.iexec.commons.poco.chain.SignerService;
import com.iexec.commons.poco.chain.WorkerpoolAuthorization;
import com.iexec.commons.poco.security.Signature;
import com.iexec.commons.poco.utils.HashUtils;
import org.springframework.stereotype.Service;

@Service
public class SignatureService {

    private final SignerService signerService;

    public SignatureService(SignerService signerService) {
        this.signerService = signerService;
    }

    public String getAddress() {
        return signerService.getAddress();
    }

    public Signature sign(String hash) {
        return signerService.signMessageHash(hash);
    }

    public WorkerpoolAuthorization createAuthorization(String workerWallet, String chainTaskId, String enclaveChallenge) {
        String hash = HashUtils.concatenateAndHash(workerWallet, chainTaskId, enclaveChallenge);
        return WorkerpoolAuthorization.builder()
                .workerWallet(workerWallet)
                .chainTaskId(chainTaskId)
                .enclaveChallenge(enclaveChallenge)
                .signature(sign(hash))
                .build();
    }
}
