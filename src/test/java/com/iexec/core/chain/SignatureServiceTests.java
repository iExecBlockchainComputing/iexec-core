/*
 * Copyright 2020 IEXEC BLOCKCHAIN TECH
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

import com.iexec.common.chain.WorkerpoolAuthorization;
import com.iexec.common.security.Signature;
import com.iexec.common.utils.BytesUtils;
import com.iexec.common.utils.HashUtils;
import com.iexec.core.configuration.SmsConfiguration;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.web3j.crypto.Credentials;

import static org.mockito.Mockito.when;


public class SignatureServiceTests {

    @Mock private CredentialsService credentialsService;
    @Mock private SmsConfiguration smsConfiguration;

    @InjectMocks
    private SignatureService signatureService;

    @BeforeEach
    public void init() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void shouldAuthorizationHashBeValid() {

        String workerWallet = "0x748e091bf16048cb5103E0E10F9D5a8b7fBDd860";
        String chainTaskid = "0xd94b63fc2d3ec4b96daf84b403bbafdc8c8517e8e2addd51fec0fa4e67801be8";
        String enclaveWallet = "0x9a43BB008b7A657e1936ebf5d8e28e5c5E021596";

        String expected = "0x54a76d209e8167e1ffa3bde8e3e7b30068423ca9554e1d605d8ee8fd0f165562";

        Assertions.assertEquals(expected, HashUtils.concatenateAndHash(workerWallet, chainTaskid, enclaveWallet));
    }

    @Test
    public void shouldCreateCorrectAuthorization() {
        // input
        String workerWallet = "0x748e091bf16048cb5103E0E10F9D5a8b7fBDd860";
        String chainTaskid = "0xd94b63fc2d3ec4b96daf84b403bbafdc8c8517e8e2addd51fec0fa4e67801be8";
        String enclaveChallenge = "0x9a43BB008b7A657e1936ebf5d8e28e5c5E021596";
        String privateKey = "0x2a46e8c1535792f6689b10d5c882c9363910c30751ec193ae71ec71630077909";

        when(credentialsService.getCredentials()).thenReturn(Credentials.create(privateKey));

        // creation
        WorkerpoolAuthorization authorization = signatureService.createAuthorization(workerWallet, chainTaskid, enclaveChallenge);

        // check
        WorkerpoolAuthorization expected = WorkerpoolAuthorization.builder()
                .workerWallet(workerWallet)
                .chainTaskId(chainTaskid)
                .enclaveChallenge(enclaveChallenge)
                .signature(new Signature(
                        BytesUtils.stringToBytes("0x63f2c959ed7dfc11619e1e0b5ba8a4bf56f81ce81d0b6e6e9cdeca538cb85d97"),
                        BytesUtils.stringToBytes("0x737747b747bc6c7d42cba859fdd030b1bed8b2513699ba78ac67dab5b785fda5"),
                        new byte[]{(byte)28}))
                .build();

        Assertions.assertEquals(authorization, expected);

    }
}
