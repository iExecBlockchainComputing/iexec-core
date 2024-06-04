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
import com.iexec.commons.poco.utils.BytesUtils;
import com.iexec.commons.poco.utils.HashUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.web3j.crypto.Credentials;

import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SignatureServiceTests {

    @Mock
    private SignerService signerService;

    @InjectMocks
    private SignatureService signatureService;

    @Test
    void shouldAuthorizationHashBeValid() {

        String workerWallet = "0x748e091bf16048cb5103E0E10F9D5a8b7fBDd860";
        String chainTaskid = "0xd94b63fc2d3ec4b96daf84b403bbafdc8c8517e8e2addd51fec0fa4e67801be8";
        String enclaveWallet = "0x9a43BB008b7A657e1936ebf5d8e28e5c5E021596";

        String expected = "0x54a76d209e8167e1ffa3bde8e3e7b30068423ca9554e1d605d8ee8fd0f165562";

        Assertions.assertEquals(expected, HashUtils.concatenateAndHash(workerWallet, chainTaskid, enclaveWallet));
    }

    @Test
    void shouldCreateCorrectAuthorization() {
        // input
        String workerWallet = "0x748e091bf16048cb5103E0E10F9D5a8b7fBDd860";
        String chainTaskId = "0xd94b63fc2d3ec4b96daf84b403bbafdc8c8517e8e2addd51fec0fa4e67801be8";
        String enclaveChallenge = "0x9a43BB008b7A657e1936ebf5d8e28e5c5E021596";
        String privateKey = "0x2a46e8c1535792f6689b10d5c882c9363910c30751ec193ae71ec71630077909";
        String hash = HashUtils.concatenateAndHash(workerWallet, chainTaskId, enclaveChallenge);

        ReflectionTestUtils.setField(signerService, "credentials", Credentials.create(privateKey));
        when(signerService.signMessageHash(hash)).thenCallRealMethod();

        // creation
        WorkerpoolAuthorization authorization = signatureService.createAuthorization(workerWallet, chainTaskId, enclaveChallenge);

        // check
        WorkerpoolAuthorization expected = WorkerpoolAuthorization.builder()
                .workerWallet(workerWallet)
                .chainTaskId(chainTaskId)
                .enclaveChallenge(enclaveChallenge)
                .signature(new Signature(
                        BytesUtils.stringToBytes("0x63f2c959ed7dfc11619e1e0b5ba8a4bf56f81ce81d0b6e6e9cdeca538cb85d97"),
                        BytesUtils.stringToBytes("0x737747b747bc6c7d42cba859fdd030b1bed8b2513699ba78ac67dab5b785fda5"),
                        new byte[]{(byte) 28}))
                .build();

        Assertions.assertEquals(authorization, expected);

    }
}
